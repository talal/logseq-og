(ns frontend.modules.instrumentation.posthog
  (:require ["posthog-js" :as posthog]
            [cljs-bean.core :as bean]
            [frontend.config :as config]
            [frontend.util :as util]
            [frontend.version :refer [version]]))

(goog-define POSTHOG-TOKEN "")
(def ^:const masked "masked")

(defn register []
  (posthog/register
   (clj->js
    {:app_type (if (util/electron?) "electron" "web")
     :app_env (if config/dev? "development" "production")
     :app_ver version
     :schema_ver 0
     ;; hack, did not find ways to hack data on-the-fly with posthog-js
     :$current_url masked
     :$pathname masked})))

(def config
  {:api_host "https://app.posthog.com"
   :persistence "localStorage"
   :autocapture false
   :disable_session_recording true
   :mask_all_text true
   :mask_all_element_attributes true
   :loaded (fn [_] (register))})

(defn init []
  (when (and (not config/dev?) (not-empty POSTHOG-TOKEN))
    (posthog/init POSTHOG-TOKEN (clj->js config))))

(defn opt-out [opt-out?]
  (if opt-out?
    (posthog/opt_out_capturing)
    (do
      (init)
      (posthog/opt_in_capturing))))

(defn capture [id data]
  (try
    (posthog/capture (str id) (bean/->js data))
    (catch :default e
      (js/console.error e)
      ;; opt out or network issues
      nil)))

(comment
  (posthog/debug))
