(ns nflow-clj.database.connection-pool
  "Database connection pooling."
  (:require [clojure.tools.logging :as log]
            )
  )

(defn create-hikari-pool [db-spec]
  (let [hikari-config (new com.zaxxer.hikari.HikariConfig)
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
    (new com.zaxxer.hikari.HikariDataSource hikari-config) ))
