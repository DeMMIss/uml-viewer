(ns uml-viewer.layout-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.geom :as geom]
            [uml-viewer.ir :as ir]
            [uml-viewer.layout :as layout]
            [uml-viewer.route :as route]))

(def sample
  (ir/normalize
    {:packages
     [{:id :top
       :label "Top"
       :classes [{:id :parent :name "Parent" :stereotype :interface}]}
      {:id :bot
       :label "Bottom"
       :classes [{:id :child :name "Child"}]}]
     :edges [{:from :child :to :parent :kind :implements}]}))

(describe "layout"
  (it "lays Domain out left-to-right with Game as the hub"
    (let [scene (layout/layout (ir/load-diagram "examples/othello.edn"))
          game (first (filter #(= :game (:id %)) (:classes scene)))
          targets (filter #(#{:board :rules :color :square :move} (:id %))
                          (:classes scene))]
      (should= 5 (count targets))
      (should (every? #(< (geom/cx (:rect game)) (geom/cx (:rect %))) targets))))

  (it "puts the parent package above the implementing child"
    (let [scene (layout/layout sample)
          top (first (filter #(= :top (:id %)) (:packages scene)))
          bot (first (filter #(= :bot (:id %)) (:packages scene)))]
      (should (< (:y (:rect top)) (:y (:rect bot))))))

  (it "keeps class boxes inside their package"
    (let [scene (layout/layout (ir/load-diagram "examples/library.edn"))]
      (doseq [c (:classes scene)]
        (let [p (first (filter #(= (:package c) (:id %)) (:packages scene)))
              r (:rect c)
              pr (:rect p)]
          (should (geom/inside? pr (:x r) (:y r)))
          (should (geom/inside? pr (geom/right r) (geom/bottom r)))))))

  (it "does not overlap class boxes"
    (let [scene (layout/layout (ir/load-diagram "examples/library.edn"))
          boxes (:classes scene)]
      (doseq [a boxes
              b boxes
              :when (not= (:id a) (:id b))]
        (let [ar (:rect a) br (:rect b)]
          (should-not
            (and (< (:x ar) (geom/right br))
                 (< (:x br) (geom/right ar))
                 (< (:y ar) (geom/bottom br))
                 (< (:y br) (geom/bottom ar)))))))))

(describe "routing"
  (it "marks each edge kind with the matching head"
    (let [d (ir/normalize
              {:packages
               [{:id :p :label "P"
                 :classes [{:id :a :name "A"} {:id :b :name "B"}]}]
               :edges [{:from :a :to :b :kind :association}
                       {:from :a :to :b :kind :dependency}
                       {:from :a :to :b :kind :aggregation}
                       {:from :a :to :b :kind :composition}
                       {:from :a :to :b :kind :inheritance}]})
          heads (map :head (:edges (route/route (layout/layout d))))]
      (should= [:open :open :diamond :diamond-fill :triangle] heads)))

  (it "starts and ends on the class boxes"
    (let [scene (route/route (layout/layout sample))
          e (first (:edges scene))
          child (first (filter #(= :child (:id %)) (:classes scene)))
          parent (first (filter #(= :parent (:id %)) (:classes scene)))
          start (first (:points e))
          end (last (:points e))]
      (should (geom/inside? (geom/inflate (:rect child) 1) start))
      (should (geom/inside? (geom/inflate (:rect parent) 1) end))
      (should= :triangle (:head e))
      (should (:dashed? e))))

  (it "gives Game's five arrows distinct channel waypoints"
    (let [scene (route/route (layout/layout (ir/load-diagram "examples/othello.edn")))
          outs (filter #(= :game (:from %)) (:edges scene))
          mids (map (fn [e] (mapv #(Math/round (double %)) (second (:points e))))
                    outs)]
      (should= 5 (count outs))
      (should= 5 (count (distinct mids)))))

  (it "does not run segments through other classes"
    (let [scene (route/route (layout/layout (ir/load-diagram "examples/othello.edn")))]
      (doseq [e (:edges scene)
              [a b] (partition 2 1 (:points e))
              c (:classes scene)
              :when (and (not= (:id c) (:from e))
                         (not= (:id c) (:to e)))]
        (should-not (geom/segment-hits-rect? a b (:rect c))))))

  (it "aims the last segment at the class center, not along a face"
    (let [scene (route/route (layout/layout (ir/load-diagram "examples/othello.edn")))
          e (first (filter #(= :game (:from %)) (:edges scene)))
          to (first (filter #(= (:to e) (:id %)) (:classes scene)))
          a (last (butlast (:points e)))
          b (last (:points e))
          cx (geom/cx (:rect to))
          cy (geom/cy (:rect to))
          ;; last chord and center-to-end should be nearly collinear
          dot (let [dx1 (- (first b) (first a))
                    dy1 (- (second b) (second a))
                    dx2 (- cx (first b))
                    dy2 (- cy (second b))
                    n1 (Math/hypot dx1 dy1)
                    n2 (Math/hypot dx2 dy2)]
                (/ (+ (* dx1 dx2) (* dy1 dy2)) (* n1 n2)))]
      (should (> dot 0.85)))))
