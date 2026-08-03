(ns frontend.extensions.tldraw.handlers
  (:require [frontend.db.model :as model]
            [frontend.handler.editor :as editor-handler]
            [frontend.state :as state]
            [frontend.util :as util]))

(defn get-selected-blocks
  []
  (clj->js (mapv #(hash-map :uuid (str %)) (state/get-selection-block-ids))))

(defn set-blocks-id
  [block-ids]
  (editor-handler/set-blocks-id! (map parse-uuid block-ids)))

(defn edit-block
  [block-id]
  (let [block-id (parse-uuid block-id)]
    (when-let [block (model/query-block-by-uuid block-id)]
      (editor-handler/edit-block! block :max block-id))))

(defn open-external-link
  [url]
  (util/open-url url))
