(ns frontend.extensions.sci
  "Provides a consistent approach to sci evaluation. Used in at least the following places:
- For :view evaluation
- For :result-transform evaluation
- For cljs evaluation in Src blocks
- For evaluating {{function }} under query tables"
  (:require [frontend.util :as util]
            [goog.dom]
            [goog.object]
            [goog.string]
            [sci.core :as sci]))

;; Helper fns for eval-string
;; ==========================
(def ^:private sum (partial apply +))

(defn- average [coll]
  (/ (reduce + coll) (count coll)))

;; Public fns
;; ==========
(defn eval-string
  "Second arg is a map of options for sci/eval-string"
  ([s]
   (eval-string s {}))
  ([s options]
   (try
     (sci/eval-string s (merge-with merge
                                    {:bindings {'sum sum
                                                'average average
                                                'parseFloat js/parseFloat
                                                'isNaN js/isNaN
                                                'log js/console.log
                                                'pprint util/pp-str}}
                                    options))
     (catch :default e
       (println "Query: sci eval failed:")
       (js/console.error e)))))

(defn call-fn
  [f & args]
  (apply f args))

(defn eval-result
  "Evaluate code with sci in a block context"
  [code block]
  [:div
   [:code "Results:"]
   [:div.results.mt-1
    (let [result (eval-string code {:bindings {'block block}})]
      (if (and (vector? result) (:hiccup (meta result)))
        result
        [:pre.code (str result)]))]])
