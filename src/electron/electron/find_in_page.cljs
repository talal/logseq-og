(ns electron.find-in-page
  (:require [cljs-bean.core :as bean]
            [electron.utils :as utils]))

(defn find!
  [^js window search option]
  (when window
    (let [contents ^js (.-webContents window)]
      (.findInPage contents search option)
      (.on contents "found-in-page"
           (fn [_event result]
             (utils/send-to-renderer window "foundInPage" (bean/->clj result))))
      true)))

(defn clear!
  [^js window]
  (when window
    (.stopFindInPage ^js (.-webContents window) "clearSelection")))
