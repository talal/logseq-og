(ns frontend.handler.events
  "System-component-like ns that defines named events and listens on a
  core.async channel to handle them. Any part of the system can dispatch
  one of these events using state/pub-event!"
  (:refer-clojure :exclude [run!])
  (:require
   [clojure.core.async :as async]
   [clojure.set :as set]
   [clojure.string :as string]
   [electron.ipc :as ipc]
   [frontend.components.cmdk :as cmdk]
   [frontend.components.conversion :as conversion-component]
   [frontend.components.diff :as diff]
   [frontend.components.settings :as settings]
   [frontend.components.shell :as shell]
   [frontend.components.themes :as themes]
   [frontend.components.whiteboard :as whiteboard]
   [frontend.config :as config]
   [frontend.context.i18n :refer [t]]
   [frontend.db :as db]
   [frontend.db.model :as db-model]
   [frontend.extensions.srs :as srs]
   [frontend.fs :as fs]
   [frontend.fs.nfs :as nfs]
   [frontend.fs.watcher-handler :as fs-watcher]
   [frontend.handler.common :as common-handler]
   [frontend.handler.editor :as editor-handler]
   [frontend.handler.file :as file-handler]
   [frontend.handler.notification :as notification]
   [frontend.handler.page :as page-handler]
   [frontend.handler.repo :as repo-handler]
   [frontend.handler.repo-config :as repo-config-handler]
   [frontend.handler.route :as route-handler]
   [frontend.handler.search :as search-handler]
   [frontend.handler.shell :as shell-handler]
   [frontend.handler.ui :as ui-handler]
   [frontend.handler.web.nfs :as nfs-handler]
   [frontend.handler.whiteboard :as whiteboard-handler]
   [frontend.idb :as idb]
   [frontend.modules.outliner.file :as outliner-file]
   [frontend.modules.shortcut.core :as st]
   [frontend.quick-capture :as quick-capture]
   [frontend.state :as state]
   [frontend.ui :as ui]
   [frontend.util :as util]
   [logseq.db.schema :as db-schema]
   [logseq.graph-parser.config :as gp-config]
   [promesa.core :as p]
   [rum.core :as rum]))

;; TODO: should we move all events here?

(defmulti handle first)

(defmethod handle :graph/added [[_ repo {:keys [empty-graph?]}]]
  (db/set-key-value repo :ast/version db-schema/ast-version)
  (search-handler/rebuild-indices!)
  (db/persist! repo)
  (when (state/setups-picker?)
    (if empty-graph?
      (route-handler/redirect! {:to :import :query-params {:from "picker"}})
      (route-handler/redirect-to-home!)))
  (when-let [dir-name (config/get-repo-dir repo)]
    (fs/watch-dir! dir-name)))

(defn- graph-switch
  [graph]
  (state/set-current-repo! graph)
  ;; load config
  (repo-config-handler/restore-repo-config! graph)
  (when-not (= :draw (state/get-current-route))
    (route-handler/redirect-to-home!))
  (srs/update-cards-due-count!)
  (state/pub-event! [:graph/ready graph])
  (when-let [dir-name (config/get-repo-dir graph)]
    (fs/watch-dir! dir-name)))

;; Parameters for the `persist-db` function, to show the notification messages
(def persist-db-noti-m
  {:before     #(ui/notify-graph-persist!)
   :on-error   #(ui/notify-graph-persist-error!)})

(defn- graph-switch-on-persisted
  [graph {:keys [persist?]}]
  (let [current-repo (state/get-current-repo)]
    (p/do!
     (when persist?
       (when (util/electron?)
         (p/do!
          (repo-handler/persist-db! current-repo persist-db-noti-m)
          (repo-handler/broadcast-persist-db! graph))))
     (repo-handler/restore-and-setup-repo! graph)
     (graph-switch graph))))

(defmethod handle :graph/switch [[_ graph opts]]
  (let [opts (if (false? (:persist? opts)) opts (assoc opts :persist? true))]
    (if (not (false? (get @outliner-file/*writes-finished? graph)))
      (graph-switch-on-persisted graph opts)
      (notification/show!
       "Please wait seconds until all changes are saved for the current graph."
       :warning))))

(defmethod handle :graph/open-new-window [[_ev repo]]
  (p/let [current-repo (state/get-current-repo)
          target-repo (or repo current-repo)
          _ (repo-handler/persist-db! current-repo persist-db-noti-m) ;; FIXME: redundant when opening non-current-graph window
          _ (when-not (= current-repo target-repo)
              (repo-handler/broadcast-persist-db! repo))]
    (ui-handler/open-new-window! repo)))

(defmethod handle :graph/migrated [[_ _repo]]
  (js/alert "Graph migrated."))

(defmethod handle :graph/save [_]
  (repo-handler/persist-db! (state/get-current-repo)
                            {:before     #(notification/show!
                                           (ui/loading (t :graph/save))
                                           :warning)
                             :on-success #(do
                                            (notification/clear-all!)
                                            (notification/show!
                                             (t :graph/save-success)
                                             :success))
                             :on-error   #(notification/show!
                                           (t :graph/save-error)
                                           :error)}))

(defn get-local-repo
  []
  (let [repo (state/get-current-repo)]
    (when (config/local-db? repo)
      repo)))

(defn ask-permission
  [repo]
  (when
   (not (util/electron?))
    (fn [close-fn]
      [:div
       ;; TODO: fn translation with args
       [:p
        "Grant filesystem permission for directory: "
        [:b (config/get-local-dir repo)]]
       (ui/button
        (t :settings-permission/start-granting)
        :class "ui__modal-enter"
        :on-click (fn []
                    (nfs/check-directory-permission! repo)
                    (close-fn)))])))

(defmethod handle :modal/nfs-ask-permission []
  (when-let [repo (get-local-repo)]
    (state/set-modal! (ask-permission repo))))

(defonce *query-properties (atom {}))
(rum/defc query-properties-settings-inner < rum/reactive
  {:will-unmount (fn [state]
                   (reset! *query-properties {})
                   state)}
  [block shown-properties all-properties _close-fn]
  (let [query-properties (rum/react *query-properties)]
    [:div.p-4
     [:div.font-bold (t :query/config-property-settings)]
     (for [property all-properties]
       (let [property-value (get query-properties property)
             shown? (if (nil? property-value)
                      (contains? shown-properties property)
                      property-value)]
         [:div.flex.flex-row.m-2.justify-between.align-items
          [:div (name property)]
          [:div.mt-1 (ui/toggle shown?
                                (fn []
                                  (let [value (not shown?)]
                                    (swap! *query-properties assoc property value)
                                    (editor-handler/set-block-query-properties!
                                     (:block/uuid block)
                                     all-properties
                                     property
                                     value)))
                                true)]]))]))

(defn query-properties-settings
  [block shown-properties all-properties]
  (fn [close-fn]
    (query-properties-settings-inner block shown-properties all-properties close-fn)))

(defmethod handle :modal/set-query-properties [[_ block all-properties]]
  (let [block-properties (some-> (get-in block [:block/properties :query-properties])
                                 (common-handler/safe-read-string "Parsing query properties failed"))
        shown-properties (if (seq block-properties)
                           (set block-properties)
                           (set all-properties))
        shown-properties (set/intersection (set all-properties) shown-properties)]
    (state/set-modal! (query-properties-settings block shown-properties all-properties)
                      {:center? true})))

(defmethod handle :modal/show-cards [_]
  (state/set-modal! srs/global-cards {:id :srs
                                      :label "flashcards__cp"}))

(defmethod handle :modal/show-themes-modal [_]
  (themes/open-select-theme!))

(defmethod handle :modal/toggle-accent-colors-modal [_]
  (let [label "accent-colors-picker"]
    (if (or (= label (state/get-modal-id))
            (= label (some-> (state/get-sub-modals) (first) :modal/id)))
      (state/close-sub-modal! label)
      (state/set-sub-modal!
       #(settings/modal-accent-colors-inner)
       {:center? true
        :id      label
        :label   label}))))

(rum/defc modal-output
  [content]
  content)

(defmethod handle :modal/show [[_ content]]
  (state/set-modal! #(modal-output content)))

(defmethod handle :page/title-property-changed [[_ old-title new-title]]
  (page-handler/rename! old-title new-title))

(defmethod handle :page/create [[_ page-name opts]]
  (page-handler/create! page-name opts))

(defmethod handle :page/create-today-journal [[_ _repo]]
  (p/let [_ (page-handler/create-today-journal!)]
    (ui-handler/re-render-root!)))

(defmethod handle :file/not-matched-from-disk [[_ path disk-content db-content]]
  (state/clear-edit!)
  (let [repo (state/get-current-repo)]
    (when (and disk-content db-content
               (not= (util/trim-safe disk-content) (util/trim-safe db-content)))
      (state/set-modal! #(diff/local-file repo path disk-content db-content)
                        {:label "diff__cp"}))))

;; Hook on a graph is ready to be shown to the user.
;; It's different from :graph/restored, as :graph/restored is for window reloaded
;; FIXME: config may not be loaded when the graph is ready.
(defmethod handle :graph/ready
  [[_ repo]]
  (when (config/local-db? repo)
    (p/let [dir               (config/get-repo-dir repo)
            dir-exists?       (fs/dir-exists? dir)]
      (when (and (not dir-exists?)
                 (not util/nfs?))
        (state/pub-event! [:graph/dir-gone dir]))))
  (p/let [loaded-homepage-files (fs-watcher/preload-graph-homepage-files!)
          ;; FIXME: an ugly implementation for redirecting to page on new window is restored
          _ (repo-handler/graph-ready! repo)
          _ (fs-watcher/load-graph-files! repo loaded-homepage-files)]))

(defmethod handle :notification/show [[_ {:keys [content status clear?]}]]
  (notification/show! content status clear?))

(defmethod handle :command/run [_]
  (when (util/electron?)
    (state/set-modal! shell/shell)))

(defmethod handle :go/search [_]
  (state/set-modal! cmdk/cmdk-modal
                    {:fullscreen? true
                     :close-btn?  false
                     :panel?      false
                     :label "ls-modal-search"}))

(defmethod handle :redirect-to-home [_]
  (page-handler/create-today-journal!))

(defmethod handle :rebuild-slash-commands-list [[_]]
  (page-handler/rebuild-slash-commands-list!))

(defmethod handle :shortcut/refresh [[_]]
  (st/refresh!))

(defn- refresh-cb []
  (page-handler/create-today-journal!))

(defmethod handle :graph/ask-for-re-fresh [_]
  (handle
   [:modal/show
    [:div {:style {:max-width 700}}
     [:p (t :refresh-from-local-changes-detected)]
     (ui/button
      (t :yes)
      :autoFocus "on"
      :class "ui__modal-enter"
      :on-click (fn []
                  (state/close-modal!)
                  (nfs-handler/refresh! (state/get-current-repo) refresh-cb)))]]))

(defmethod handle :graph/re-index [[_]]
  (repo-handler/re-index!
   nfs-handler/rebuild-index!
   #(page-handler/create-today-journal!)))

;; FIXME: move
(defn- clear-cache!
  []
  (notification/show! "Clearing..." :warning false)
  (p/let [_ (when (util/electron?)
              (ipc/ipc "clearCache"))
          _ (idb/clear-local-storage-and-idb!)]
    (js/setTimeout
     (fn [] (if (util/electron?)
              (ipc/ipc :reloadWindowPage)
              (js/window.location.reload)))
     2000)))

(defmethod handle :graph/clear-cache! [[_]]
  (clear-cache!))

(defmethod handle :graph/ask-for-re-index [[_ *multiple-windows? ui]]
  ;; *multiple-windows? - if the graph is opened in multiple windows, boolean atom
  ;; ui - custom message to show on asking for re-index
  (if (and (util/atom? *multiple-windows?) @*multiple-windows?)
    (handle
     [:modal/show
      [:div
       (when (not (nil? ui)) ui)
       [:p (t :re-index-multiple-windows-warning)]]])
    (handle
     [:modal/show
      [:div {:style {:max-width 700}}
       (when (not (nil? ui)) ui)
       [:p (t :re-index-discard-unsaved-changes-warning)]
       (ui/button
        (t :yes)
        :autoFocus "on"
        :class "ui__modal-enter"
        :on-click (fn []
                    (state/close-modal!)
                    (state/pub-event! [:graph/re-index])))]])))

(defmethod handle :journal/insert-template [[_ page-name]]
  (let [page-name (util/page-name-sanity-lc page-name)]
    (when-let [page (db/pull [:block/name page-name])]
      (when (db/page-empty? (state/get-current-repo) page-name)
        (when-let [template (state/get-default-journal-template)]
          (editor-handler/insert-template!
           nil
           template
           {:target page}))))))

(defmethod handle :editor/set-org-mode-heading [[_ block heading]]
  (when-let [id (:block/uuid block)]
    (editor-handler/set-heading! id heading)))

(defmethod handle :whiteboard/onboarding [[_ opts]]
  (state/set-modal!
   (fn [close-fn] (whiteboard/onboarding-welcome close-fn))
   (merge {:close-btn?      false
           :center?         true
           :close-backdrop? false} opts)))

(defmethod handle :graph/restored [[_ _graph]]
  (state/publish-graph-ready!))

(defmethod handle :whiteboard-link [[_ shapes]]
  (route-handler/go-to-search! :whiteboard/link)
  (state/set-state! :whiteboard/linked-shapes shapes))

(defmethod handle :whiteboard-go-to-link [[_ link]]
  (route-handler/redirect! {:to :whiteboard
                            :path-params {:name link}}))

(defmethod handle :graph/dir-gone [[_ dir]]
  (state/pub-event! [:notification/show
                     {:content (str "The directory " dir " has been renamed or deleted, the editor will be disabled for this graph, you can unlink the graph.")
                      :status :error
                      :clear? false}])
  (state/update-state! :file/unlinked-dirs (fn [dirs] (conj dirs dir))))

(defmethod handle :graph/dir-back [[_ repo dir]]
  (when (contains? (:file/unlinked-dirs @state/state) dir)
    (notification/clear-all!)
    (state/pub-event! [:notification/show
                       {:content (str "The directory " dir " has been back, you can edit your graph now.")
                        :status :success
                        :clear? true}])
    (state/update-state! :file/unlinked-dirs (fn [dirs] (disj dirs dir)))
    (when (= dir (config/get-repo-dir repo))
      (fs/watch-dir! dir))))

(defmethod handle :ui/notify-outdated-filename-format [[_ paths]]
  ;; paths - the affected paths that contains reserved characters
  (notification/show!
   [:div
    [:div.mb-4
     [:div.font-semibold.mb-4.text-xl "It seems that some of your filenames are in the outdated format."]

     [:div
      [:p
       "We suggest you upgrade now to avoid potential bugs."]
      (when (seq paths)
        [:p
         "For example, the files below have reserved characters that aren't supported on some platforms."])]]
    (ui/button
     "Update filename format"
     :aria-label "Update filename format"
     :on-click (fn []
                 (notification/clear-all!)
                 (state/set-modal!
                  (fn [_] (conversion-component/files-breaking-changed))
                  {:id :filename-format-panel :center? true})))
    (when (seq paths)
      [:ol.my-2
       (for [path paths]
         [:li path])])]
   :warning
   false))

(defmethod handle :graph/setup-a-repo [[_ opts]]
  (let [opts' (merge {:picked-root-fn #(state/close-modal!)} opts)]
    (page-handler/ls-dir-files! st/refresh! opts')))

(defmethod handle :file/alter [[_ repo path content]]
  (p/let [_ (file-handler/alter-file repo path content {:from-disk? true})]
    (ui-handler/re-render-root!)))

(rum/defcs file-id-conflict-item <
  (rum/local false ::resolved?)
  [state repo file data]
  (let [resolved? (::resolved? state)
        id (last (:assertion data))]
    [:li {:key file}
     [:div
      [:a {:on-click #(js/window.apis.openPath file)} file]
      (if @resolved?
        [:div.flex.flex-row.items-center
         (ui/icon "circle-check" {:style {:font-size 20}})
         [:div.ml-1 "Resolved"]]
        [:div
         [:p
          (str "It seems that another whiteboard file already has the ID \"" id
               "\". You can fix it by changing the ID in this file with another UUID.")]
         [:p
          "Or, let me"
          (ui/button "Fix"
                     :on-click (fn []
                                 (let [dir (config/get-repo-dir repo)]
                                   (p/let [content (fs/read-file dir file)]
                                     (let [new-content (string/replace content (str id) (str (random-uuid)))]
                                       (p/let [_ (fs/write-plain-text-file! repo
                                                                            dir
                                                                            file
                                                                            new-content
                                                                            {})]
                                         (reset! resolved? true))))))
                     :class "inline mx-1")
          "it."]])]]))

(defmethod handle :file/parse-and-load-error [[_ repo parse-errors]]
  (state/pub-event! [:notification/show
                     {:content
                      [:div
                       [:h2.title "Oops. These files failed to import to your graph:"]
                       [:ol.my-2
                        (for [[file error] parse-errors]
                          (let [data (ex-data error)]
                            (cond
                              (and (gp-config/whiteboard? file)
                                   (= :transact/upsert (:error data))
                                   (uuid? (last (:assertion data))))
                              (rum/with-key (file-id-conflict-item repo file data) file)

                              :else
                              [:li.my-1 {:key file}
                               [:a {:on-click #(js/window.apis.openPath file)} file]
                               [:p (.-message error)]])))]
                       [:p "Don't forget to re-index your graph when all the conflicts are resolved."]]
                      :status :error}]))

(defmethod handle :run/cli-command [[_ command content]]
  (when (and command (not (string/blank? content)))
    (shell-handler/run-cli-command-wrapper! command content)))

(defmethod handle :whiteboard/undo [[_ e]]
  (whiteboard-handler/undo! e))

(defmethod handle :whiteboard/redo [[_ e]]
  (whiteboard-handler/redo! e))

(defmethod handle :editor/quick-capture [[_ args]]
  (quick-capture/quick-capture args))

(defmethod handle :modal/keymap [[_]]
  (state/open-settings! :keymap))

(defmethod handle :editor/toggle-own-number-list [[_ blocks]]
  (let [batch? (sequential? blocks)
        blocks (cond->> blocks
                 batch?
                 (map #(cond-> % (or (uuid? %) (string? %)) (db-model/get-block-by-uuid))))]
    (if (and batch? (> (count blocks) 1))
      (editor-handler/toggle-blocks-as-own-order-list! blocks)
      (when-let [block (cond-> blocks batch? (first))]
        (if (editor-handler/own-order-number-list? block)
          (editor-handler/remove-block-own-order-list-type! block)
          (editor-handler/make-block-as-own-order-list! block))))))

(defmethod handle :editor/remove-own-number-list [[_ block]]
  (when (some-> block (editor-handler/own-order-number-list?))
    (editor-handler/remove-block-own-order-list-type! block)))

(defmethod handle :editor/toggle-children-number-list [[_ block]]
  (when-let [blocks (and block (db-model/get-block-immediate-children (state/get-current-repo) (:block/uuid block)))]
    (editor-handler/toggle-blocks-as-own-order-list! blocks)))

(defn run!
  []
  (let [chan (state/get-events-chan)]
    (async/go-loop []
      (let [[payload d] (async/<! chan)]
        (->
         (try
           (p/resolved (handle payload))
           (catch :default error
             (p/rejected error)))
         (p/then (fn [result]
                   (p/resolve! d result)))
         (p/catch (fn [error]
                    (p/reject! d error)))))
      (recur))
    chan))

(comment
  (let [{:keys [deprecated-app-id current-app-id]} {:deprecated-app-id "AFDADF9A-7466-4ED8-B74F-AAAA0D4565B9", :current-app-id "7563518E-0EFD-4AD2-8577-10CFFD6E4596"}]
    (def deprecated-app-id deprecated-app-id)
    (def current-app-id current-app-id))
  (def deprecated-repo (state/get-current-repo))
  (def new-repo (string/replace deprecated-repo deprecated-app-id current-app-id))

  (update-file-path deprecated-repo new-repo deprecated-app-id current-app-id))
