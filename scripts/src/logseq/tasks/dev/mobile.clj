(ns logseq.tasks.dev.mobile
  "Tasks for mobile development"
  (:require [babashka.fs :as fs]
            [babashka.tasks :refer [shell]]
            [clojure.string :as string]
            [logseq.tasks.util :as task-util]))

(defn- local-server-url
  "Returns the local development server URL used by Capacitor"
  []
  (let [ip (string/trim (:out (shell {:out :string}
                                     "node"
                                     "-e"
                                     "console.log(require('ip').address())")))]
    (format "http://%s:3001" ip)))

(defn- open-dev-app
  "Opens mobile app when watch process has built main.js"
  [platform]
  (let [start-time (java.time.Instant/now)
        server-url (local-server-url)
        shell-opts {:extra-env {"LOGSEQ_APP_SERVER_URL" server-url}}]
    (loop [n 1000]
      (if (and (fs/exists? "static/js/main.js")
               (task-util/file-modified-later-than? "static/js/main.js" start-time))
        (shell shell-opts "npx" "cap" "sync" platform)
        (println "Waiting for app to build..."))
      (Thread/sleep 1000)
      (when-not (or (and (fs/exists? "ios/App/App/public/static/js/main.js")
                         (task-util/file-modified-later-than? "ios/App/App/public/static/js/main.js" start-time))
                    (and (fs/exists? "android/App/src/main/assets/public/static/js/main.js")
                         (task-util/file-modified-later-than? "android/App/src/main/assets/public/static/js/main.js" start-time)))
        (recur (dec n))))))

(defn app-watch
  "Watches environment to reload cljs, css and other assets for mobile"
  []
  (shell "yarn clean")
  (shell "sh" "-c" "yarn gulp:watch & clojure -M:cljs watch app & wait"))

(defn npx-cap-run-ios
  "Copy assets files to iOS build directory, and run app in Xcode"
  []
  (open-dev-app "ios")
  (shell {:extra-env {"LOGSEQ_APP_SERVER_URL" (local-server-url)}}
         "npx" "cap" "open" "ios"))

(defn npx-cap-run-android
  "Copy assets files to Android build directory, and run app in Android Studio"
  []
  (open-dev-app "android")
  (shell {:extra-env {"LOGSEQ_APP_SERVER_URL" (local-server-url)}}
         "npx" "cap" "open" "android"))

(defn- run-mobile-release
  "Copies the built web app into the Capacitor project and runs a release build"
  [platform]
  (shell "rm -rf ./public/static")
  (shell "rm -f ./static/js/*.map")
  (shell "mv static ./public")
  (shell "npx" "cap" "sync" platform)
  (shell "npx" "cap" "run" platform))

(defn run-ios-release
  "Build iOS app release"
  []
  (run-mobile-release "ios"))

(defn run-android-release
  "Build Android app release"
  []
  (run-mobile-release "android"))
