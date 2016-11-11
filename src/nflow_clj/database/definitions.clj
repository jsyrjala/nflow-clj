(ns nflow-clj.database.definitions
  (:require [hugsql.core :as hugsql]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [nflow-clj.database.db-util :as db-util]
            )
  (:import (java.security MessageDigest)
           (java.nio.charset StandardCharsets)))

(hugsql/def-db-fns "sql/definitions.sql")

(defn get-definitions [db executor-group]
  (db-util/execute query-definitions
                   db {:executor_group executor-group}))

(defn- serialize-definition [value]
  (db-util/clj->json value))

(defn- sha1-digest [value]
  (let [^MessageDigest digest (MessageDigest/getInstance "SHA-1")
        bytes (.getBytes value StandardCharsets/UTF_8)
        _ (.update digest bytes)
        value (new BigInteger 1 (.digest digest))]
    (format "%040x" value)))

(defn- add-digest [definition]
  (let [data (serialize-definition (:definition definition))
        digest (sha1-digest data)]
    ;; numbers are not allowed in keys by hugsql
    (assoc definition
      :definition data
      :definition-sha digest)))

(defn store-definition! [db definition]
  (db-util/execute insert-definition! db
                   (-> definition add-digest)))
