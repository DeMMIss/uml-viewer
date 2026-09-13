(ns uml-viewer.route
  (:require [uml-viewer.geom :as geom]))

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

(defn- top-port [r]
  [(geom/cx r) (:y r)])

(defn- bottom-port [r]
  [(geom/cx r) (geom/bottom r)])

(defn- nearest-ports [a b]
  (let [acx (geom/cx a) acy (geom/cy a)
        bcx (geom/cx b) bcy (geom/cy b)
        dx (- bcx acx)
        dy (- bcy acy)]
    (if (> (abs dx) (abs dy))
      (if (pos? dx)
        [[(geom/right a) acy] [(:x b) bcy]]
        [[(:x a) acy] [(geom/right b) bcy]])
      (if (pos? dy)
        [(bottom-port a) (top-port b)]
        [(top-port a) (bottom-port b)]))))

(defn- ports [from-c to-c kind]
  (if (contains? #{:inheritance :implements} kind)
    {:start (top-port (:rect from-c))
     :end (bottom-port (:rect to-c))}
    (let [[s e] (nearest-ports (:rect from-c) (:rect to-c))]
      {:start s :end e})))

(defn- elbow
  [[sx sy] [tx ty] wobble]
  (if (< (abs (- sx tx)) 6)
    [[sx sy] [tx ty]]
    (let [mid (+ (/ (+ sy ty) 2.0) wobble)]
      [[sx sy] [sx mid] [tx mid] [tx ty]])))

(defn- wobble [i]
  (* 8 (- (mod i 5) 2)))

(defn route-edge [scene edge i]
  (let [idx (class-by-id scene)
        from (idx (:from edge))
        to (idx (:to edge))
        {:keys [start end]} (ports from to (:kind edge))]
    (assoc edge
      :points (elbow start end (wobble i))
      :dashed? (dashed? (:kind edge))
      :head (head (:kind edge)))))

(defn route [scene]
  (let [edges (:edges (:diagram scene))]
    (assoc scene
      :edges (vec (map-indexed (fn [i e] (route-edge scene e i)) edges)))))
