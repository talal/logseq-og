(ns frontend.components.graph-setup
  (:require [clojure.string :as string]
            [frontend.components.svg :as svg]
            [frontend.components.widgets :as widgets]
            [frontend.context.i18n :refer [t]]
            [frontend.handler.external :as external-handler]
            [frontend.handler.notification :as notification]
            [frontend.handler.page :as page-handler]
            [frontend.handler.route :as route-handler]
            [frontend.handler.ui :as ui-handler]
            [frontend.handler.web.nfs :as nfs]
            [frontend.modules.shortcut.core :as shortcut]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [goog.object :as gobj]
            [rum.core :as rum]))

(defn- device []
  (t :graph-setup/section-computer))

(rum/defc setups-container
  [flag content]

  [:div.cp__graph-setup-setups.flex.flex-1
   (let [picker? (= flag :picker)]
     [:div.inner-card.flex.flex-col.items-center

      [:h1.text-xl
       (if picker?
         [:span.flex.items-center.gap-1
          [:strong (ui/icon "heart" {:size 30})] (t :graph-setup/main-title)]
         [:span.flex.items-center.gap-1
          [:strong (ui/icon "file-import" {:size 30})] (t :graph-setup/importing-main-title)])]

      [:h2.opacity-60
       (if picker?
         (t :graph-setup/main-desc)
         (t :graph-setup/importing-main-desc))]

      content])])

(rum/defcs picker < rum/reactive
  [_route-match]
  (let [parsing? (state/sub :repo/parsing-files?)]

    (setups-container
     :picker
     [:article.flex.w-full
      [:section.a.
       (if (nfs/supported?)
         [:div.choose.flex.flex-col.items-center
          {:on-click #(page-handler/ls-dir-files!
                       (fn []
                         (shortcut/refresh!)))}
          [:i]
          [:div.control
           [:label.action-input.flex.items-center.justify-center.flex-col
            {:disabled parsing?}

            (if parsing?
              (ui/loading "")
              [[:strong (t :graph-setup/section-btn-title)]
               [:small (t :graph-setup/section-btn-desc)]])]]]
         [:div.px-5
          (ui/admonition :warning
                         (widgets/filesystem-api-alert))])]
      [:section.b.flex.items-center.flex-col
       [:p.flex
        [:i.as-flex-center (ui/icon "zoom-question" {:style {:fontSize "22px"}})]
        [:span.flex-1.flex.flex-col
         [:strong (t :graph-setup/section-title)]
         [:small.opacity-60 (t :graph-setup/section-desc)]]]

       [:p.text-sm.pt-5.tracking-wide
        [:span (str (t :graph-setup/section-tip-1 (device)))]
        [:br]
        [:span (t :graph-setup/section-tip-2)]]

       [:ul
        (for [[title label icon]
              [[(t :graph-setup/section-journals) "/journals" "calendar-plus"]
               [(t :graph-setup/section-pages) "/pages" "page"]
               []
               [(t :graph-setup/section-app) "/logseq" "tool"]
               [(t :graph-setup/section-config) "/logseq/config.edn" "settings"]]]
          (if-not title
            [:li.hr]
            [:li
             {:key title}
             [:i.as-flex-center
              {:class (when (string/ends-with? label ".edn") "is-file")}
              (when icon (ui/icon icon))]
             [:span
              [:strong.uppercase title]
              [:small.opacity-50 label]]]))]]])))

(defonce *opml-imported-pages (atom nil))

(defn- finished-cb
  []
  (route-handler/redirect-to-home!)
  (notification/show! "Import finished!" :success)
  (ui-handler/re-render-root!))

(defn- roam-import-handler
  [e]
  (let [file (first (array-seq (.-files (.-target e))))
        file-name (gobj/get file "name")]
    (if (string/ends-with? file-name ".json")
      (do
        (state/set-state! :graph/importing :roam-json)
        (let [reader (js/FileReader.)]
          (set! (.-onload reader)
                (fn [e]
                  (let [text (.. e -target -result)]
                    (external-handler/import-from-roam-json!
                     text
                     #(do
                        (state/set-state! :graph/importing nil)
                        (finished-cb))))))
          (.readAsText reader file)))
      (notification/show! "Please choose a JSON file."
                          :error))))

(defn- lsq-import-handler
  [e]
  (let [file (first (array-seq (.-files (.-target e))))
        file-name (some-> (gobj/get file "name")
                          (string/lower-case))
        edn? (string/ends-with? file-name ".edn")
        json? (string/ends-with? file-name ".json")]
    (if (or edn? json?)
      (do
        (state/set-state! :graph/importing :logseq)
        (let [reader (js/FileReader.)
              import-f (if edn?
                         external-handler/import-from-edn!
                         external-handler/import-from-json!)]
          (set! (.-onload reader)
                (fn [e]
                  (let [text (.. e -target -result)]
                    (import-f
                     text
                     #(do
                        (state/set-state! :graph/importing nil)
                        (finished-cb))))))
          (.readAsText reader file)))
      (notification/show! "Please choose an EDN or a JSON file."
                          :error))))

(defn- opml-import-handler
  [e]
  (let [file (first (array-seq (.-files (.-target e))))
        file-name (gobj/get file "name")]
    (if (string/ends-with? file-name ".opml")
      (do
        (state/set-state! :graph/importing :opml)
        (let [reader (js/FileReader.)]
          (set! (.-onload reader)
                (fn [e]
                  (let [text (.. e -target -result)]
                    (external-handler/import-from-opml! text
                                                        (fn [pages]
                                                          (reset! *opml-imported-pages pages)
                                                          (state/set-state! :graph/importing nil)
                                                          (finished-cb))))))
          (.readAsText reader file)))
      (notification/show! "Please choose a OPML file."
                          :error))))

(rum/defc importer < rum/reactive
  [{:keys [query-params]}]
  (if (state/sub :graph/importing)
    (let [{:keys [total current-idx current-page]} (state/sub :graph/importing-state)
          left-label [:div.flex.flex-row.font-bold
                      (t :importing)
                      [:div.hidden.md:flex.flex-row
                       [:span.mr-1 ": "]
                       [:div.text-ellipsis-wrapper {:style {:max-width 300}}
                        current-page]]]
          width (js/Math.round (* (.toFixed (/ current-idx total) 2) 100))
          process (when (and total current-idx)
                    (str current-idx "/" total))]
      (ui/progress-bar-with-label width left-label process))
    (setups-container
     :importer
     [:article.flex.flex-col.items-center.importer.py-16.px-8
      [:section.c.text-center
       [:h1 (t :graph-setup/importing-title)]
       [:h2 (t :graph-setup/importing-desc)]]
      [:section.d.md:flex
       [:label.action-input.flex.items-center.mx-2.my-2
        [:span.as-flex-center [:i (svg/roam-research 28)]]
        [:div.flex.flex-col
         [[:strong "RoamResearch"]
          [:small (t :graph-setup/importing-roam-desc)]]]
        [:input.absolute.hidden
         {:id        "import-roam"
          :type      "file"
          :on-change roam-import-handler}]]

       [:label.action-input.flex.items-center.mx-2.my-2
        [:span.as-flex-center [:i (svg/logo 28)]]
        [:span.flex.flex-col
         [[:strong "EDN / JSON"]
          [:small (t :graph-setup/importing-lsq-desc)]]]
        [:input.absolute.hidden
         {:id        "import-lsq"
          :type      "file"
          :on-change lsq-import-handler}]]

       [:label.action-input.flex.items-center.mx-2.my-2
        [:span.as-flex-center (ui/icon "sitemap" {:style {:fontSize "26px"}})]
        [:span.flex.flex-col
         [[:strong "OPML"]
          [:small (t :graph-setup/importing-opml-desc)]]]

        [:input.absolute.hidden
         {:id        "import-opml"
          :type      "file"
          :on-change opml-import-handler}]]]

      (when (= "picker" (:from query-params))
        [:section.e
         [:a.button {:on-click #(route-handler/redirect-to-home!)} "Skip"]])])))
