(ns ^:no-doc logseq.sdk.debug
  (:require [cljs-bean.core :as bean]
            [frontend.state :as state]))

(defn ^:export log_app_state
  [path]
  (-> (if (string? path)
        (get @state/state (keyword path))
        @state/state)
      (bean/->js)))
