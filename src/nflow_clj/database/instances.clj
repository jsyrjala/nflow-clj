(ns nflow-clj.database.instances
  (:require [hugsql.core :as hugsql]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
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

(defn store-workflow! [db workflow]
  (jdbc/with-db-transaction
    [transaction db]
    (let [workflow-id (store-instance! transaction workflow)]
      (doseq [action (:actions workflow)]
        (let [action-id
              (store-action! transaction (assoc action
                                           :workflow-id workflow-id))]
          (doseq [state (:variables action)]
            (store-state! transaction (assoc state
                                        :action-id action-id
                                        :workflow-id workflow-id
                                        )))))
      workflow-id)))

(defn update-workflow-after-execution! [db executor-id workflow action new-state-variables]
  (jdbc/with-db-transaction
    [transaction db]
    (let [workflow-id (:workflow-id workflow)
          workflow (assoc workflow :current-executor-id executor-id)
          workflows-updated (execute update-instance-after-execution! transaction workflow)
          _ (assert (= 1 workflows-updated)
                    (format "Updating workflow didn't work for workflow %s, updated %s rows."
                            workflow workflows-updated))
          action-id (store-action! transaction (assoc action
                                                 :workflow-id workflow-id))]
      (doseq [state new-state-variables]
        (store-state! transaction (assoc state
                                    :workflow-id workflow-id
                                    :action-id action-id)))
      action-id)))

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
  (jdbc/with-db-transaction
    [transaction db]
    (let [processables (get-processable-instances transaction executor-group limit)]
      (cond (seq processables) (update-next-instances transaction executor-group executor-id processables)
            :default nil))))

;; READ
(defn- reconstruct-state-vars [states]
  (reduce (fn [acc state]
            (let [{:keys [action-id state-key state-value]} state]
              (update-in acc [action-id state-key] (constantly state-value))))
          {}
          states))

(defn get-state-variables [db instance-id]
  (->> (execute query-state db {:instance-id instance-id})
       (map reconstruct-state-vars)
      ))