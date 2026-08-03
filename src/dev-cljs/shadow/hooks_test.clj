(ns shadow.hooks-test
  (:require [clojure.test :refer [deftest is]]
            [shadow.hooks :as hooks]))

(deftest git-revision-hook-injects-revision
  (with-redefs [hooks/exec (fn [& _] "v1.2.3")]
    (let [state (hooks/git-revision-hook
                 {:shadow.build/config {:closure-defines {'frontend.config/TEST true}}
                  :compiler-options {:closure-defines {'frontend.config/TEST true}}}
                 "--long")]
      (is (= "v1.2.3"
             (get-in state [:shadow.build/config :closure-defines
                            'frontend.config/REVISION])))
      (is (= "v1.2.3"
             (get-in state [:compiler-options :closure-defines
                            'frontend.config/REVISION]))))))
