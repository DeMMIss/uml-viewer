(ns uml-viewer.engine.hit
  (:require [uml-viewer.domain.geom :as geom]
            [uml-viewer.engine.layout :as layout]))

(defn- port-at [c x y]
  (or (some (fn [p]
              (when (and (:rect p) (geom/inside? (:rect p) x y))
                {:kind :port :id (:id p) :parent (:id c) :dir :in}))
            (:in-ports c))
      (some (fn [p]
              (when (and (:rect p) (geom/inside? (:rect p) x y))
                {:kind :port :id (:id p) :parent (:id c) :dir :out}))
            (:out-ports c))))

(defn deps-of
  "Leaf `from -> to` pairs on an arrow, collapsed or not."
  [e]
  (or (seq (:deps e))
      (when (and (:from e) (:to e))
        [{:from (:from e)
          :to (:to e)
          :violating (boolean (:violating e))}])))

(defn- edge-paths [e]
  (or (seq (:strokes e))
      (when (next (:points e)) [(:points e)])))

(defn edge-at
  "Arrow under world point [x y], or nil. Classes take priority in `at`."
  ([scene x y] (edge-at scene x y 8.0))
  ([scene x y pad]
   (let [p [x y]]
     (some (fn [e]
             (when (some #(geom/near-polyline? p % pad) (edge-paths e))
               {:kind :edge
                :from (:from e)
                :to (:to e)
                :deps (vec (deps-of e))}))
           (reverse (:edges scene))))))

(defn at
  "Topmost port, class, child row, edge, or package under world point [x y]."
  [scene x y]
  (or (some (fn [c]
              (or (port-at c x y)
                  (when (geom/inside? (:rect c) x y)
                    (let [line (layout/line-at c x y)]
                      (if (and line (= :child (:kind line)))
                        {:kind :child
                         :id (:id line)
                         :parent (:id c)
                         :drill? (boolean (:drill? line))}
                        {:kind :class
                         :id (:id c)
                         :drill? (boolean (:drill? c))})))))
            (let [cs (:classes scene)
                  visible (vec (remove :dummy? cs))]
              (reverse (if (seq visible) visible cs))))
      (edge-at scene x y)
      (some (fn [p]
              (when (geom/inside? (:rect p) x y)
                {:kind :package :id (:id p)}))
            (reverse (:packages scene)))))

(defn class-by-id [scene id]
  (first (filter #(= id (:id %)) (:classes scene))))

(defn package-by-id [scene id]
  (first (filter #(= id (:id %)) (:packages scene))))

(defn connected-edges [scene class-id]
  (filter (fn [e]
            (or (= class-id (:from e))
                (= class-id (:to e))))
          (:edges scene)))
