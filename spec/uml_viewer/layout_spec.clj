(ns uml-viewer.layout-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.geom :as geom]
            [uml-viewer.ir :as ir]
            [uml-viewer.layout :as layout]
            [uml-viewer.metrics :as m]
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
  (it "stacks Othello adapters above the domain"
    (let [scene (layout/layout (ir/load-diagram "examples/othello.edn"))
          adapters (first (filter #(= :adapters (:id %)) (:packages scene)))
          domain (first (filter #(= :domain (:id %)) (:packages scene)))]
      (should (< (:y (:rect adapters)) (:y (:rect domain))))))

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

(defn- seg-len [[ax ay] [bx by]]
  (Math/hypot (- bx ax) (- by ay)))

(describe "routing"
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

  (it "points the last segment into the target, not along its edge"
    (let [scene (route/route (layout/layout (ir/load-diagram "examples/othello.edn")))]
      (doseq [e (:edges scene)]
        (let [to (first (filter #(= (:to e) (:id %)) (:classes scene)))
              pts (vec (:points e))
              a (nth pts (- (count pts) 2))
              b (last pts)
              [ax ay] a [bx by] b
              dx (- bx ax) dy (- by ay)
              r (:rect to)
              on-top (< (abs (- by (:y r))) 0.51)
              on-bot (< (abs (- by (geom/bottom r))) 0.51)
              on-left (< (abs (- bx (:x r))) 0.51)
              on-right (< (abs (- bx (geom/right r))) 0.51)]
          (should (>= (seg-len a b) m/stub-len))
          (should-not (geom/inside? r a))
          (should (geom/inside? (geom/inflate r 1) b))
          (should (or on-top on-bot on-left on-right))
          (cond
            (or on-top on-bot) (should (< (abs dx) 0.51))
            :else (should (< (abs dy) 0.51)))
          (cond
            on-top (should (pos? dy))
            on-bot (should (neg? dy))
            on-left (should (pos? dx))
            on-right (should (neg? dx))))))))
