(ns frontend.components.network-proxy
  (:require [clojure.string :as string]
            [electron.ipc :as ipc]
            [frontend.context.i18n :refer [t]]
            [frontend.handler.notification :as notification]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [frontend.util :as util]
            [promesa.core :as p]
            [rum.core :as rum]))

(rum/defc settings-panel
  [{:keys [protocol type] :as agent-opts}]
  (let [type        (or (not-empty type) (not-empty protocol) "system")
        [opts set-opts!] (rum/use-state agent-opts)
        [testing? set-testing?!] (rum/use-state false)
        *test-input (rum/create-ref)
        disabled?   (or (= (:type opts) "system") (= (:type opts) "direct"))]
    [:div.cp__settings-network-proxy-panel
     [:h1.mb-2.text-2xl.font-bold (t :settings-page/network-proxy)]
     [:div.p-2
      [:p [:label [:strong (t :type)]
           (ui/select [{:label "System" :value "system" :selected (= type "system")}
                       {:label "Direct" :value "direct" :selected (= type "direct")}
                       {:label "HTTP" :value "http" :selected (= type "http")}
                       {:label "SOCKS5" :value "socks5" :selected (= type "socks5")}]
                      (fn [_e value]
                        (set-opts! (assoc opts :type value :protocol value))))]]
      [:p.flex
       [:label.pr-4
        {:class (if disabled? "opacity-50" nil)}
        [:strong (t :host)]
        [:input.form-input.is-small
         {:value     (:host opts)
          :disabled  disabled?
          :on-change #(set-opts!
                       (assoc opts :host (util/trim-safe (util/evalue %))))}]]

       [:label
        {:class (if disabled? "opacity-50" nil)}
        [:strong (t :port)]
        [:input.form-input.is-small
         {:value       (:port opts)
          :type        "number"
          :min         1
          :max         65535
          :disabled    disabled?
          :on-change   #(set-opts!
                         (assoc opts :port (util/trim-safe (util/evalue %))))}]]]

      [:hr]
      [:p.flex.items-center.space-x-2
       [:span.w-60
        [:input.form-input.is-small
         {:ref         *test-input
          :list        "proxy-test-url-datalist"
          :type        "url"
          :placeholder "https://"
          :on-change   #(set-opts!
                         (assoc opts :test (util/trim-safe (util/evalue %))))
          :value       (:test opts)}]
        [:datalist#proxy-test-url-datalist
         [:option "https://api.logseq.com/logseq/version"]
         [:option "https://logseq-connectivity-testing-prod.s3.us-east-1.amazonaws.com/logseq-connectivity-testing"]
         [:option "https://www.google.com"]
         [:option "https://s3.amazonaws.com"]
         [:option "https://clients3.google.com/generate_204"]]]

       (ui/button (if testing? (ui/loading "Testing") "Test URL")
                  :intent "logseq"
                  :on-click #(let [val (util/trim-safe (.-value (rum/deref *test-input)))]
                               (when (and (not testing?) (not (string/blank? val)))
                                 (set-testing?! true)
                                 (-> (p/let [result (ipc/ipc :testProxyUrl val opts)]
                                       (js->clj result :keywordize-keys true))
                                     (p/then (fn [{:keys [code response-ms]}]
                                               (notification/clear! :proxy-net-check)
                                               (notification/show! (str "Success! Status " code " in " response-ms "ms.") :success)))
                                     (p/catch (fn [e]
                                                (notification/show! (str e) :error false :proxy-net-check)))
                                     (p/finally (fn [] (set-testing?! false)))))))]

      [:p.pt-2
       (ui/button (t :save)
                  :on-click (fn []
                              (p/let [_ (ipc/ipc :setProxy opts)]
                                (state/set-state! [:electron/user-cfgs :settings/agent] opts)
                                (state/close-sub-modal! :https-proxy-panel))))]]]))
