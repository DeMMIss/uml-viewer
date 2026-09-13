(ns uml-viewer.hit-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.events :as events]
            [uml-viewer.geom :as geom]
            [uml-viewer.hit :as hit]
            [uml-viewer.ir :as ir]))

(defn scene []
  (events/compile-diagram
    (ir/normalize
      {:packages
       [{:id :p :label "P"
         :classes [{:id :a :name "A"} {:id :b :name "B"}]}]
       :edges [{:from :a :to :b :kind :association}]})))

(describe "hit"
  (it "selects a package when the point is in the frame but not a class"
    (let [s (scene)
          p (first (:packages s))
          r (:rect p)
          x (+ (:x r) 4)
          y (+ (:y r) 4)]
      (should= {:kind :package :id :p} (hit/at s x y))))

  (it "finds classes and packages by id"
    (let [s (scene)]
      (should= :a (:id (hit/class-by-id s :a)))
      (should= :p (:id (hit/package-by-id s :p)))
      (should-be-nil (hit/class-by-id s :nope))))

  (it "lists edges touching a class"
    (let [s (scene)
          es (hit/connected-edges s :a)]
      (should= 1 (count es))
      (should= :b (:to (first es)))
      (should= :a (:from (first (hit/connected-edges s :b)))))))
