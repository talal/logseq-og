(ns frontend.handler.web.nfs-test
  (:require [cljs.test :refer [deftest is]]
            [frontend.handler.web.nfs :as nfs]))

(deftest filter-ignored-files-excludes-metadata
  (let [files [{:file/path "pages/page.md"}
               {:file/path ".git/config"}
               {:file/path ".gitignore"}
               {:file/path "pages/.hidden.md"}]]
    (is (= ["pages/page.md"]
           (mapv :file/path (nfs/filter-ignored-files files "/graph" true))))))
