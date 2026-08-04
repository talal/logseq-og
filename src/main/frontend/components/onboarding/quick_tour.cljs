(ns frontend.components.onboarding.quick-tour
  (:require [cljs-bean.core :as bean]
            [dommy.core :as d]
            [frontend.context.i18n :refer [t]]
            [frontend.date :as date]
            [frontend.handler.command-palette :as command-palette]
            [frontend.handler.route :as route-handler]
            [frontend.state :as state]
            [frontend.util :as util]
            [hiccups.runtime :as h]
            [promesa.core :as p]))

(defn- load-base-assets$
  []
  (util/js-load$ (str util/JS_ROOT "/shepherd.min.js")))

(defn- make-skip-fns
  [^js jsTour]
  (let [^js el (js/document.createElement "button")]
    (.add (.-classList el) "cp__onboarding-skip-quick-tour")
    (set! (.-innerHTML el) (h/render-html [:span [:i.ti.ti-player-skip-forward] (t :on-boarding/quick-tour-btn-skip)]))
    (.addEventListener el "click" #(.cancel jsTour))
    [#(.appendChild js/document.body el)
     #(.removeChild js/document.body el)]))

(defn- wait-target
  [fn-or-selector time]
  (p/let [action (if (string? fn-or-selector)
                   #(d/sel1 fn-or-selector)
                   fn-or-selector)
          _ (action)
          _ (p/delay time)]))

(defn- inject-steps-indicator
  [current total]

  (h/render-html
   [:div.steps
    [:strong (str (t :on-boarding/quick-tour-steps) current)]
    [:ul (for [i (range total)] [:li {:class (when (= current (inc i)) "active")} i])]]))

(defn- create-steps! [^js jsTour]
  [   ;; step 1
   {:id                "nav-help"
    :text              (h/render-html [:section [:h2 (t :on-boarding/quick-tour-help-title)]
                                       [:p (t :on-boarding/quick-tour-help-desc)]])
    :attachTo          {:element ".cp__sidebar-help-btn" :on "top"}
    :beforeShowPromise #(if (state/sub :ui/sidebar-open?)
                          (wait-target state/hide-right-sidebar! 700)
                          (p/resolved true))
    :canClickTarget    true
    :buttons           [{:text (t :on-boarding/quick-tour-btn-next) :action (.-next jsTour)}]
    :popperOptions     {:modifiers [{:name    "preventOverflow"
                                     :options {:padding 20}}
                                    {:name    "offset"
                                     :options {:offset [0, 10]}}]}}

   ;; step 2
   {:id                "nav-journal-page"
    :text              (h/render-html [:section [:h2 (t :on-boarding/quick-tour-journal-page-title)]
                                       [:p
                                        [:span (t :on-boarding/quick-tour-journal-page-desc-1)]
                                        [:a (t :on-boarding/quick-tour-journal-page-desc-2)]
                                        [:span (t :on-boarding/quick-tour-journal-page-desc-3)]]])

    :attachTo          {:element ".page.is-journals .page-title" :on "top-end"}
    :beforeShowPromise #(if-not (= (util/safe-lower-case (state/get-current-page))
                                   (util/safe-lower-case (date/today)))
                          (wait-target (fn []
                                         (route-handler/redirect-to-page! (date/today))
                                         (util/scroll-to-top)) 200)
                          (p/resolved true))
    :buttons           [{:text (t :on-boarding/quick-tour-btn-back) :classes "back" :action (.-back jsTour)}
                        {:text (t :on-boarding/quick-tour-btn-next) :action (.-next jsTour)}]
    :popperOptions     {:modifiers [{:name    "preventOverflow"
                                     :options {:padding 63}}
                                    {:name    "offset"
                                     :options {:offset [10, 10]}}]}}

   ;; step 3
   {:id                "nav-left-sidebar"
    :text              (h/render-html [:section [:h2 (t :on-boarding/quick-tour-left-sidebar-title)]
                                       [:p [:span (t :on-boarding/quick-tour-left-sidebar-desc)]]])

    :attachTo          {:element "#left-menu" :on "top"}
    :beforeShowPromise #(p/resolved true)
    :buttons           [{:text (t :on-boarding/quick-tour-btn-back) :classes "back" :action (.-back jsTour)}
                        {:text (t :on-boarding/quick-tour-btn-next) :action (.-next jsTour)}]
    :popperOptions     {:modifiers [{:name    "preventOverflow"
                                     :options {:padding 20}}
                                    {:name    "offset"
                                     :options {:offset [10, 10]}}]}}

   ;; step 4
   {:id                "nav-favorites"
    :text              (h/render-html [:section [:h2 (t :on-boarding/quick-tour-favorites-title)]
                                       [:p (t :on-boarding/quick-tour-favorites-desc-1)]
                                       [:p (t :on-boarding/quick-tour-favorites-desc-2)]])
    :beforeShowPromise #(if-not (state/sub :ui/left-sidebar-open?)
                          (wait-target state/toggle-left-sidebar! 500)
                          (p/resolved true))
    :attachTo          {:element ".nav-content-item.favorites" :on "right"}
    :buttons           [{:text (t :on-boarding/quick-tour-btn-back) :classes "back" :action (.-back jsTour)}
                        {:text (t :on-boarding/quick-tour-btn-finish) :action (.-complete jsTour)}]}])

(defn start
  []
  (let [^js jsTour (js/Shepherd.Tour.
                    (bean/->js
                     {:useModalOverlay    true
                      :defaultStepOptions {:classes  "cp__onboarding-quick-tour"
                                           :scrollTo false}}))
        steps      (create-steps! jsTour)
        steps      (map-indexed #(assoc %2 :text (str (:text %2) (inject-steps-indicator (inc %1) (count steps)))) steps)
        [show-skip! hide-skip!] (make-skip-fns jsTour)]

    (doto jsTour
      (.on "show" show-skip!)
      (.on "hide" hide-skip!)
      (.on "complete" hide-skip!)
      (.on "cancel" hide-skip!))

    (doseq [step steps]
      (.addStep jsTour (bean/->js step)))

    (.start jsTour)))

(defn ready
  [callback]
  (p/then
   (if (nil? js/window.Shepherd)
     (load-base-assets$) (p/resolved true))
   callback))

(def should-guide? false)

(defn init []
  (command-palette/register {:id     :document/quick-tour
                             :desc   (t :on-boarding/command-palette-quick-tour)
                             :action #(ready start)})

  ;; TODO: fix logic
  (when should-guide?
    (ready start)))
