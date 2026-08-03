(ns electron.server-test
  (:require [cljs.test :refer [deftest is]]
            [electron.server :as server]))

(deftest git-api-tags-are-rejected
  (is (nil? (server/resolve-real-api-method "logseq.Git.execCommand"))))
