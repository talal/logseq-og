 (ns frontend.components.lazy-editor
   (:require-macros [frontend.components.lazy-editor :refer [code-editor-loadable]])
   (:require [clojure.string :as string]
             [frontend.state :as state]
             [frontend.ui :as ui]
             [promesa.core :as p]
             [rum.core :as rum]
             [shadow.lazy :as lazy]))

 ;; The test target has no lazy modules; keep the production module boundary intact.
 ;; See `code-editor-loadable` for the build-aware expansion.
(def lazy-editor (code-editor-loadable))

(defonce loaded? (atom false))

(rum/defc editor <
  rum/reactive
  {:will-mount
   (fn [state]
     (when lazy-editor
       (lazy/load lazy-editor
                  (fn []
                    (if-not @loaded?
                      (p/finally
                        (p/delay 200)
                        #(reset! loaded? true))
                      (reset! loaded? true)))))
     state)}
  [config id attr code options]
  (let [loaded? (rum/react loaded?)
        theme   (state/sub :ui/theme)
        code    (or code "")
        code    (string/replace-first code #"\n$" "")]      ;; See-also: #3410
    (if (and loaded? lazy-editor)
      (@lazy-editor config id attr code theme options)
      (ui/loading "CodeMirror"))))
