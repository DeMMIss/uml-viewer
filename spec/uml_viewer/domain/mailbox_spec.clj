(ns uml-viewer.domain.mailbox-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [uml-viewer.application.document :as document]
            [uml-viewer.domain.mailbox :as mailbox]))

(defn- tmp-root []
  (doto (io/file "target" (str "mailbox-" (System/nanoTime)))
    (.mkdirs)))

(describe "mailbox"
  (it "writes commands atomically with rising ids"
    (let [root (tmp-root)
          f (mailbox/to-agent root)
          a (mailbox/write-command! f :regen {})
          b (mailbox/write-command! f :regen {})]
      (should= 1 (:id a))
      (should= 2 (:id b))
      (should= :regen (:op (mailbox/read-command f)))
      (should-not (.exists (io/file (str (.getPath f) ".tmp"))))))

  (it "returns unread commands only once past seen-id"
    (let [root (tmp-root)
          f (mailbox/to-viewer root)]
      (mailbox/write-command! f :display {:path "a.edn"})
      (should= :display (:op (mailbox/unread f 0)))
      (should-be-nil (mailbox/unread f 1))))

  (it "switches the viewer path on :display"
    (let [s {:path "examples/library.edn" :mail-seen 0 :waiting true}
          next (document/apply-mail s {:id 3 :op :display :path "examples/library.edn"})]
      (should= 3 (:mail-seen next))
      (should (.endsWith (:path next) "examples/library.edn"))
      (should (seq (get-in next [:scene :classes])))
      (should-not (:waiting next))))

  (it "ignores unknown ops after recording the id"
    (let [s {:path "examples/library.edn" :mail-seen 0 :mtime 99}
          next (document/apply-mail s {:id 4 :op :nope})]
      (should= 4 (:mail-seen next))
      (should= "examples/library.edn" (:path next))
      (should= 99 (:mtime next))))

  (it "marks the viewer to quit for restart"
    (let [s {:path "examples/library.edn" :mail-seen 0 :mtime 99}
          next (document/apply-mail s {:id 5 :op :quit-for-restart})]
      (should (:quit-for-restart next))
      (should= 5 (:mail-seen next))
      (should= "examples/library.edn" (:path next))
      (should= 99 (:mtime next)))))
