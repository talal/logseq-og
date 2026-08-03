(ns electron.shell-test
  (:require [cljs.test :refer [deftest is]]
            [electron.shell :as shell]
            [electron.state :as state]))

(deftest git-command-is-never-allowlisted
  (with-redefs [state/state (atom {:config {:commands-allowlist ["git" "PANDOC"]}})]
    (let [allowlist (#'shell/get-commands-allowlist)]
      (is (not (contains? allowlist "git")))
      (is (contains? allowlist "pandoc")))))
