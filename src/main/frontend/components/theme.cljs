(ns frontend.components.theme
  (:require [electron.ipc :as ipc]
            [frontend.components.settings :as settings]
            [frontend.config :as config]
            [frontend.context.i18n :refer [t]]
            [frontend.extensions.pdf.core :as pdf]
            [frontend.handler.route :as route-handler]
            [frontend.handler.theme :as theme-handler]
            [frontend.handler.ui :as ui-handler]
            [frontend.rum :refer [use-mounted]]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [frontend.util :as util]
            [rum.core :as rum]))

(rum/defc scrollbar-measure
  []
  (let [*el (rum/use-ref nil)]
    (rum/use-effect!
     (fn []
       (when-let [el (rum/deref *el)]
         (let [w (- (.-offsetWidth el) (.-clientWidth el))
               c "custom-scrollbar"
               l (.-classList js/document.documentElement)]
           (if (or (not util/mac?) (> w 2))
             (.add l c) (.remove l c)))))
     [])
    [:div.fixed.w-16.h-16.overflow-scroll.opacity-0
     {:ref   *el
      :class "top-1/2 -left-1/2 z-[-999]"}]))

(rum/defc container
  [{:keys [route theme accent-color on-click current-repo nfs-granted? db-restoring?
           settings-open? sidebar-open? system-theme? sidebar-blocks-len preferred-language]} child]
  (let [mounted-fn (use-mounted)
        [restored-sidebar? set-restored-sidebar?] (rum/use-state false)]

    (rum/use-effect!
     #(let [^js doc js/document.documentElement
            ^js cls (.-classList doc)
            ^js cls-body (.-classList js/document.body)]
        (.setAttribute doc "data-theme" theme)
        (if (= theme "dark") ;; for tailwind dark mode
          ; The white-theme is for backward compatibility. See: https://github.com/logseq/logseq/pull/4652.
          (do (.add cls "dark") (doto cls-body (.remove "white-theme" "light-theme") (.add "dark-theme")))
          (do (.remove cls "dark") (doto cls-body (.remove "dark-theme") (.add "white-theme" "light-theme"))))
        (theme-handler/apply-selected-theme!))
     [theme])
    (rum/use-effect!
     #(when (and restored-sidebar?
                 (mounted-fn))
        (ui-handler/persist-right-sidebar-state!))
     [sidebar-open? restored-sidebar? sidebar-blocks-len])

    (rum/use-effect!
     (fn []
       (theme-handler/load-themes! current-repo)
       (pdf/reset-current-pdf!))
     [current-repo])

    ;; theme color
    (rum/use-effect!
     #(some-> js/document.documentElement
              (.setAttribute "data-color"
                             (or accent-color "logseq")))
     [accent-color])

    (rum/use-effect!
     #(let [doc js/document.documentElement]
        (.setAttribute doc "lang" preferred-language)))

    (rum/use-effect!
     #(js/setTimeout (fn [] (ipc/ipc "theme-loaded")) 100) ; Wait for the theme to be applied
     [])

    (rum/use-effect!
     #(let [db-restored? (false? db-restoring?)]
        (if db-restoring?
          (util/set-title! (t :loading))
          (when (or nfs-granted? db-restored?)
            (route-handler/update-page-title! route))))
     [nfs-granted? db-restoring? route])

    (rum/use-effect!
     (fn []
       (when-not db-restoring?
         (let [repos (state/get-repos)]
           (if-not (or
                    ;; demo graph only
                    (and (= 1 (count repos)) (:example? (first repos)))
                    ;; not in publishing mode
                    config/publishing?
                    ;; other graphs exists
                    (seq repos))
             (route-handler/redirect! {:to :repo-add})
             (do
               (ui-handler/restore-right-sidebar-state!)
               (set-restored-sidebar? true))))))
     [db-restoring?])

    (rum/use-effect!
     #(when system-theme?
        (ui/setup-system-theme-effect!))
     [system-theme?])

    (rum/use-effect!
     #(state/set-modal!
       (when settings-open?
         (fn [] [:div.settings-modal (settings/settings settings-open?)])))
     [settings-open?])

    [:div.theme-container
     {:on-click on-click}
     child

     (pdf/default-embed-playground)
     (scrollbar-measure)]))
