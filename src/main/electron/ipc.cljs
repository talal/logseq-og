(ns electron.ipc
  "Provides fns to send ipc messages to electron's main process"
  (:require [cljs-bean.core :as bean]
            [frontend.util :as util]
            [promesa.core :as p]))

;; TODO: handle errors
(defn ipc
  [& args]
  (when (util/electron?)
    (p/let [result (js/window.apis.doAction (bean/->js args))]
      result)))

