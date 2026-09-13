(ns uml-viewer.events
  (:require [uml-viewer.hit :as hit]
            [uml-viewer.ir :as ir]
            [uml-viewer.layout :as layout]
            [uml-viewer.route :as route]))

(defn compile-diagram [diagram]
  (route/route (layout/layout diagram)))

(defn load-path [path]
  (let [file (java.io.File. path)]
    {:path path
     :mtime (.lastModified file)
     :scene (compile-diagram (ir/load-diagram path))
     :selected nil
     :hover nil
     :panning false
     :cam-x 0
     :cam-y 0}))

(defn maybe-reload [state]
  (let [file (java.io.File. (:path state))
        mtime (.lastModified file)]
    (if (and (.exists file) (not= mtime (:mtime state)))
      (try
        (assoc state
          :mtime mtime
          :scene (compile-diagram (ir/load-diagram (:path state)))
          :error nil)
        (catch Exception e
          (assoc state :mtime mtime :error (.getMessage e))))
      state)))

(defn world-xy [state x y]
  [(+ x (:cam-x state)) (+ y (:cam-y state))])

(defn on-move [state x y]
  (let [[wx wy] (world-xy state x y)]
    (assoc state :hover (hit/at (:scene state) wx wy))))

(defn on-press [state x y]
  (let [[wx wy] (world-xy state x y)
        hit (hit/at (:scene state) wx wy)]
    (if hit
      (assoc state :selected hit :panning false)
      (assoc state
        :selected nil
        :panning true
        :pan-anchor [x y]
        :cam-anchor [(:cam-x state) (:cam-y state)]))))

(defn on-drag [state x y]
  (if (:panning state)
    (let [[ax ay] (:pan-anchor state)
          [cx cy] (:cam-anchor state)]
      (assoc state
        :cam-x (- cx (- x ax))
        :cam-y (- cy (- y ay))))
    state))

(defn on-release [state]
  (assoc state :panning false))

(defn on-key [state k]
  (case k
    :esc (assoc state :selected nil)
    :r (assoc state :mtime 0)
    state))
