(ns uml-viewer.curve-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.curve :as curve]
            [uml-viewer.layout :as layout]))

(describe "class box sizing"
  (it "grows with the longest text line"
    (let [[w1] (layout/class-box-size {:name "A"})
          [w2] (layout/class-box-size {:name "AVeryLongClassName"})]
      (should (> w2 w1))))

  (it "grows taller when members are added"
    (let [[_ h1] (layout/class-box-size {:name "A"})
          [_ h2] (layout/class-box-size {:name "A"
                                         :fields [{:text "x : int"}]
                                         :ops [{:text "go() : void"}]})]
      (should (> h2 h1)))))

(describe "curve"
  (it "handles short polylines"
    (should= [] (:ops (curve/basis-path [])))
    (should= [] (:ops (curve/basis-path [[0 0]])))
    (should= :line (:op (first (:ops (curve/basis-path [[0 0] [1 1]]))))))

  (it "emits cubics for a polyline with elbows"
    (let [path (curve/basis-path [[0 0] [0 48] [80 48] [80 96] [160 96] [160 120]])
          ops (:ops path)]
      (should= [0 0] (:start path))
      (should (some #(= :cubic (:op %)) ops))
      (should= :line (:op (last ops)))))

  (it "reports an end tangent from the last stroke, not a forced stub"
    (let [path (curve/basis-path [[0 0] [0 40] [80 40] [80 80]])
          [behind tip] (curve/end-tangent path)]
      (should= [80.0 80.0] (mapv double tip))
      (should-not= behind tip))))
