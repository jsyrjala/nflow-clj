(ns nflow-clj.engine
  (:require [nflow-clj.database.executors :as executors]
            [nflow-clj.database.instances :as instances]
            [nflow-clj.util :as util]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [com.climate.claypoole :as cp]
            )
  )

(defn- no-work-sleep [config]
  (let [sleep (-> config :engine :no-work-sleep)]
    (log/debug "No workflows to process. Sleeping" sleep "millis.")
    (Thread/sleep sleep)))

(defn- work-sleep [config]
  (when-let [sleep (-> config :engine :work-sleep)]
    (log/debug "Sleeping bit after processing a workflow. Sleeping" sleep "millis.")
    (Thread/sleep sleep)))

(defn- find-process [processes workflow]
  (let [workflow-type (keyword (:type workflow))
        found-process (first (filter (fn [process] (= (:type process) workflow-type)) processes))]
    (log/info "Found process" workflow-type found-process)
    found-process))

(defn- no-process-workflow [config workflow]
  (log/warn "No process found for workflow" workflow)
  )

(defn- state-not-found [process workflow]
  (log/warn "No process state found for workflow" workflow)
  )

(defn- create-context [config executor-id db process workflow]
  (let [type (:type process)
        full-workflow (instances/get-workflow db (:id workflow))]
    {
     :process  process
     :workflow full-workflow
     :executor-id executor-id
     :config   (-> config :workflows type)
     }))

(defn- find-current-state [process workflow-state]
  (log/info "find" workflow-state process )
  (let [states (:states process)]
    (first (filter (fn [state]
                     (= (:state-id state) (keyword workflow-state)))
                   states))))

(defn- execute-idle-state [db ctx]
  (log/info "Idle state" ctx)
  ;; TODO mark executed
  )

(defn- error-retry-delay [config ctx response]
  (let [retries (or (-> ctx :workflow :retries) 0)
        retry-delay (or (-> ctx :config :retry-delay)
                        (-> config :engine :retry-delay))]
    (time/plus (time/now)
               (time/millis (* retry-delay (Math/pow 2 (inc retries)))))))

(defn- process-step-delay [config ctx response]
  (let [now (time/now)
        next-activation (:next-activation response)]
    (cond (not next-activation) now
          (.isAfter now next-activation) now
          :default next-activation)))

(defn- make-updated-workflow [config ctx process response]
  (let [workflow (:workflow ctx)
        retries (or (:retries workflow) 0)]
    (if (:error response)
      (assoc workflow :next-activation (error-retry-delay config ctx response)
                      :retries (inc retries))
      ;; TODO normal delay
      (let [new-process-state (find-current-state process (:next-state response))]
        (assoc workflow :next-activation (process-step-delay config ctx response)
                        :state (or (:next-state response) (:state workflow))
                        :retries 0
                        :status (or (:status new-process-state) :nflow-instance-type/manual)
                        :executor-id nil))
      )))

(defn- execute-catch [execute-fn ctx]
  (try
    (execute-fn ctx)
    (catch Exception e
      {:error e})))

(defn- make-action [ctx process-state response start end]
  (log/info process-state)
  (let [action-type (if (:error response)
                      :nflow-action-type/stateExecutionFailed
                      :nflow-action-type/stateExecution)]
    {:execution-start start
     :execution_end   end
     :executor-id     (:executor-id ctx)
     :type            action-type
     :state           (:state-id process-state)
     :state-text      (:state-text response)
     :retry-no        (inc (-> ctx :workflow :retries))
     }))

(defn validate-response [ctx process-state response]

  )

(defn- execute-processing-state [config db ctx process process-state]
  (let [execution-started (time/now)
        execute-fn (:execute-fn process-state)
        response (execute-catch execute-fn ctx)
        execution-end (time/now)
        _ (validate-response ctx process-state response)
        updated-workflow (make-updated-workflow config ctx process response)
        variables (:variables response)
        action (make-action ctx process-state response execution-started execution-end)
        executor-id (:executor-id ctx)]
    (instances/update-workflow-after-execution! db executor-id updated-workflow action variables)
    ))

(defn- execute-process-state [config db ctx process process-state]
  (cond (:execute-fn process-state) (execute-processing-state config db ctx process process-state)
        :default (execute-idle-state db ctx)))

(defn- execute-process [config executor-id db process workflow]
  (let [{:keys [name type]} process
        ctx (create-context config executor-id db process workflow)
        state (:state workflow)
        process-state (find-current-state process state)]
    (try
      (cond (not process-state) (state-not-found process workflow)
            :default (execute-process-state config db ctx process process-state))
      (catch Exception e
        (log/error e (str "Processing of " name ": " type " failed."))
        ;; TODO handle retry
        ;; TODO handle max retries
        )
      )))

(defn- execute-process-in-thread [config executor-id thread-pool db process workflow]
  ;; this wont work with clojure.spec
  (cp/future thread-pool (execute-process config executor-id db process workflow))
  )

(defn- start-process [config executor-id thread-pool db processes workflow]
  (let [process (find-process processes workflow)]
    (cond (not process) (no-process-workflow config workflow)
          :default (execute-process-in-thread config executor-id thread-pool db process workflow)) ))

(defn- create-executor [config db]
  (let [executor {:host (util/hostname)
                  :pid (util/process-id)
                  :executor-group (-> config :engine :executor-group)
                  :expires-in (-> config :engine :executor-expires)}]
    (executors/create-executor! db executor)))

(defn poll-workflows [config executor-id thread-pool db processes]
  (let [{:keys [executor-group batch-size]} (-> config :engine)]
    ;; TODO handle race condition exception
    (let [reserved-workflows (instances/reserve-instances db executor-group executor-id batch-size)]
      (log/info "Reserved" (count reserved-workflows) "for processing.")
      (cond (empty? reserved-workflows) (no-work-sleep config)
            :default (doseq [workflow reserved-workflows]
                       (start-process config executor-id thread-pool db processes workflow)
                       (work-sleep config))
            ))))

;; TODO limits running to N polls
(def running (atom 10))
(defn- running? [config]
  (swap! running dec)
  (> @running 0))

(defn- create-executor-thread-pool [config]
  (cp/threadpool (get-in config [:engine :poller-thread-count] (cp/ncpus))
                 :name "nflow-executor"))

(defn- create-polling-thread-pool [config]
  (cp/threadpool 1 :name "nflow-poller"))

(defn start-engine! [config db processes]
  (log/info "Starting nflow engine")
  (let [executor-id (create-executor config db)]
    (cp/with-shutdown!
      [executor-thread-pool (create-executor-thread-pool config)]
      (while (running? config)
        (poll-workflows config executor-id executor-thread-pool db processes))
      (log/info "Polling stopped. Waiting for nflow-executor threads to finish.")
      ))
  (log/info "Executor threads stopped"))

(defn validate-processes [processes]
  true)