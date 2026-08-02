(ns frontend.handler.assets-test
  (:require [cljs.test :refer [deftest is testing]]
            [frontend.handler.assets :as assets]))

(deftest alias-enabled-change-detection
  (testing "restart is required only after the persisted toggle changes"
    (is (false? (assets/alias-enabled-changed? false false)))
    (is (true? (assets/alias-enabled-changed? false true)))
    (is (true? (assets/alias-enabled-changed? true false)))))
