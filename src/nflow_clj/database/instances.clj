(ns nflow-clj.database.instances
  (:require [hugsql.core :as hugsql]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [nflow-clj.database.db-util :as db-util :refer [execute inserted-id]]
            ))

(hugsql/def-db-fns "sql/instances.sql")

(defn store-instance! [db instance]
  (-> (execute insert-instance! db instance)
      inserted-id))

(defn store-action! [db action]
  (-> (execute insert-action! db action)
      inserted-id))

(defn store-state! [db state]
  (-> (execute insert-state! db state)
      inserted-id))

(defn get-recoverable-instances [db executor-group executor-id]
  (execute query-recoverable-instances db
           {:executor-group executor-group
            :executor-id executor-id
            }))

(defn get-processable-instances [db executor-group limit]
  (execute query-processable-instances db
           {:executor-group executor-group
            :limit limit}))

(defn- try-reserving-instance! [db executor-id instance]
  (let [reserved-count (execute update-reserve-instance! db
                                (assoc instance :executor-id executor-id))]
    (when (= reserved-count 1)
      instance)))

;; IMPROVE this is rather non optimal way to do reserving. Look into batching.
(defn update-next-instances [db executor-group executor-id possible-instances]
  (let [reserved (->> possible-instances
                      (map #(try-reserving-instance! db executor-id %))
                      (filter identity))]
    (cond (= (count reserved) 0) (throw (ex-info "Race condition in polling workflow instances detected. Multiple pollers using same executor group."
                                                 {:type           :polling-race-condition
                                                  :executor-group executor-group}))
          :default reserved)))

(defn reserve-instances [db executor-group executor-id limit]
  (let [processables (get-processable-instances db executor-group limit)]
    (cond (seq processables) (update-next-instances db executor-group executor-id processables)
          :default nil)))
