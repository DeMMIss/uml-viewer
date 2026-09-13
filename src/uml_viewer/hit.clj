(ns uml-viewer.hit
  (:require [uml-viewer.geom :as geom]))

(defn at
  "Topmost class or package under world point [x y]."
  [scene x y]
  (or (some (fn [c]
              (when (geom/inside? (:rect c) x y)
                {:kind :class :id (:id c)}))
            (reverse (:classes scene)))
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
