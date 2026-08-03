(ns frontend.state-test
  (:require [clojure.test :refer [deftest is]]
            [electron.ipc :as ipc]
            [frontend.db :as db]
            [frontend.handler :as handler]
            [frontend.handler.events :as events]
            [frontend.handler.file :as file-handler]
            [frontend.handler.page :as page-handler]
            [frontend.handler.repo-config :as repo-config-handler]
            [frontend.handler.ui :as ui-handler]
            [frontend.modules.shortcut.core :as shortcut]
            [frontend.state :as state]
            [frontend.storage :as storage]
            [frontend.test.helper :as test-helper :include-macros true :refer [deftest-async]]
            [promesa.core :as p]))

(deftest current-repo-storage-migrates-from-legacy-key
  (let [old-state      @state/state
        storage-values (atom {:repo/current "/current"
                              :git/current-repo "/legacy"})
        operations     (atom [])]
    (try
      (with-redefs [storage/get    (fn [key] (get @storage-values key))
                    storage/set    (fn [key value]
                                     (swap! operations conj [:set key value])
                                     (swap! storage-values assoc key value))
                    storage/remove (fn [key]
                                     (swap! operations conj [:remove key])
                                     (swap! storage-values dissoc key))
                    ipc/ipc        (fn [& _])]
        (is (= "/current"
               (state/load-current-repo!)))
        (is (= [[:remove :git/current-repo]]
               @operations))
        (is (nil? (:git/current-repo @storage-values)))
        (reset! operations [])
        (reset! storage-values {:git/current-repo "/legacy"})

        (is (= "/legacy" (state/load-current-repo!)))
        (is (= [[:remove :git/current-repo]
                [:set :repo/current "/legacy"]]
               @operations))
        (state/set-current-repo! "/next")
        (is (= "/next" (:repo/current @state/state)))
        (is (= "/next" (:repo/current @storage-values)))
        (is (nil? (:git/current-repo @storage-values))))
      (finally
        (reset! state/state old-state)))))

(deftest merge-configs
  (let [global-config
        {:shortcuts {:ui/toggle-theme "t z"}
         :hidden []
         :ui/enable-tooltip? true
         :preferred-workflow :todo
         :pull-secs 60}
        local-config {:hidden ["foo" "bar"]
                      :ui/enable-tooltip? false
                      :preferred-workflow :now
                      :pull-secs 120}]
    (is (= local-config
           (dissoc (state/merge-configs global-config local-config) :shortcuts))
        "Later config overrides all non-map values")
    (is (= {:start-of-week 6 :shortcuts {:ui/toggle-theme "t z"}}
           (select-keys (state/merge-configs {:start-of-week 6}
                                             global-config
                                             local-config)
                        [:start-of-week :shortcuts]))
        "Earlier configs set default values"))

  (is (= {:shortcuts {:ui/toggle-theme "t z"
                      :ui/toggle-brackets "t b"
                      :editor/up ["ctrl+p" "up"]}}
         (state/merge-configs {:shortcuts {:ui/toggle-theme "t z"}}
                              {:shortcuts {:ui/toggle-brackets "t b"}}
                              {:shortcuts {:editor/up ["ctrl+p" "up"]}}))
      "Map values get merged across configs"))

(deftest publish-graph-ready-publishes-current-repo
  (let [published-events (atom [])]
    (with-redefs [state/get-current-repo (constantly "repo")
                  state/pub-event! (fn [event]
                                     (swap! published-events conj event))]
      (state/publish-graph-ready!))
    (is (= [[:graph/ready "repo"]] @published-events))))

(deftest-async restore-and-setup-publishes-graph-ready-once
  (let [published-events (atom [])
        repo             "repo"]
    (test-helper/with-reset reset
      [db/get-files                           (constantly [:existing-file])
       db/restore!                            (constantly (p/resolved nil))
       file-handler/watch-for-current-graph-dir! (constantly nil)
       page-handler/create-today-journal!     (constantly nil)
       page-handler/init-commands!            (constantly nil)
       repo-config-handler/start              (constantly nil)
       shortcut/refresh!                      (constantly nil)
       state/get-current-repo                 (constantly repo)
       state/pub-event!                       (fn [event]
                                                (swap! published-events conj event))
       ui-handler/add-style-if-exists!        (constantly nil)
       js/setInterval                         (constantly nil)]
      (p/finally
        (p/then
         (handler/restore-and-setup! [{:url repo}])
         (fn []
           (doseq [event @published-events]
             (when (= :graph/restored (first event))
               (events/handle event)))
           (is (= [[:graph/restored repo]]
                  (vec (filter #(= :graph/restored (first %)) @published-events))))
           (is (= [[:graph/ready repo]]
                  (vec (filter #(= :graph/ready (first %)) @published-events))))))
        reset))))
