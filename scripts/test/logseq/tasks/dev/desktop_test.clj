(ns logseq.tasks.dev.desktop-test
  (:require [babashka.process :as process]
            [clojure.test :refer [deftest is]]
            [logseq.tasks.dev.desktop :as desktop]
            [logseq.tasks.dev.process :as dev-process]))

(deftest stop-processes-destroys-process-tree
  (let [child (dev-process/start! ["sh" "-c" "sleep 30"])]
    (try
      (is (process/alive? child))
      (dev-process/stop! [child])
      (is (not (process/alive? child)))
      (finally
        (when (process/alive? child)
          (process/destroy-tree child))))))

(deftest watch-rejects-a-live-watch-state
  (try
    (#'desktop/write-watch-state!
     {:pid (.pid (java.lang.ProcessHandle/current))
      :child-pids []})
    (let [error (try
                  (#'desktop/ensure-no-running-watch!)
                  nil
                  (catch Exception error
                    error))]
      (is (instance? clojure.lang.ExceptionInfo error)))
    (finally
      (#'desktop/delete-watch-state!))))
