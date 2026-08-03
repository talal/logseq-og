(ns frontend.handler.theme
  (:require [cljs-bean.core :as bean]
            [electron.ipc :as ipc]
            [frontend.handler.config :as config-handler]
            [frontend.handler.ui :as ui-handler]
            [frontend.state :as state]
            [frontend.util :as util]
            [logseq.common.theme :as common-theme]
            [promesa.core :as p]))

(def theme-style-id "logseq-local-theme-id")

(defn- remove-theme-style!
  []
  (when-let [element (.getElementById js/document theme-style-id)]
    (.remove element)))

(defn- current-selection
  [repo]
  (let [selection (get (state/get-graph-config repo) :ui/custom-theme)]
    (when (and (string? selection) (not (empty? selection)))
      selection)))

(defn- selected-theme
  []
  (let [selection (state/sub :theme/selected)]
    (some #(when (= selection (:id %)) %) (state/sub :theme/installed))))

(defn apply-selected-theme!
  []
  (remove-theme-style!)
  (when-let [theme (selected-theme)]
    (when (common-theme/active-for-mode? theme (state/sub :ui/theme))
      (util/add-style! theme-style-id (:href theme)))))

(defn select-theme!
  [theme]
  (let [theme (some-> theme bean/->clj)
        repo  (state/get-current-repo)]
    (if theme
      (do
        (state/set-state! :theme/selected (:id theme))
        (config-handler/set-config! :ui/custom-theme (:id theme))
        (when-let [mode (:mode theme)]
          (state/use-theme-mode! mode))
        (apply-selected-theme!))
      (do
        (state/set-state! :theme/selected nil)
        (config-handler/set-config! :ui/custom-theme "")
        (remove-theme-style!)))
    repo))

(defn load-themes!
  ([] (load-themes! (state/get-current-repo)))
  ([repo]
   (ui-handler/reset-custom-css!)
   (remove-theme-style!)
   (state/set-state! :theme/installed [])
   (state/set-state! :theme/selected nil)
   (if (and repo (util/electron?))
     (p/then (ipc/ipc :getGraphThemes)
             (fn [themes]
               (when (= repo (state/get-current-repo))
                 (let [themes (->> (bean/->clj themes)
                                   common-theme/sort-themes
                                   vec)
                       selection (current-selection repo)
                       selected (some #(when (= selection (:id %)) %) themes)]
                   (state/set-state! :theme/installed themes)
                   (state/set-state! :theme/selected (:id selected))
                   (apply-selected-theme!)))))
     (p/resolved nil))))

(defn refresh!
  []
  (load-themes!))
