(ns frontend.extensions.zotero.setting-test
  (:require [cljs.test :refer [deftest is testing]]
            [frontend.extensions.zotero.setting :as setting]))

(deftest profile-to-remove-falls-back-to-active-profile
  (testing "deleting before changing the selector removes the active profile"
    (is (= "default" (setting/profile-to-remove nil "default")))
    (is (= "secondary" (setting/profile-to-remove "secondary" "default")))))

(deftest type-id-validation
  (testing "existing invalid Zotero IDs remain visible as a warning"
    (is (true? (setting/invalid-type-id? "not-a-number")))
    (is (false? (setting/invalid-type-id? "12345")))
    (is (false? (setting/invalid-type-id? nil)))))
