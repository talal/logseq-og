(ns logseq.tasks.dev.process
  "Process lifecycle helpers for long-running development tasks"
  (:require [babashka.process :as process]))

(defn start!
  [command]
  (process/process {:cmd command
                    :inherit true
                    :shutdown process/destroy-tree}))

(defn stop!
  [processes]
  (doseq [child processes]
    (when (process/alive? child)
      (process/destroy-tree child)))
  (doseq [child processes]
    (loop [attempt 0]
      (when (and (process/alive? child)
                 (< attempt 100))
        (Thread/sleep 50)
        (recur (inc attempt))))
    (when (process/alive? child)
      (.destroyForcibly (:proc child)))))

(defn install-shutdown-hook!
  [cleanup!]
  (let [hook (Thread.
              (reify Runnable
                (run [_]
                  (cleanup!))))]
    (.addShutdownHook (Runtime/getRuntime) hook)
    hook))

(defn remove-shutdown-hook!
  [hook]
  (try
    (.removeShutdownHook (Runtime/getRuntime) hook)
    (catch IllegalStateException _
      nil)))
