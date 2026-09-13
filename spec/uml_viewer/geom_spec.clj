(ns uml-viewer.geom-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.geom :as geom]))

(describe "geom"
  (it "reports containment on the closed rectangle"
    (let [r (geom/rect 10 20 30 40)]
      (should (geom/inside? r 10 20))
      (should (geom/inside? r 40 60))
      (should-not (geom/inside? r 9 20))
      (should-not (geom/inside? r 10 61))))

  (it "unions rectangles"
    (let [u (geom/union [(geom/rect 0 0 10 10) (geom/rect 5 5 10 10)])]
      (should= 0 (:x u))
      (should= 15 (:w u))
      (should= 15 (:h u)))))
