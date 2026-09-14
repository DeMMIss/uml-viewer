(ns uml-viewer.layout-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.curve :as curve]
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

(def hub
  (ir/normalize
    {:direction :lr
     :packages [{:id :p :label "P"
                 :classes [{:id :hub :name "Hub"}
                           {:id :a :name "A"}
                           {:id :b :name "B"}
                           {:id :c :name "C"}
                           {:id :d :name "D"}
                           {:id :e :name "E"}]}]
     :edges [{:from :hub :to :a :kind :association}
             {:from :hub :to :b :kind :association}
             {:from :hub :to :c :kind :association}
             {:from :hub :to :d :kind :association}
             {:from :hub :to :e :kind :association}]}))

(describe "layout"
  (it "omits private ops from the class box"
    (let [d (ir/normalize
              {:packages
               [{:id :p :label "P"
                 :classes [{:id :a :name "A"
                            :ops [{:name "show"}
                                  {:name "hide" :private true}]}]}]
               :edges []})
          lines (layout/class-lines (get-in d [:packages 0 :classes 0]))
          texts (keep :text lines)]
      (should (some #{"show"} texts))
      (should-not (some #{"hide"} texts))))

  (it "lays an LR hub to the left of its targets"
    (let [scene (layout/layout hub)
          h (first (filter #(= :hub (:id %)) (:classes scene)))
          targets (filter #(#{:a :b :c :d :e} (:id %)) (:classes scene))]
      (should= 5 (count targets))
      (should (every? #(< (geom/cx (:rect h)) (geom/cx (:rect %))) targets))))

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
      (should= :triangle (:head e))))

  (it "gives a hub's five arrows distinct channel waypoints"
    (let [scene (route/route (layout/layout hub))
          outs (filter #(= :hub (:from %)) (:edges scene))
          mids (map (fn [e] (mapv #(Math/round (double %)) (second (:points e))))
                    outs)]
      (should= 5 (count outs))
      (should= 5 (count (distinct mids)))))

  (it "leaves a downward cross-package edge from the source bottom"
    (let [d (ir/normalize
              {:packages
               [{:id :up :label "Up"
                 :classes [{:id :root :name "Root"}
                           {:id :src :name "Src"}]}
                {:id :down :label "Down"
                 :classes [{:id :dst :name "Dst"}]}]
               :edges [{:from :root :to :src :kind :association}
                       {:from :src :to :dst :kind :dependency}]})
          scene (route/route (layout/layout d))
          src (first (filter #(= :src (:id %)) (:classes scene)))
          dst (first (filter #(= :dst (:id %)) (:classes scene)))
          e (first (filter #(and (= :src (:from %)) (= :dst (:to %)))
                           (:edges scene)))
          p0 (first (:points e))
          p1 (second (:points e))]
      (should (< (geom/cy (:rect src)) (geom/cy (:rect dst))))
      (should (<= (abs (- (second p0) (geom/bottom (:rect src)))) 1.51))
      (should (>= (- (second p1) (second p0)) -0.51))))

  (it "does not reverse at the start of a long same-rank detour"
    (let [d (ir/normalize
              {:direction :lr
               :packages [{:id :p :label "P"
                           :classes [{:id :hub :name "Hub"}
                                     {:id :a :name "Above"}
                                     {:id :mid :name "Middle"}
                                     {:id :b :name "Below"}]}]
               :edges [{:from :hub :to :a :kind :association}
                       {:from :hub :to :mid :kind :association}
                       {:from :hub :to :b :kind :association}
                       {:from :a :to :b :kind :association}]})
          scene (route/route (layout/layout d))
          e (first (filter #(and (= :a (:from %)) (= :b (:to %)))
                           (:edges scene)))
          a (first (filter #(= :a (:id %)) (:classes scene)))
          b (first (filter #(= :b (:id %)) (:classes scene)))
          p0 (first (:points e))
          p1 (second (:points e))
          down? (> (geom/cy (:rect b)) (geom/cy (:rect a)))]
      (if down?
        (should (>= (- (second p1) (second p0)) -0.51))
        (should (<= (- (second p1) (second p0)) 0.51)))))

  (it "does not start or end two arrows at the same point on a class"
    (let [scene (route/route (layout/layout hub))
          round (fn [p] (mapv #(Math/round (double %)) p))
          starts (map (fn [e] [(:from e) (round (first (:points e)))])
                      (:edges scene))
          ends (map (fn [e] [(:to e) (round (last (:points e)))])
                    (:edges scene))]
      (doseq [group (vals (group-by first starts))]
        (should= (count group) (count (distinct (map second group)))))
      (doseq [group (vals (group-by first ends))]
        (should= (count group) (count (distinct (map second group)))))))

  (it "does not run segments through other classes"
    (let [scene (route/route (layout/layout hub))]
      (doseq [e (:edges scene)
              [a b] (partition 2 1 (:points e))
              c (:classes scene)
              :when (and (not= (:id c) (:from e))
                         (not= (:id c) (:to e)))]
        (should-not (geom/segment-hits-rect? a b (:rect c))))))

  (it "routes a stacked same-rank pair through the gap, not around the column"
    (let [d (ir/normalize
              {:direction :lr
               :packages [{:id :p :label "P"
                           :classes [{:id :hub :name "Hub"}
                                     {:id :a :name "Above"}
                                     {:id :b :name "Below"}]}]
               :edges [{:from :hub :to :a :kind :association}
                       {:from :hub :to :b :kind :association}
                       {:from :a :to :b :kind :association}]})
          scene (route/route (layout/layout d))
          e (first (filter #(= #{:a :b} #{(:from %) (:to %)}) (:edges scene)))
          a (first (filter #(= :a (:id %)) (:classes scene)))
          b (first (filter #(= :b (:id %)) (:classes scene)))
          xs (map first (:points e))
          pair-left (min (:x (:rect a)) (:x (:rect b)))
          pair-right (max (geom/right (:rect a)) (geom/right (:rect b)))]
      (should (every? #(<= pair-left % pair-right) xs))))

  (it "detours a blocked same-rank edge around the pair instead of the whole rank"
    (let [d (ir/normalize
              {:direction :lr
               :packages [{:id :p :label "P"
                           :classes [{:id :hub :name "Hub"}
                                     {:id :a :name "Above"}
                                     {:id :mid :name "Middle"}
                                     {:id :b :name "Below"}]}]
               :edges [{:from :hub :to :a :kind :association}
                       {:from :hub :to :mid :kind :association}
                       {:from :hub :to :b :kind :association}
                       {:from :a :to :b :kind :association}]})
          scene (route/route (layout/layout d))
          e (first (filter #(= #{:a :b} #{(:from %) (:to %)}) (:edges scene)))
          a (first (filter #(= :a (:id %)) (:classes scene)))
          b (first (filter #(= :b (:id %)) (:classes scene)))
          mid (first (filter #(= :mid (:id %)) (:classes scene)))
          pack (first (:packages scene))
          xs (map first (:points e))
          pair-right (max (geom/right (:rect a)) (geom/right (:rect b)))]
      (doseq [[p q] (partition 2 1 (:points e))]
        (should-not (geom/segment-hits-rect? p q (:rect mid))))
      (should (< (apply max xs) (+ pair-right (* 4 10))))
      (should (< (apply max xs) (geom/right (:rect pack))))))

  (it "does not dash any edge"
    (let [scene (route/route (layout/layout hub))]
      (should (every? #(nil? (:dashed? %)) (:edges scene)))))

  (it "ends the basis stroke on the last waypoint so the head follows the spline"
    (let [path (curve/basis-path [[0 0] [40 0] [40 80] [120 80]])
          last-op (last (:ops path))
          [behind tip] (curve/end-tangent path)]
      (should= :cubic (:op last-op))
      (should= [120.0 80.0] (:p last-op))
      (should= [120.0 80.0] (mapv double tip))
      (should (> (abs (- (second tip) (second behind))) 1.0)))))
