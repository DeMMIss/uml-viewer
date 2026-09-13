(ns uml-viewer.route
  (:require [uml-viewer.geom :as geom]
            [uml-viewer.metrics :as m]))

(defn- class-by-id [scene]
  (into {} (map (juxt :id identity) (:classes scene))))

(defn- dashed? [kind]
  (contains? #{:implements :dependency} kind))

(defn- head [kind]
  (case kind
    :inheritance :triangle
    :implements :triangle
    :composition :diamond-fill
    :aggregation :diamond
    :association :open
    :dependency :open
    :open))

(defn- face-out [face]
  (case face
    :top [0.0 -1.0]
    :bottom [0.0 1.0]
    :left [-1.0 0.0]
    :right [1.0 0.0]))

(defn- port-on [r face]
  (case face
    :top [(geom/cx r) (:y r)]
    :bottom [(geom/cx r) (geom/bottom r)]
    :left [(:x r) (geom/cy r)]
    :right [(geom/right r) (geom/cy r)]))

(defn- plus [[x y] [dx dy] s]
  [(+ x (* dx s)) (+ y (* dy s))])

(defn choose-faces [from-r to-r]
  (let [dx (- (geom/cx to-r) (geom/cx from-r))
        dy (- (geom/cy to-r) (geom/cy from-r))]
    (if (> (abs dx) (abs dy))
      (if (pos? dx)
        {:from-face :right :to-face :left}
        {:from-face :left :to-face :right})
      (if (pos? dy)
        {:from-face :bottom :to-face :top}
        {:from-face :top :to-face :bottom}))))

(defn- collinear? [[x1 y1] [x2 y2] [x3 y3]]
  (or (and (< (abs (- x1 x2)) 0.51) (< (abs (- x2 x3)) 0.51))
      (and (< (abs (- y1 y2)) 0.51) (< (abs (- y2 y3)) 0.51))))

(defn- collapse [pts]
  (reduce (fn [acc p]
            (cond
              (empty? acc) [p]
              (= p (last acc)) acc
              (and (>= (count acc) 2)
                   (collinear? (last (butlast acc)) (last acc) p))
              (let [without (pop acc)]
                (if (= p (last without))
                  without
                  (conj without p)))
              :else (conj acc p)))
          []
          pts))

(defn- force-stub [pts end e0]
  "Keep a final segment of stub-len into the class, even after collapse."
  (let [pts (vec pts)
        n (count pts)]
    (if (< n 2)
      [e0 end]
      (let [prev (nth pts (- n 2))
            last-pt (nth pts (dec n))
            [px py] prev
            [ex ey] end
            len (Math/hypot (- ex px) (- ey py))]
        (if (>= len m/stub-len)
          pts
          (conj (subvec pts 0 (dec n)) e0 end))))))

(defn route-points [from-r to-r kind wobble]
  (let [{:keys [from-face to-face]} (choose-faces from-r to-r)
        start (port-on from-r from-face)
        end (port-on to-r to-face)
        s0 (plus start (face-out from-face) m/stub-len)
        e0 (plus end (face-out to-face) m/stub-len)
        [ax ay] s0
        [bx by] e0
        hy (+ (/ (+ ay by) 2.0) wobble)
        raw [start s0 [ax hy] [bx hy] e0 end]]
    (force-stub (collapse raw) end e0)))

(defn- wobble [i]
  (* 8 (- (mod i 5) 2)))

(defn route-edge [scene edge i]
  (let [idx (class-by-id scene)
        from (idx (:from edge))
        to (idx (:to edge))]
    (assoc edge
      :points (route-points (:rect from) (:rect to) (:kind edge) (wobble i))
      :dashed? (dashed? (:kind edge))
      :head (head (:kind edge))
      :to-face (:to-face (choose-faces (:rect from) (:rect to))))))

(defn route [scene]
  (let [edges (:edges (:diagram scene))]
    (assoc scene
      :edges (vec (map-indexed (fn [i e] (route-edge scene e i)) edges)))))
