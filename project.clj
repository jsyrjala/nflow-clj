(defproject nflow-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [
                 [org.clojure/clojure "1.9.0-alpha14"]

                 ;; business logic
                 [cheshire "5.6.3"]
                 [camel-snake-kebab "0.4.0"]

                 ;; db
                 [com.layerware/hugsql "0.4.7"]
                 [mysql/mysql-connector-java "5.1.39"]
                 [com.zaxxer/HikariCP "2.5.1"]
                 [com.climate/claypoole "1.1.3"]

                 ;; Logging
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]
                 [ch.qos.logback/logback-classic "1.1.7"
                  :exclusions [org.slf4j/slf4j-api]]
                 ]

  :profiles {:dev {
                   :source-paths ["dev"]
                   :dependencies [
                                  [midje "1.8.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [reloaded.repl "0.2.3"]
                                  ]
                   }

             :uberjar {
                       :aot :all
                       }
             }

  :plugins [[lein-midje "3.2"]
            [lein-marginalia "0.9.0"]
            [lein-ancient "0.6.10"]]

  ;; :global-vars {*warn-on-reflection* true}

  )
