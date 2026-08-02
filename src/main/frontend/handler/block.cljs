(ns ^:no-doc frontend.handler.block
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [frontend.db :as db]
   [frontend.db.model :as db-model]
   [frontend.db.react :as react]
   [frontend.state :as state]
   [frontend.util :as util]
   [logseq.graph-parser.block :as gp-block]))

;;  Fns

;; TODO: reduced version
(defn- walk-block
  [block check? transform]
  (let [result (atom nil)]
    (walk/postwalk
     (fn [x]
       (if (check? x)
         (reset! result (transform x))
         x))
     (:block/body block))
    @result))

(defn get-timestamp
  [block typ]
  (walk-block block
              (fn [x]
                (and (gp-block/timestamp-block? x)
                     (= typ (first (second x)))))
              #(second (second %))))

(defn get-scheduled-ast
  [block]
  (get-timestamp block "Scheduled"))

(defn get-deadline-ast
  [block]
  (get-timestamp block "Deadline"))

(defn load-more!
  [db-id start-id]
  (let [repo (state/get-current-repo)
        db (db/get-db repo)
        block (db/entity repo db-id)
        block? (not (:block/name block))
        k (if block?
            :frontend.db.react/block-and-children
            :frontend.db.react/page-blocks)
        query-k [repo k db-id]
        option (cond-> {:limit db-model/step-loading-blocks}
                 block?
                 (assoc :scoped-block-id db-id))
        more-data (->> (db-model/get-paginated-blocks-no-cache db start-id option)
                       (map #(db/pull (:db/id %))))]
    (react/swap-new-result! query-k
                            (fn [result]
                              (->> (concat result more-data)
                                   (util/distinct-by :db/id))))))

(defn select-block!
  [block-uuid]
  (let [blocks (js/document.getElementsByClassName (str block-uuid))]
    (when (seq blocks)
      (state/exit-editing-and-set-selected-blocks! blocks))))

(defn get-blocks-refed-pages
  [aliases [block & children]]
  (let [children-refs (mapcat :block/refs children)
        refs (->>
              (:block/path-refs block)
              (concat children-refs)
              (remove #(aliases (:db/id %))))]
    (keep (fn [ref]
            (when (:block/name ref)
              {:db/id (:db/id ref)
               :block/name (:block/name ref)
               :block/original-name (:block/original-name ref)})) refs)))

(defn filter-blocks
  [ref-blocks filters]
  (if (empty? filters)
    ref-blocks
    (let [exclude-ids (->> (keep (fn [page] (:db/id (db/entity [:block/name (util/page-name-sanity-lc page)]))) (get filters false))
                           (set))
          include-ids (->> (keep (fn [page] (:db/id (db/entity [:block/name (util/page-name-sanity-lc page)]))) (get filters true))
                           (set))]
      (cond->> ref-blocks
        (seq exclude-ids)
        (remove (fn [block]
                  (let [ids (set (map :db/id (:block/path-refs block)))]
                    (seq (set/intersection exclude-ids ids)))))

        (seq include-ids)
        (filter (fn [block]
                  (let [ids (set (map :db/id (:block/path-refs block)))]
                    (set/subset? include-ids ids))))))))

(defn get-filtered-ref-blocks-with-parents
  [all-ref-blocks filtered-ref-blocks]
  (when (seq filtered-ref-blocks)
    (let [id->block (zipmap (map :db/id all-ref-blocks) all-ref-blocks)
          get-parents (fn [block]
                        (loop [block block
                               result [block]]
                          (let [parent (id->block (:db/id (:block/parent block)))]
                            (if (and parent (not= (:db/id parent) (:db/id block)))
                              (recur parent (conj result parent))
                              result))))]
      (distinct (mapcat get-parents filtered-ref-blocks)))))

(defn get-idx-of-order-list-block
  [block order-list-type]
  (let [order-block-fn? #(some-> % :block/properties :logseq.order-list-type (= order-list-type))
        prev-block-fn   #(some->> (:db/id %) (db-model/get-prev-sibling (state/get-current-repo)))
        prev-block      (prev-block-fn block)]
    (letfn [(page-fn? [b] (some-> b :block/name some?))
            (order-sibling-list [b]
              (lazy-seq
               (when (and (not (page-fn? b)) (order-block-fn? b))
                 (cons b (order-sibling-list (prev-block-fn b))))))
            (order-parent-list [b]
              (lazy-seq
               (when (and (not (page-fn? b)) (order-block-fn? b))
                 (cons b (order-parent-list (db-model/get-block-parent (:block/uuid b)))))))]
      (let [idx           (if prev-block
                            (count (order-sibling-list block)) 1)
            order-parents-count (dec (count (order-parent-list block)))
            delta (if (neg? order-parents-count) 0 (mod order-parents-count 3))]
        (cond
          (zero? delta) idx

          (= delta 1)
          (some-> (util/convert-to-letters idx) util/safe-lower-case)

          :else
          (util/convert-to-roman idx))))))

(defn attach-order-list-state
  [config block]
  (let [own-order-list-type  (some-> block :block/properties :logseq.order-list-type str string/lower-case)
        own-order-list-index (some->> own-order-list-type (get-idx-of-order-list-block block))]
    (assoc config :own-order-list-type own-order-list-type
           :own-order-list-index own-order-list-index
           :own-order-number-list? (= own-order-list-type "number"))))
