(ns logseq.graph-parser.config
  "App config that is shared between graph-parser and rest of app"
  (:require [clojure.string :as string]))

(def app-name
  "Copy of frontend.config/app-name. Too small to couple to main app"
  "logseq")

(defonce asset-protocol "assets://")
(defonce local-assets-dir "assets")

(defn local-asset?
  [s]
  (and (string? s)
       (re-find (re-pattern (str "^[./]*" local-assets-dir)) s)))

(defn local-protocol-asset?
  [s]
  (when (string? s)
    (string/starts-with? s asset-protocol)))

(defn remove-asset-protocol
  [s]
  (if (local-protocol-asset? s)
    (string/replace-first s asset-protocol "file://")
    s))

;; TODO: rename
(defonce mldoc-support-formats
  #{:org :markdown :md})

(defn mldoc-support?
  [format]
  (contains? mldoc-support-formats (keyword format)))

(defn text-formats
  []
  #{:json :org :md :yml :dat :asciidoc :rst :txt :markdown :adoc :html :js :ts :edn :clj :ml :rb :ex :erl :java :php :c :css :sh})

(defn img-formats
  []
  #{:gif :svg :jpeg :ico :png :jpg :bmp :webp})

(defn get-date-formatter
  [config]
  (or
   (:journal/page-title-format config)
   ;; for compatibility
   (:date-formatter config)
   "MMM do, yyyy"))

(defn get-block-pattern
  [format]
  (let [format' (keyword format)]
    (case format'
      :org
      "*"

      "-")))
