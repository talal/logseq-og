(ns electron.utils
  (:require
   ["electron" :refer [BrowserWindow]]
   ["fs-extra" :as fs]
   ["path" :as node-path]
   [cljs-bean.core :as bean]
   [clojure.string :as string]
   [electron.configs :as cfgs]
   [electron.logger :as logger]))

(defonce *win (atom nil)) ;; The main window

(defonce mac? (= (.-platform js/process) "darwin"))
(defonce win32? (= (.-platform js/process) "win32"))
(defonce linux? (= (.-platform js/process) "linux"))

(defonce prod? (= js/process.env.NODE_ENV "production"))

;; Under e2e testing?
(defonce ci? (let [v js/process.env.CI]
               (or (true? v)
                   (= v "true"))))

(defonce dev? (not prod?))

(defonce open (js/require "open"))
(defonce _fetch (js/require "node-fetch"))

(defn fetch
  ([url] (_fetch url))
  ([url options]
   (_fetch url (bean/->js options))))

(defn fix-win-path!
  [path]
  (when (not-empty path)
    (if win32?
      (string/replace path "\\" "/")
      path)))

(defn to-native-win-path!
  "Convert path to native win path"
  [path]
  (when (not-empty path)
    (if win32?
      (string/replace path "/" "\\")
      path)))

(defn get-ls-dotdir-root
  []
  (when-not (fs/existsSync cfgs/dot-root)
    (fs/mkdirSync cfgs/dot-root))
  (fix-win-path! cfgs/dot-root))

(defn should-read-content?
  "Skip reading content of file while using file-watcher"
  [path]
  (let [ext (string/lower-case (node-path/extname path))]
    (contains? #{".md" ".markdown" ".org" ".js" ".edn" ".css"} ext)))

(defn read-file
  [path]
  (try
    (when (fs/existsSync path)
      (.toString (fs/readFileSync path)))
    (catch :default e
      (logger/error "Read file:" e))))

(defn get-focused-window
  []
  (.getFocusedWindow BrowserWindow))

(defn get-win-from-sender
  [^js evt]
  (try
    (.fromWebContents BrowserWindow (.-sender evt))
    (catch :default _
      nil)))

(defn send-to-renderer
  "Notice: pass the `window` parameter if you can. Otherwise, the message
  will not be received if there's no focused window.
   Use `send-to-focused-renderer` instead if you want to set a window for fallback"
  ([kind payload]
   (send-to-renderer (get-focused-window) kind payload))
  ([window kind payload]
   (when window
     (.. ^js window -webContents
         (send (name kind) (bean/->js payload))))))

(defn send-to-focused-renderer
  "Try to send to focused window. If no focused window, fallback to the `fallback-win`"
  ([kind payload fallback-win]
   (let [focused-win (get-focused-window)
         win         (if focused-win focused-win fallback-win)]
     (send-to-renderer win kind payload))))

(defn get-graph-dir
  "required by all internal state in the electron section"
  [graph-name]
  (when (string/includes? graph-name "logseq_local_")
    (string/replace-first graph-name "logseq_local_" "")))

(defn get-graph-name
  "reversing `get-graph-dir`"
  [graph-dir]
  (str "logseq_local_" graph-dir))

(defn decode-protected-assets-schema-path
  [schema-path]
  (cond-> schema-path
    (string? schema-path)
    (string/replace "/logseq__colon/" ":/")))

;; Keep update with the normalization in main
(defn normalize
  [s]
  (.normalize s "NFC"))

(defn normalize-lc
  [s]
  (normalize (string/lower-case s)))

(defn safe-decode-uri-component
  [uri]
  (try
    (js/decodeURIComponent uri)
    (catch :default _
      (println "decodeURIComponent failed: " uri)
      uri)))

(defn fs-stat->clj
  [path]
  (let [stat (fs/statSync path)]
    {:size (.-size stat)
     :mtime (.-mtime stat)
     :ctime (.-ctime stat)}))
