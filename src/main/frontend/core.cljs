(ns frontend.core
  "Entry ns for the browser and Electron frontend apps"
  {:dev/always true}
  (:require [frontend.config :as config]
            [frontend.handler :as handler]
            [frontend.handler.route :as route-handler]
            [frontend.log]
            [frontend.page :as page]
            [frontend.routes :as routes]
            [frontend.spec]
            [malli.dev.cljs :as md]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [rum.core :as rum]))

(defn set-router!
  []
  (rfe/start!
   (rf/router routes/routes nil)
   (fn [route]
     (route-handler/set-route-match! route))

   ;; set to false to enable HistoryAPI
   {:use-fragment true}))

(defn display-welcome-message
  []
  (js/console.log
   "
    Welcome to Logseq!
    If you encounter any problem, feel free to file an issue on GitHub (https://github.com/talal/logseq-og)
    or join our forum (https://discuss.logseq.com).
    .____
    |    |    ____   ____  ______ ____  ______
    |    |   /  _ \\ / ___\\/  ___// __ \\/ ____/
    |    |__(  <_> ) /_/  >___ \\\\  ___< <_|  |
    |_______ \\____/\\___  /____  >\\___  >__   |
            \\/    /_____/     \\/     \\/   |__|
     "))

(defn start []
  (when config/dev?
    (md/start!))
  (when-let [node (.getElementById js/document "root")]
    (set-router!)
    (rum/mount (page/current-page) node)
    (display-welcome-message)
    ;; NO repo state here, better not add init logic here
    ))
(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds

  (handler/start! start))

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (handler/stop!)
  (js/console.log "stop"))
