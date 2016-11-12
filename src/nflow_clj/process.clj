(ns nflow-clj.process
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as time]
            ))

(defn start [ctx]
  {:next-state :read-subscription
   :next-activation (time/now)})

(defn read-subscription [ctx]
  {:next-state :update-accounting
   :next-activation (time/now)})

(defn update-accounting [ctx]
  (log/info "Updating accounting" (get-in ctx [:config :accounting-api]))
  (when (= 0 (rand-int 4))
    (throw (ex-info "Oh no, accounting is down" {})))
  {:next-state :update-press
   :next-activation (time/now)})

(defn update-press [ctx]
  (log/info "Updating press" (get-in ctx [:config :press-api]))
  {:next-state :send-email
   :next-activation (time/now)})

(defn send-email [ctx]
  (log/info "Sending email" (get-in ctx [:workflow :state :email]))
  {:next-state :finished
   :next-activation (time/now)})

(defn manual-processing [ctx]
  (log/warn "Manual processing needed" (:process ctx) (:workflow ctx))
  {:next-activation nil})

(defn error [ctx]
  (log/error (:exception ctx) "Failed")
  {:next-activation nil})

(defn finished [ctx]
  (log/info "Process finished.")
  {:next-activation nil})

(def subscription {
              :type :order-management/akuankka-tilaus
              :name "Aku Ankka tilaus"
              :description "Aku Ankan tilausprosessi"
              :error-state :error
              :states [
                       {:id :start
                        :name "aloitus"
                        :execute-fn start
                        :next-states [:read-subcription]
                        :status :nflow-instance-type/inProgress
                        }
                       {:id :read-subcription
                        :name "lue tilaus"
                        :execute-fn read-subscription
                        :next-states [:update-accounting :manual-handling]
                        :status :nflow-instance-type/inProgress
                        }
                       {:id :update-accounting
                        :name "paivita laskutus"
                        :execute-fn update-accounting
                        :next-states [:update-press :manual-handling]
                        :status :nflow-instance-type/inProgress
                        }
                       {:id :update-press
                        :name "paivita kirjapaino"
                        :execute-fn update-press
                        :next-states [:send-email :manual-handling]
                        :status :nflow-instance-type/inProgress
                        }
                       {:id :send-email
                        :name "lähetä vahvistusemail"
                        :execute-fn send-email
                        :next-states [:finished]
                        :status :nflow-instance-type/inProgress}
                       {:id         :manual-handling
                        :name       "manuaalikäsittely"
                        :execute-fn manual-processing
                        :status     :nflow-instance-type/manual
                        }
                       {:id :error
                        :name "virhetila"
                        :execute-fn error
                        :status :nflow-instance-type/manual
                        }
                       {:id :finished
                        :execute-fn finished
                        :name "Prosessi valmis"
                        :status :nflow-instance-type/finished
                        }
                       ]
              })
