(ns electron.exceptions
  (:require [clojure.string :as string]
            [electron.logger :as logger]
            [electron.utils :as utils]))

(defonce uncaughtExceptionChan "uncaughtException")

(defn show-error-tip
  [& msg]
  (utils/send-to-renderer "notification"
                          {:type    "error"
                           :payload (string/join "\n" msg)}))

(defn- app-uncaught-handler
  [^js e]
  (let [msg (.-message e)
        stack (.-stack e)]
    (show-error-tip "[Main Exception]" msg stack))

  ;; for debug log
  (logger/error uncaughtExceptionChan (str e)))

(defn setup-exception-listeners!
  []
  (js/process.on uncaughtExceptionChan app-uncaught-handler)
  #(js/process.off uncaughtExceptionChan app-uncaught-handler))
