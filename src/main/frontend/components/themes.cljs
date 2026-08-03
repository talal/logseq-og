(ns frontend.components.themes
  (:require [clojure.string :as string]
            [frontend.context.i18n :refer [t]]
            [frontend.handler.theme :as theme-handler]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [frontend.util :as util]
            [rum.core :as rum]))

(rum/defc installed-themes < rum/reactive
  [close-fn]
  (let [mode     (state/sub :ui/theme)
        selected (state/sub :theme/selected)
        themes   (->> (state/sub :theme/installed)
                      (filter #(or (nil? (:mode %)) (= mode (:mode %))))
                      (sort-by (juxt :name :url)))]
    [:div.cp__themes-installed
     [:h1.mb-4.text-2xl.p-1 (t :themes)]
     (let [default-selected? (nil? selected)]
       [:div.it.flex.px-3.py-1.5.rounded-sm.justify-between
        {:class (util/classnames [{:is-selected default-selected?}])
         :on-click (fn []
                     (theme-handler/select-theme! nil)
                     (close-fn))}
        [:div.flex.items-center.text-xs
         [:div.opacity-60 "Logseq •"]
         [:div.name.ml-1 (str "Default " (string/capitalize mode) " theme")]
         (when default-selected?
           [:small.inline-flex.ml-1.opacity-60 (ui/icon "check")])]])
     (for [{:keys [id name description theme-id] :as theme} themes]
       (let [selected? (= selected id)]
         [:div.it.flex.px-3.py-1.5.rounded-sm.justify-between
          {:key id
           :title description
           :class (util/classnames [{:is-selected selected?}])
           :on-click (fn []
                       (theme-handler/select-theme! theme)
                       (close-fn))}
          [:div.flex.items-center.text-xs
           [:div.opacity-60 (str theme-id " •")]
           [:div.name.ml-1 name]
           (when selected?
             [:small.inline-flex.ml-1.opacity-60 (ui/icon "check")])]]))]))

(defn open-select-theme!
  []
  (state/set-sub-modal! installed-themes))
