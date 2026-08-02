(ns logseq.tasks.dev.desktop
  "Tasks for desktop (electron) development"
  (:require [babashka.fs :as fs]
            [babashka.tasks :refer [shell]]
            [logseq.tasks.util :as task-util]))

(defn watch
  "Watches environment to reload cljs, css and other assets"
  []
  (shell "sh" "-c" "yarn gulp:watch & clojure -M:cljs watch app electron --config-merge '{:asset-path \"./js\"}' & wait"))

(defn open-dev-electron-app
  "Opens dev-electron-app when watch process has built main.js"
  []
  (let [start-time (java.time.Instant/now)]
    (dotimes [_n 1000]
      (if (and (fs/exists? "static/js/main.js")
               (task-util/file-modified-later-than? "static/js/main.js" start-time))
        (shell "yarn dev-electron-app")
        (println "Waiting for app to build..."))
      (Thread/sleep 1000))))
