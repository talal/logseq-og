(ns frontend.core-test
  (:require [frontend.db.conn :as conn]
            [frontend.state :as state]))

(defn get-current-conn
  []
  (->
   (state/get-current-repo)
   (conn/get-db false)))
