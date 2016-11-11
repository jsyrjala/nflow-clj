(ns nflow-clj.database.connection-pool
  "Database connection pooling."
  (:require [clojure.tools.logging :as log]
            )
  (:import (com.zaxxer.hikari HikariDataSource HikariConfig)))

(defn create-hikari-pool [db-spec]
  (log/info db-spec)
  (let [hikari-config (new HikariConfig)
        {:keys [datasource-classname
                max-connections
                max-lifetime
                auto-commit
                connection-uri
                username
                password
                register-mbeans
                leak-detection-threshold]} db-spec]
    (doto hikari-config
      (.setDataSourceClassName datasource-classname)
      (.setMaximumPoolSize max-connections)
      (.setMaxLifetime max-lifetime)
      (.setAutoCommit auto-commit)
      (.addDataSourceProperty "URL", connection-uri)
      (.addDataSourceProperty "user" username)
      (.addDataSourceProperty "password" password)
      (.setRegisterMbeans register-mbeans)
      (.setLeakDetectionThreshold leak-detection-threshold))
    {:datasource (new HikariDataSource hikari-config)}))
