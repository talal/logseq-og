(ns frontend.loader
  "Provides fns related to loading js assets"
  (:require [cljs-bean.core :as bean]
            [goog.html.legacyconversions :as conv]
            [goog.net.jsloader :as jsloader]))

(defn load
  ([url ok-handler] (load url ok-handler nil))
  ([url ok-handler opts]
   (let [loader (jsloader/safeLoad
                 (conv/trustedResourceUrlFromString (str url))
                 (bean/->js opts))]
     (.addCallback ^goog.net.jsloader loader ok-handler))))
