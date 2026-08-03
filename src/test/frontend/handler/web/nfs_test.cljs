(ns frontend.handler.web.nfs-test
  (:require [cljs.test :refer [deftest is]]
            [frontend.handler.web.nfs :as nfs]))

(deftest filter-ignored-files-excludes-hidden-and-internal-files
  (let [files [{:file/path "pages/page.md"}
               {:file/path ".hidden/config"}
               {:file/path "logseq/bak/page.md"}
               {:file/path "pages/.hidden.md"}]]
    (is (= ["pages/page.md"]
           (mapv :file/path (nfs/filter-ignored-files files "/graph" true))))))
