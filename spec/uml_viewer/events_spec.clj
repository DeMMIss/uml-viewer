(ns uml-viewer.events-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.events :as events]
            [uml-viewer.geom :as geom]
            [uml-viewer.ir :as ir]))

(defn scene []
  (events/compile-diagram
    (ir/normalize
      {:packages
       [{:id :p :label "P"
         :classes [{:id :a :name "A"} {:id :b :name "B"}]}]
       :edges [{:from :a :to :b :kind :association}]})))

(defn state []
  {:scene (scene)
   :selected nil
   :hover nil
   :panning false
   :cam-x 0
   :cam-y 0
   :path "examples/library.edn"
   :mtime 0})

(describe "clicks"
  (it "selects the class under the cursor"
    (let [s (state)
          a (first (filter #(= :a (:id %)) (:classes (:scene s))))
          [x y] [(geom/cx (:rect a)) (geom/cy (:rect a))]
          next (events/on-press s x y)]
      (should= {:kind :class :id :a} (:selected next))))

  (it "deselects when clicking empty space"
    (let [s (assoc (state) :selected {:kind :class :id :a})
          next (events/on-press s 0 0)]
      (should-not (:selected next))
      (should (:panning next))))

  (it "pans the camera when dragging empty space"
    (let [s (-> (state) (events/on-press 10 10) (events/on-drag 40 30))]
      (should= -30 (:cam-x s))
      (should= -20 (:cam-y s))))

  (it "reloads on r by clearing mtime"
    (should= 0 (:mtime (events/on-key (assoc (state) :mtime 99) :r)))))
