(ns frontend.fs.test-node
  "Test implementation of fs protocol for node.js"
  (:require ["fs/promises" :as fsp]
            [frontend.fs.protocol :as protocol]
            [promesa.core :as p]))

;; Most protocol fns are not defined. Define them as needed for tests
(defrecord NodeTestfs
           []
  protocol/Fs
  (mkdir! [_this _dir] nil)
  (mkdir-recur! [_this _dir] nil)
  (readdir [_this _dir] nil)
  (unlink! [_this _repo _path _opts] nil)
  (rmdir! [_this _dir] nil)
  (read-file [_this _dir path _options]
    (p/let [content (fsp/readFile path)]
      (str content)))
  (write-file! [_this _repo _dir path content _opts]
    (fsp/writeFile path content))
  (rename! [_this _repo _old-path _new-path] nil)
  (stat [_this fpath]
    (fsp/stat fpath))
  (open-dir [_this _dir] nil)
  (get-files [_this _dir] nil)
  (watch-dir! [_this _dir _options] nil)
  (unwatch-dir! [_this _dir] nil))
