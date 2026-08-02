(ns logseq.graph-parser.util.page-ref-test
  (:require [cljs.test :refer [are deftest]]
            [logseq.graph-parser.util.page-ref :as page-ref]))

(deftest page-ref?
  (are [x y] (= (page-ref/page-ref? x) y)
    "[[page]]" true
    "[[another page]]" true
    "[[some [[nested]] page]]" true
    "[single bracket]" false
    "no brackets" false))
