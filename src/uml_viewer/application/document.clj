(ns uml-viewer.application.document
  (:require [clojure.java.io :as io]
            [uml-viewer.engine.compose :as compose]
            [uml-viewer.domain.hierarchy :as hierarchy]
            [uml-viewer.engine.hit :as hit]
            [uml-viewer.domain.ir :as ir]
            [uml-viewer.domain.mailbox :as mailbox]
            [uml-viewer.application.overlay :as overlay]))

(defn compile-view
  "Compile the hierarchical view at `focus`, or the stacked document."
  ([doc] (compile-view doc (System/getProperty "user.dir") []))
  ([doc metrics-root] (compile-view doc metrics-root []))
  ([doc metrics-root focus]
   (let [painted (overlay/apply-metrics doc (overlay/load-metrics metrics-root))]
     (if (:hierarchical painted)
       (compose/compile-diagram (hierarchy/view-at painted (or focus [])))
       (compose/compile-document painted)))))

(defn compile-document
  ([doc] (compile-view doc (System/getProperty "user.dir") []))
  ([doc metrics-root] (compile-view doc metrics-root [])))

(def empty-scene
  {:classes []
   :packages []
   :edges []
   :sections []
   :size {:w 800 :h 600}
   :diagram {:title "UML"}})

(def waiting-message "Waiting for agent to create diagram.")

(defn- mail-seen-at [root]
  (long (or (:id (mailbox/read-command (mailbox/to-viewer root))) 0)))

(defn waiting-state
  "Blank canvas until the companion sends :display. R still loads `path`."
  ([path] (waiting-state path (overlay/metrics-root path)))
  ([path root]
   {:path path
    :mtime 0
    :waiting true
    :scene empty-scene
    :selected nil
    :hover nil
    :detail-id nil
    :cam-x 0
    :cam-y 0
    :mail-seen (mail-seen-at root)}))

(defn- blank-state [path error]
  {:path path
   :mtime (.lastModified (java.io.File. path))
   :scene empty-scene
   :error error
   :selected nil
   :hover nil
   :detail-id nil
   :cam-x 0
   :cam-y 0
   :mail-seen (mail-seen-at (overlay/metrics-root path))})

(defn- paint
  "IR plus .metrics members. The class card reads this, not the scene."
  [doc path]
  (let [root (overlay/metrics-root path)]
    (overlay/apply-metrics doc (overlay/load-metrics root))))

(defn load-path [path]
  (let [file (java.io.File. path)]
    (cond
      (not (.exists file))
      (blank-state path (str "file not found: " path))

      (not (.isFile file))
      (blank-state path (str "not a file: " path))

      :else
      (try
        (let [root (overlay/metrics-root path)
              doc (paint (ir/load-document path) path)]
          {:path path
           :mtime (.lastModified file)
           :doc doc
           :focus []
           :scene (compile-view doc root [])
           :selected nil
           :hover nil
           :detail-id nil
           :cam-x 0
           :cam-y 0
           :mail-seen (mail-seen-at root)})
        (catch Exception e
          (blank-state path (or (.getMessage e) (.getSimpleName (class e)))))))))

(defn- drop-missing-detail [state]
  (let [id (:detail-id state)]
    (cond-> state
      (and id (nil? (hit/class-by-id (:scene state) id)))
      (dissoc :detail-id))))

(defn- resolve-display-path [state p]
  (let [f (io/file p)]
    (if (.isAbsolute f)
      (.getPath f)
      (.getPath (io/file (overlay/metrics-root (:path state)) p)))))

(defn apply-mail
  "Act on one unread command from the companion Grok."
  [state cmd]
  (let [state (assoc state :mail-seen (:id cmd))]
    (case (keyword (:op cmd))
      :display (if-let [p (:path cmd)]
                 (-> (load-path (resolve-display-path state p))
                     (assoc :mail-seen (:id cmd))
                     (dissoc :waiting))
                 state)
      :quit-for-restart (assoc state :quit-for-restart true)
      state)))

(defn poll-mail
  [state]
  (if-not (:path state)
    state
    (let [root (overlay/metrics-root (:path state))
          cmd (mailbox/unread (mailbox/to-viewer root) (:mail-seen state))]
      (if cmd
        (apply-mail state cmd)
        state))))

(defn maybe-reload [state]
  (if (or (:waiting state) (nil? (:path state)))
    state
    (let [file (java.io.File. (:path state))
          mtime (.lastModified file)]
      (if (and (.exists file) (not= mtime (:mtime state)))
        (try
          (let [path (:path state)
                root (overlay/metrics-root path)
                doc (paint (ir/load-document path) path)
                focus (or (:focus state) [])]
            (-> state
                (dissoc :waiting)
                (assoc :mtime mtime
                       :doc doc
                       :scene (compile-view doc root focus)
                       :error nil)
                drop-missing-detail))
          (catch Exception e
            (assoc state :mtime mtime :error (.getMessage e))))
        state))))
