(ns nflow-clj.engine
  (:require [nflow-clj.database.connection-pool :as pool]
            [nflow-clj.database.executors :as executors]
            [nflow-clj.database.instances :as instances]
            [nflow-clj.database.definitions :as definitions]
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

(defn- create-context [config db process workflow]
  ;; TODO fetch full workflow from db
  (let [type (:type process)
        full-workflow workflow]
    {
     :process  process
     :workflow full-workflow
     :config   (-> config :workflows type)
     }))

(defn- find-current-state [process workflow-state]
  (let [states (:states process)]
    (first (filter (fn [state]
                     (log/info "check state" (:state-id state) (keyword workflow-state))
                     (= (:state-id state) (keyword workflow-state)))
                   states))))

(defn- execute-idle-state [db ctx]
  (log/info "Idle state" ctx)
  ;; TODO mark executed
  )

(defn- make-action [ctx response])

(defn- error-retry-delay [config ctx response]
  (let [retries (or (-> ctx :workflow :retries) 0)
        retry-delay (or (-> ctx :config :retry-delay)
                        (-> config :engine :retry-delay))]
    (log/info retry-delay retries)
    (log/info (Math/pow retry-delay (inc retries)))

    (time/plus (time/now)
               (time/millis (Math/pow retry-delay (inc retries))))))

(defn- make-updated-workflow [config ctx response]
  (let [workflow (:workflow ctx)
        retries (or (:retries workflow) 0)]
    (if (:error response)
      (assoc workflow :next-activation (error-retry-delay config ctx response)
                      :retries (inc retries))
      (assoc workflow :next-activation (error-retry-delay config ctx response)
                      :retries (inc retries))
      )))

(defn- execute-catch [execute-fn ctx]
  (try
    (execute-fn ctx)
    (catch Exception e
      {:error e})))

(defn- execute-processing-state [config db executor-id ctx process-state]
  (log/info "Processing state" ctx)
  (let [execution-started (System/currentTimeMillis)
        execute-fn (:execute-fn process-state)
        response (execute-catch execute-fn ctx)
        execution-end (System/currentTimeMillis)
        workflow (:workflow ctx)
        updated-workflow (make-updated-workflow config ctx response)
        variables (:variables response)
        action nil]
    (instances/update-workflow-after-execution! db executor-id updated-workflow action variables)
    )
  )

(defn- execute-process-state [config db executor-id ctx process-state]
  (cond (:execute-fn process-state) (execute-processing-state config db executor-id ctx process-state)
        :default (execute-idle-state db ctx))
  )

(defn- execute-process [config executor-id db process workflow]
  (let [{:keys [name type]} process
        ctx (create-context config db process workflow)
        _ (log/info "ww" workflow)
        state (:state workflow)
        process-state (find-current-state process state)
        {:keys [execute-fn next-states status]} process-state
        ]
    (try
      (cond (not process-state) (state-not-found process workflow)
            :default (execute-process-state config db executor-id ctx process-state))
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

(def running (atom 5))
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
  (log/info "Executor threads stopped")
  )

(defn validate-processes [processes]
  true)