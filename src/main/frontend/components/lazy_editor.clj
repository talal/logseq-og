(ns frontend.components.lazy-editor
  (:require [cljs.analyzer :as ana]
            [cljs.env :as env]
            [shadow.lazy :as shadow-lazy]))

(defn- cljs-munge
  [symbol]
  ((resolve 'cljs.compiler/munge) symbol))

(defmacro code-editor-loadable
  []
  (if-let [module (try
                    (shadow-lazy/module-for-ns @env/*compiler* 'frontend.extensions.code)
                    (catch Exception _ nil))]
    (let [current-ns (:name (:ns &env))]
      (swap! env/*compiler*
             assoc-in
             [::ana/namespaces current-ns ::ana/ns-refs 'frontend.extensions.code]
             module)
      `(shadow.lazy/Loadable.
        [~module]
        (fn [] ~(list 'js* (str (cljs-munge 'frontend.extensions.code/editor))))))
    nil))
