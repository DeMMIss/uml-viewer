(ns uml-viewer.domain.mailbox
  "File mailbox between the viewer and the companion Grok.
  Payload is durable EDN; tmux is only a wake-up."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.nio.file Files StandardCopyOption]))

(def dir-name ".uml-viewer")
(def to-viewer-name "to-viewer.edn")
(def to-agent-name "to-agent.edn")

(defn dir [root]
  (io/file root dir-name))

(defn to-viewer [root]
  (io/file (dir root) to-viewer-name))

(defn to-agent [root]
  (io/file (dir root) to-agent-name))

(defn read-command
  [file]
  (when (and file (.isFile (io/file file)))
    (try
      (edn/read-string (slurp file))
      (catch Exception _ nil))))

(defn- atomic-write!
  [file m]
  (let [file (io/file file)
        tmp (io/file (str (.getPath file) ".tmp"))]
    (io/make-parents file)
    (spit tmp (pr-str m))
    (try
      (Files/move (.toPath tmp)
                  (.toPath file)
                  (into-array StandardCopyOption
                              [StandardCopyOption/REPLACE_EXISTING
                               StandardCopyOption/ATOMIC_MOVE]))
      (catch Exception _
        (Files/move (.toPath tmp)
                    (.toPath file)
                    (into-array StandardCopyOption
                                [StandardCopyOption/REPLACE_EXISTING]))))))

(defn write-command!
  "Write `op` (and extra keys) with an id greater than any previous command."
  [file op extra]
  (let [prev (read-command file)
        id (inc (long (or (:id prev) 0)))
        cmd (merge {:id id :op (keyword op)} extra)]
    (atomic-write! file cmd)
    cmd))

(defn unread
  "Return cmd when its :id is newer than `seen-id`."
  [file seen-id]
  (let [cmd (read-command file)
        id (:id cmd)]
    (when (and (number? id) (> (long id) (long (or seen-id 0))))
      cmd)))
