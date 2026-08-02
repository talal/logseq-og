(ns frontend.fs-test
  (:require ["fs" :as fs-node]
            ["path" :as node-path]
            [clojure.test :refer [deftest is use-fixtures]]
            [frontend.fs :as fs]
            [frontend.test.fixtures :as fixtures]
            [frontend.test.helper :as test-helper :include-macros true :refer [deftest-async]]
            [frontend.test.node-helper :as test-node-helper]
            [frontend.util :as util]
            [logseq.graph-parser.config :as gp-config]
            [promesa.core :as p]))

(def ^:private host-get-fs fs/get-fs)

(use-fixtures :once fixtures/redef-get-fs)

(deftest get-fs-selects-the-host-filesystem
  (with-redefs [util/electron? (constantly false)]
    (is (identical? fs/nfs-backend (host-get-fs nil)))
    (is (identical? fs/nfs-backend (host-get-fs "/graph"))))
  (with-redefs [util/electron? (constantly true)]
    (is (identical? fs/node-backend (host-get-fs nil)))
    (is (identical? fs/node-backend (host-get-fs "/graph"))))
  (is (identical? fs/memory-backend (host-get-fs "memory://graph"))))

(deftest remove-asset-protocol-keeps-supported-url-forms
  (is (= "file://images/example.png"
         (gp-config/remove-asset-protocol "assets://images/example.png")))
  (is (= "file:///tmp/example.png"
         (gp-config/remove-asset-protocol "file:///tmp/example.png")))
  (is (= "https://example.com/example.png"
         (gp-config/remove-asset-protocol "https://example.com/example.png"))))

(deftest-async create-if-not-exists-creates-correctly
  ;; dir needs to be an absolute path for fn to work correctly
  (let [dir (node-path/resolve (test-node-helper/create-tmp-dir))
        some-file (node-path/join dir "something.txt")]

    (->
     (p/do!
      (fs/create-if-not-exists nil dir some-file "NEW")
      (is (fs-node/existsSync some-file)
          "something.txt created correctly")
      (is (= "NEW"
             (str (fs-node/readFileSync some-file)))
          "something.txt has correct content"))

     (p/finally
       (fn []
         (fs-node/unlinkSync some-file)
         (fs-node/rmdirSync dir))))))

(deftest-async create-if-not-exists-does-not-create-correctly
  (let [dir (node-path/resolve (test-node-helper/create-tmp-dir))
        some-file (node-path/join dir "something.txt")]
    (fs-node/writeFileSync some-file "OLD")

    (->
     (p/do!
      (fs/create-if-not-exists nil dir some-file "NEW")
      (is (= "OLD" (str (fs-node/readFileSync some-file)))
          "something.txt has not been touched and old content still exists"))

     (p/finally
       (fn []
         (fs-node/unlinkSync some-file)
         (fs-node/rmdirSync dir))))))
