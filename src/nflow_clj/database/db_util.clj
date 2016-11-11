(ns nflow-clj.database.db-util
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [camel-snake-kebab.extras :refer [transform-keys]])
  )

(def action-types #{:nflow-action-type/stateExecution
                    :nflow-action-type/stateExecutionFailed
                    :nflow-action-type/recovery
                    :nflow-action-type/externalChange})

(def instance-statuses #{:nflow-instance-type/created
                         :nflow-instance-type/executing
                         :nflow-instance-type/inProgress
                         :nflow-instance-type/finished
                         :nflow-instance-type/manual})

(defn convert-values [func m]
  (reduce-kv (fn [m k v] (assoc m k (func v))) {} m))

(defn- value->db [value]
  (cond (action-types value) (-> value action-types name)
        (instance-statuses value) (-> value instance-statuses name)
        :default value))

(defn clj->db [value]
  (->> value
       (transform-keys ->snake_case)
       (convert-values value->db)))

(defn db->clj [value]
  (->> value
       (transform-keys ->kebab-case)))

(defn execute [db-command db data]
  (-> (db-command db
                  (-> data
                      clj->db))
      db->clj))

(defn inserted-id [value]
  (:generated-key value))