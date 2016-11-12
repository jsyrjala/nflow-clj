(ns nflow-clj.engine
  (:require [nflow-clj.database.connection-pool :as pool]
            [nflow-clj.database.executors :as executors]
            [nflow-clj.database.instances :as instances]
            [nflow-clj.database.definitions :as definitions]
            [nflow-clj.util :as util]
            [clojure.tools.logging :as log]
            [com.climate.claypoole :as cp]
            )
  )

(defn validate-processes [processes]
  true)

(defn- no-work-sleep [config]
  (let [sleep (-> config :engine :no-work-sleep)]
    (log/debug "No workflows to process. Sleeping" sleep "millis.")
    (Thread/sleep sleep)))

(defn- work-sleep [config]
  (when-let [sleep (-> config :engine :work-sleep)]
    (log/debug "Sleeping bit after processing a workflow. Sleeping" sleep "millis.")
    (Thread/sleep sleep)))

(defn- find-process [processes workflow]
  (prn workflow)
  )

(defn- no-process-workflow [config workflow]
  (log/warn "No process found for workflow" workflow)
  )

(defn execute-process [db process workflow]
  (try

    (catch Exception e
      (log/error e "Processing of" (:name process) "failed."))
    )
  )

(defn- execute-process-in-thread [thread-pool db process workflow]
  ;; this wont work with clojure.spec
  (cp/future thread-pool (execute-process db process workflow))
  )

(defn- start-process [config thread-pool db processes workflow]
  (let [process (find-process processes workflow)]
    (cond (not process) (no-process-workflow config workflow)
          :default (execute-process-in-thread thread-pool db process workflow)) ))

(defn- create-executor [config db]
  (let [executor {:host (util/hostname)
                  :pid (util/process-id)
                  :executor-group (-> config :engine :executor-group)
                  :expires-in (-> config :engine :executor-expires)}]
    (executors/create-executor! db executor)))

(defn poll-workflows [config executor-id thread-pool db processes]
  (log/info "polls" (-> config :engine))
  (let [{:keys [executor-group batch-size]} (-> config :engine)]
    ;; TODO handle race condition exception
    (let [reserved-workflows (instances/reserve-instances db executor-group executor-id batch-size)]
      (log/info "Reserved" (count reserved-workflows) "for processing.")
      (cond (empty? reserved-workflows) (no-work-sleep config)
            :default (doseq [workflow reserved-workflows]
                       (start-process config thread-pool db processes workflow)
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