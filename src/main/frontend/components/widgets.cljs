(ns frontend.components.widgets
  (:require [frontend.config :as config]
            [frontend.context.i18n :refer [t]]
            [frontend.handler.page :as page-handler]
            [frontend.handler.web.nfs :as nfs]
            [frontend.modules.shortcut.core :as shortcut]
            [frontend.ui :as ui]
            [rum.core :as rum]))

(rum/defc filesystem-api-alert
  []
  [:p "It seems that your browser doesn't support the "
   [:a {:href   "https://web.dev/file-system-access/"
        :target "_blank"}
    "File System Access API"]
   [:span ", please use a Chromium 86+ based browser like Chrome, Vivaldi, or Edge."]])

(rum/defc add-local-directory
  []
  [:div.flex.flex-col
   [:h1.title (t :graph-setup/add-graph)]
   (let [nfs-supported? (nfs/supported?)]
     [:div.cp__widgets-open-local-directory
      [:div.select-file-wrap.cursor
       (when nfs-supported?
         {:on-click #(page-handler/ls-dir-files! shortcut/refresh!)})
       [:div
        [:h1.title (t :graph-setup/open-local-dir)]
        [:p (t :graph-setup/new-graph-desc-1)]
        [:p (t :graph-setup/new-graph-desc-2)]
        [:ul
         [:li (t :graph-setup/new-graph-desc-3)]
         [:li (t :graph-setup/new-graph-desc-4)]
         [:li (t :graph-setup/new-graph-desc-5)]]
        (when-not nfs-supported?
          (ui/admonition :warning (filesystem-api-alert)))]]])])

(rum/defcs add-graph <
  [state & {:keys [graph-types]
            :or {graph-types [:local]}}]
  (let [generate-f (fn [x]
                     (case x
                       :local
                       [(rum/with-key (add-local-directory)
                          "add-local-directory")]

                       nil))
        available-graph (->> (set graph-types)
                             (keep generate-f)
                             (vec)
                             (interpose [:b.mt-10.mb-5.opacity-50 "OR"]))]
    [:div.p-8.flex.flex-col available-graph]))

(rum/defc demo-graph-alert
  []
  (when (config/demo-graph?)
    (ui/admonition
     :warning
     [:p (t :graph-setup/demo-graph)])))
