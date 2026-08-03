(ns electron.shell
  (:require
   ["child_process" :as child-process]
   ["command-exists" :as command-exists]
   [clojure.set :as set]
   [clojure.string :as string]
   [electron.logger :as logger]
   [electron.state :as state]))

(def commands-allowlist
  #{"pandoc" "ag" "grep" "alda"})

;(def commands-denylist
;  #{"rm" "mv" "rename" "dd" ">" "command" "sudo"})

(defn- get-commands-allowlist
  []
  (-> (set/union (set (some->> (map #(some-> % str string/trim string/lower-case)
                                    (get-in @state/state [:config :commands-allowlist]))
                               (remove nil?)))
                 commands-allowlist)
      (disj "git")))

(defn- run-command!
  [command args on-data on-exit]
  (logger/debug "Shell: " (str command " " args))
  (let [job (child-process/spawn (str command " " args)
                                 #js []
                                 #js {:shell true :detached false})]

    (.on (.-stderr job) "data" on-data)
    (.on (.-stdout job) "data" on-data)
    (.on job "close" on-exit)

    job))

(defn- ensure-command-exists
  [command]
  (when-not
   (some->> command (.sync command-exists))
    (throw (js/Error. (str "Shell: " command " does not exist!")))) command)

(defn- ensure-command-in-allowlist
  [command]
  (when-not
   (some->> command (contains? (get-commands-allowlist)))
    (throw (js/Error. (str "Shell: " command " is not allowed!")))) command)

(defn run-command-safely!
  [command args on-data on-exit]
  (when (some-> command str string/trim string/lower-case
                (ensure-command-exists)
                (ensure-command-in-allowlist))
    (run-command! command args on-data on-exit)))
