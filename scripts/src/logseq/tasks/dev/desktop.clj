(ns logseq.tasks.dev.desktop
  "Tasks for desktop (electron) development"
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [logseq.tasks.dev.process :as dev-process]
            [logseq.tasks.util :as task-util]))

(def ^:private watch-state-file
  (fs/path "tmp" "logseq-watch.edn"))

(def ^:private watch-build-files
  ["static/js/main.js"
   "static/electron.js"])

(def ^:private asset-watch-command
  ["yarn" "gulp:watch"])

(def ^:private cljs-watch-command
  ["clojure" "-M:cljs" "watch" "app" "electron" "--config-merge"
   "{:asset-path \"./js\"}"])

(defn- process-alive-by-pid?
  [pid]
  (try
    (let [handle (java.lang.ProcessHandle/of (long pid))]
      (and (.isPresent handle)
           (.isAlive (.get handle))))
    (catch Exception _
      false)))

(defn- state-pids
  [state]
  (keep identity (concat [(:pid state)] (:child-pids state))))

(defn- watch-state-active?
  [state]
  (some process-alive-by-pid? (state-pids state)))

(defn- read-watch-state
  []
  (when (fs/exists? watch-state-file)
    (try
      (edn/read-string (slurp (str watch-state-file)))
      (catch Exception _
        nil))))

(defn- write-watch-state!
  [state]
  (fs/create-dirs (fs/parent watch-state-file))
  (spit (str watch-state-file) (pr-str state)))

(defn- delete-watch-state!
  []
  (when (fs/exists? watch-state-file)
    (fs/delete watch-state-file)))

(defn- ensure-no-running-watch!
  []
  (when (fs/exists? watch-state-file)
    (if-let [state (read-watch-state)]
      (if (watch-state-active? state)
        (throw (ex-info "A desktop watcher is already running"
                        {:state-file (str watch-state-file)
                         :pids (vec (state-pids state))}))
        (delete-watch-state!))
      (delete-watch-state!))))

(defn- file-built-after?
  [file start-time]
  (and (fs/exists? file)
       (try
         (task-util/file-modified-later-than? file start-time)
         (catch Exception _
           false))))

(defn- initial-build-complete?
  [start-time]
  (every? #(file-built-after? % start-time) watch-build-files))

(defn- process-summary
  [processes]
  (mapv (fn [child]
          {:cmd (:cmd child)
           :exit (when-not (process/alive? child)
                   (:exit @child))})
        processes))

(defn- interrupted?
  [child]
  (and (not (process/alive? child))
       (= 130 (:exit @child))))

(defn- wait-for-initial-build!
  [processes start-time]
  (println "Waiting for the initial desktop build...")
  (loop []
    (cond
      (initial-build-complete? start-time)
      nil

      (some interrupted? processes)
      nil

      (some (complement process/alive?) processes)
      (throw (ex-info "A desktop watcher exited before the initial build completed"
                      {:processes (process-summary processes)}))

      :else
      (do
        (Thread/sleep 1000)
        (recur)))))

(defn- wait-for-watchers!
  [processes]
  (loop []
    (cond
      (every? process/alive? processes)
      (do
        (Thread/sleep 1000)
        (recur))

      (some interrupted? processes)
      nil

      :else
      (throw (ex-info "A desktop watcher exited"
                      {:processes (process-summary processes)})))))

(defn- watcher-ready?
  [state]
  (and (= :ready (:status state))
       (seq (:child-pids state))
       (every? process-alive-by-pid? (state-pids state))
       (every? fs/exists? watch-build-files)))

(defn watch
  "Build assets, then watch desktop assets and ClojureScript until stopped."
  []
  (ensure-no-running-watch!)
  (let [started-at (java.time.Instant/now)
        processes (atom [])
        cleaned? (atom false)
        cleanup! (fn []
                   (when (compare-and-set! cleaned? false true)
                     (dev-process/stop! @processes)
                     (delete-watch-state!)))
        shutdown-hook (dev-process/install-shutdown-hook! cleanup!)]
    (try
      (swap! processes conj (dev-process/start! asset-watch-command))
      (swap! processes conj (dev-process/start! cljs-watch-command))
      (write-watch-state!
       {:pid (.pid (java.lang.ProcessHandle/current))
        :child-pids (mapv #(.pid (:proc %)) @processes)
        :started-at (str started-at)
        :status :starting})
      (wait-for-initial-build! @processes started-at)
      (when (every? process/alive? @processes)
        (write-watch-state!
         {:pid (.pid (java.lang.ProcessHandle/current))
          :child-pids (mapv #(.pid (:proc %)) @processes)
          :started-at (str started-at)
          :status :ready})
        (println "Desktop watcher is ready.")
        (wait-for-watchers! @processes))
      (finally
        (cleanup!)
        (dev-process/remove-shutdown-hook! shutdown-hook)))))

(defn- wait-for-watcher-ready!
  []
  (when-not (fs/exists? watch-state-file)
    (throw (ex-info "No desktop watcher is running; start `bb watch` first"
                    {:state-file (str watch-state-file)})))
  (println "Waiting for the desktop watcher to become ready...")
  (loop [attempt 0]
    (let [state (read-watch-state)]
      (cond
        (watcher-ready? state)
        nil

        (and state (not (watch-state-active? state)))
        (throw (ex-info "The desktop watcher is no longer running"
                        {:state-file (str watch-state-file)
                         :pids (vec (state-pids state))}))

        (>= attempt 300)
        (throw (ex-info "The desktop watcher did not finish its initial build"
                        {:state-file (str watch-state-file)}))

        :else
        (do
          (Thread/sleep 1000)
          (recur (inc attempt)))))))

(defn electron-start
  "Open Electron using the watcher started by `bb watch`."
  []
  (wait-for-watcher-ready!)
  (println "Starting Electron...")
  (process/check (dev-process/start! ["yarn" "dev-electron-app"])))
