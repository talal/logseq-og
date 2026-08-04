(ns frontend.handler.theme-test
  (:require [clojure.test :refer [deftest is testing]]
            [logseq.common.theme :as theme]))

(deftest theme-manifest-entries
  (testing "only local CSS entries are accepted"
    (is
     (= [{:id "atlas/custom.css/dark/Atlas"
          :theme-id "atlas"
          :name "Atlas"
          :url "custom.css"
          :mode "dark"}
         {:id "atlas/custom.css/light/Atlas"
          :theme-id "atlas"
          :name "Atlas"
          :url "custom.css"
          :mode "light"}]
        (theme/manifest->themes
         "atlas"
         {:logseq {:themes [{:name "Atlas"
                             :url "./custom.css"
                             :mode "dark"}
                            {:name "Atlas"
                             :url "./custom.css"
                             :mode "light"}
                            {:name "JavaScript"
                             :url "./main.js"}
                            {:name "Remote"
                             :url "https://example.com/theme.css"}
                            {:name "Traversal"
                             :url "../outside.css"}]}})))))

(deftest theme-enumeration-is-deterministic
  (is (= ["alpha/a.css/any/A" "alpha/z.css/any/Z" "beta/theme.css/any/Beta"]
         (mapv :id
               (theme/sort-themes
                (concat
                 (theme/manifest->themes
                  "beta"
                  {:logseq {:themes [{:name "Beta" :url "./theme.css"}]}})
                 (theme/manifest->themes
                  "alpha"
                  {:logseq {:themes [{:name "Z" :url "./z.css"}
                                     {:name "A" :url "./a.css"}]}})))))))

(deftest malformed-theme-folders-are-ignored
  (testing "missing or malformed manifests do not become selectable themes"
    (is (= [] (theme/manifest->themes "missing" {})))
    (is (= [] (theme/manifest->themes "malformed" {:logseq {:themes "not-a-vector"}})))
    (is (= [] (theme/manifest->themes "empty" {:logseq {:themes []}})))
    (is (= []
           (theme/manifest->themes
            "invalid"
            {:logseq {:themes [{:name "No URL"}
                               {:url "./theme.css"}
                               {:name "" :url "./theme.css"}]}})))))

(deftest theme-selection-is-graph-local
  (is (= "atlas/custom.css/any/Atlas"
         (theme/theme-selection-key {:theme-id "atlas"
                                     :url "custom.css"
                                     :name "Atlas"})))
  (is (theme/active-for-mode?
       {:mode nil}
       "light"))
  (is (theme/active-for-mode?
       {:mode "dark"}
       "dark"))
  (is (not (theme/active-for-mode?
            {:mode "dark"}
            "light"))))
