(ns uml-viewer.events
  (:require [uml-viewer.compose :as compose]
            [uml-viewer.detail :as detail]
            [uml-viewer.hit :as hit]
            [uml-viewer.ir :as ir]
            [uml-viewer.layout :as layout]
            [uml-viewer.metrics :as m]
            [uml-viewer.overlay :as overlay]
            [uml-viewer.route :as route]))

(defn compile-diagram [diagram]
  (route/route (layout/layout diagram)))

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

(defn select-class [state id]
  (if (hit/class-by-id (:scene state) id)
    (assoc state :selected {:kind :class :id id} :detail-id id)
    state))

(defn close-detail [state]
  (dissoc state :detail-id))

(defn on-detail-press [state model scroll y]
  (if-let [id (detail/rel-at (detail/rows model) (+ y scroll))]
    (select-class state id)
    state))

(defn world-xy [state x y]
  [(+ x (:cam-x state)) (+ y (:cam-y state))])

(defn on-move [state x y]
  (let [[wx wy] (world-xy state x y)]
    (assoc state :hover (hit/at (:scene state) wx wy))))

(defn on-press [state x y]
  (let [[wx wy] (world-xy state x y)
        hit (hit/at (:scene state) wx wy)]
    (if (= :class (:kind hit))
      (select-class state (:id hit))
      (assoc state :selected hit))))

(defn on-scroll [state amount opts]
  (let [opts (if (map? opts) opts {:window-h opts :window-w 1500})
        horizontal? (:horizontal? opts)
        window-w (or (:window-w opts) 1500)
        window-h (or (:window-h opts) 900)
        amount (cond
                 (number? amount) amount
                 (map? amount) (or (:count amount) 0)
                 :else 0)
        size (get-in state [:scene :size] {:w 800 :h 600})
        view-w (max 0 (- window-w m/sidebar-w))
        max-x (max 0 (- (:w size) view-w))
        max-y (max 0 (- (:h size) window-h))]
    (if horizontal?
      (update state :cam-x #(max 0 (min max-x (+ % (* amount 48)))))
      (update state :cam-y #(max 0 (min max-y (+ % (* amount 48))))))))

(defn on-key
  ([state k] (on-key state k {:window-w 1500 :window-h 900}))
  ([state k dims]
   (case k
     :left (on-scroll state -2 (assoc dims :horizontal? true))
     :right (on-scroll state 2 (assoc dims :horizontal? true))
     :up (on-scroll state -2 dims)
     :down (on-scroll state 2 dims)
     :esc (assoc state :selected nil)
     :r (assoc state :mtime 0)
     state)))
