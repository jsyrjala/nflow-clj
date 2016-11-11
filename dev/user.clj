(ns user
  (:require [nflow-clj.database.connection-pool :as pool]
            [nflow-clj.database.executors :as executors]
            [nflow-clj.database.instances :as instances]
            [nflow-clj.database.definitions :as definitions]
            [nflow-clj.util :as util]
            [clj-time.core :as time]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            )
  (:import (java.util UUID)))

;; TODO need to do this somewhere
(require 'clj-time.jdbc)

(defn read-config [filename]
  (-> (slurp filename)
      (edn/read-string)))

(def config (read-config "./config.edn"))

(println "REPL ready")

(defonce db (pool/create-hikari-pool (-> config :database)))

(executors/get-executors db "foo")

(def executor {:host (util/hostname)
               :pid (util/process-id)
               :executor-group "foo"
               :expires-in 1000})

(def executor-id (executors/create-executor! db executor))
executor-id
(executors/update-activity! db executor-id 3600)

(executors/mark-shutdown! db "foo" 1)

(comment
  :nflow-instance-type/created
  :nflow-instance-type/executing
  :nflow-instance-type/inProgress
  :nflow-instance-type/finished
  :nflow-instance-type/manual )

(defn create-instance []
  {
   :type               "foo"
   :root_workflow_id   nil
   :parent_workflow_id nil
   :parent_action_id   nil
   :business_key       "foo"
   :external_id        (.toString (UUID/randomUUID))
   :executor_group     "foo"
   :status             :nflow-instance-type/inProgress
   :state              "foo"
   :state_text         "foo"
   :next_activation    nil
   })


(def instance-id (instances/store-instance! db (create-instance)))
instance-id

(comment
  :nflow-action-type/stateExecution
  :nflow-action-type/stateExecutionFailed
  :nflow-action-type/recovery
  :nflow-action-type/externalChange )

(defn create-action [workflow-id executor-id]
  {:workflow_id workflow-id
   :executor_id executor-id
   :type :nflow-action-type/stateExecution
   :state "foo"
   :state_text "bad foo"
   :retry_no 0
   :execution_start (time/now)
   :execution_end (time/now)})

(def action-id (instances/store-action! db (create-action instance-id executor-id)))

(defn create-state [workflow-id action-id key value]
  {:workflow-id workflow-id
   :action-id action-id
   :state-key key
   :state-value value})

(instances/store-state! db (create-state instance-id action-id "foo" "bar"))

(defn create-definition [executor-group]
  {
   :type           "account221"
   :definition     {:next-state "foo2"},
   :modified-by    2
   :executor-group executor-group
   })

(definitions/get-definitions db "foo")


(definitions/store-definition! db (create-definition "foo"))

(definitions/refresh-definition! db (create-definition "foo"))
