(ns frontend.routes
  "Defines routes for use with reitit router"
  (:require [frontend.components.bug-report :as bug-report]
            [frontend.components.file :as file]
            [frontend.components.graph-setup :as graph-setup]
            [frontend.components.home :as home]
            [frontend.components.journal :as journal]
            [frontend.components.page :as page]
            [frontend.components.repo :as repo]
            [frontend.components.settings :as settings]
            [frontend.extensions.zotero :as zotero]
            [logseq.shui.demo :as shui]))

;; http://localhost:3000/#?anchor=fn.1
(def routes
  [["/"
    {:name :home
     :view home/home}]

   ["/graphs"
    {:name :repos
     :view repo/repos}]

   ["/repo/add"
    {:name :repo-add
     :view graph-setup/picker}]

   ["/all-files"
    {:name :all-files
     :view file/files}]

   ["/file/:path"
    {:name :file
     :view file/file}]

   ["/page/:name"
    {:name :page
     :view page/page}]

   ["/page/:name/block/:block-route-name"
    {:name :page-block
     :view page/page}]

   ["/all-pages"
    {:name :all-pages
     :view page/all-pages}]

   ["/graph"
    {:name :graph
     :view page/global-graph}]

   ["/settings"
    {:name :settings
     :view settings/settings}]

   ["/settings/zotero"
    {:name :zotero-setting
     :view zotero/settings}]

   ["/import"
    {:name :import
     :view graph-setup/importer}]
   ["/bug-report"
    {:name :bug-report
     :view bug-report/bug-report}]

   ["/bug-report-tool/:tool"
    {:name :bug-report-tools
     :view bug-report/bug-report-tool-route}]

   ["/all-journals"
    {:name :all-journals
     :view journal/all-journals}]

   ["/ui"
    {:name :ui
     :view shui/page}]])
