(ns frontend.components.settings-test
  (:require [cljs.test :refer [deftest is]]
            [frontend.components.settings :as settings]
            [frontend.handler.config :as config-handler]
            [frontend.state :as state]))

(deftest preferred-settings-are-written-to-graph-config
  (let [config-writes (atom [])
        state-writes (atom [])]
    (with-redefs [config-handler/set-config! #(swap! config-writes conj [%1 %2])
                  state/set-preferred-format! #(swap! state-writes conj [:format %])
                  state/set-preferred-workflow! #(swap! state-writes conj [:workflow %])]
      (settings/set-preferred-format! :org)
      (settings/set-preferred-workflow! :todo))
    (is (= [[:preferred-format :org]
            [:preferred-workflow :todo]]
           @config-writes))
    (is (= [[:format :org]
            [:workflow :todo]]
           @state-writes))))
