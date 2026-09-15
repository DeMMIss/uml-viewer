(ns uml-viewer.document
  (:require [uml-viewer.compose :as compose]
            [uml-viewer.hit :as hit]
            [uml-viewer.ir :as ir]
            [uml-viewer.overlay :as overlay]))

(defn compile-document [doc]
  (compose/compile-document (overlay/apply-metrics doc (overlay/load-metrics))))

(def empty-scene
  {:classes []
   :packages []
   :edges []
   :sections []
   :size {:w 800 :h 600}
   :diagram {:title "UML"}})

(defn- blank-state [path error]
  {:path path
   :mtime (.lastModified (java.io.File. path))
   :scene empty-scene
   :error error
   :selected nil
   :hover nil
   :detail-id nil
   :cam-x 0
   :cam-y 0})

(defn load-path [path]
  (let [file (java.io.File. path)]
    (cond
      (not (.exists file))
      (blank-state path (str "file not found: " path))

      (not (.isFile file))
      (blank-state path (str "not a file: " path))

      :else
      (try
        {:path path
         :mtime (.lastModified file)
         :scene (compile-document (ir/load-document path))
         :selected nil
         :hover nil
         :detail-id nil
         :cam-x 0
         :cam-y 0}
        (catch Exception e
          (blank-state path (or (.getMessage e) (.getSimpleName (class e)))))))))

(defn- drop-missing-detail [state]
  (let [id (:detail-id state)]
    (cond-> state
      (and id (nil? (hit/class-by-id (:scene state) id)))
      (dissoc :detail-id))))

(defn maybe-reload [state]
  (let [file (java.io.File. (:path state))
        mtime (.lastModified file)]
    (if (and (.exists file) (not= mtime (:mtime state)))
      (try
        (-> state
            (assoc :mtime mtime
                   :scene (compile-document (ir/load-document (:path state)))
                   :error nil)
            drop-missing-detail)
        (catch Exception e
          (assoc state :mtime mtime :error (.getMessage e))))
      state)))
