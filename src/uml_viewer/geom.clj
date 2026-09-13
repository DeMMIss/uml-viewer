(ns uml-viewer.geom)

(defn rect [x y w h]
  {:x x :y y :w w :h h})

(defn right [r]
  (+ (:x r) (:w r)))

(defn bottom [r]
  (+ (:y r) (:h r)))

(defn cx [r]
  (+ (:x r) (/ (:w r) 2.0)))

(defn cy [r]
  (+ (:y r) (/ (:h r) 2.0)))

(defn inside?
  ([r p] (inside? r (first p) (second p)))
  ([r x y]
   (and (<= (:x r) x (right r))
        (<= (:y r) y (bottom r)))))

(defn intersect-rect
  "Where the ray from the rectangle center through outside hits the border."
  [r outside]
  (let [x (cx r)
        y (cy r)
        [px py] outside
        dx (- px x)
        dy (- py y)
        w (/ (:w r) 2.0)
        h (/ (:h r) 2.0)]
    (if (> (* (abs dy) w) (* (abs dx) h))
      (let [h (if (neg? dy) (- h) h)
            sx (if (zero? dy) 0.0 (/ (* h dx) dy))]
        [(+ x sx) (+ y h)])
      (let [w (if (neg? dx) (- w) w)
            sy (if (zero? dx) 0.0 (/ (* w dy) dx))]
        [(+ x w) (+ y sy)]))))

(defn segment-hits-rect?
  "True if an axis-aligned segment passes through the interior of r."
  [a b r]
  (let [[x1 y1] a
        [x2 y2] b
        xmin (min x1 x2)
        xmax (max x1 x2)
        ymin (min y1 y2)
        ymax (max y1 y2)]
    (and (< xmin (right r))
         (> xmax (:x r))
         (< ymin (bottom r))
         (> ymax (:y r)))))

(defn inflate [r pad]
  (rect (- (:x r) pad)
        (- (:y r) pad)
        (+ (:w r) (* 2 pad))
        (+ (:h r) (* 2 pad))))

(defn union [rects]
  (when (seq rects)
    (let [xs (map :x rects)
          ys (map :y rects)]
      (rect (apply min xs)
            (apply min ys)
            (- (apply max (map right rects)) (apply min xs))
            (- (apply max (map bottom rects)) (apply min ys))))))

;; clj-mutate-manifest-begin
;; {:version 2, :hash-algorithm :sha256-source-v1, :verified? true, :tested-at "2026-09-13T12:14:28.001708-05:00", :module-hash "da94df555e7670286f9213b3001c7bf533d4b1262abecbddb64bba64da5f5187", :provenance {:mutation-rules-version "3", :test-command "clj -M:spec --tag ~no-mutate", :test-roots ["spec"], :test-profile-fingerprint "01f72bad2b4b9bac72353eccfb667d7ea8820b5b0699dd36256c4d99b23f89ae"}, :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "b43051f09f1fd06f68a98fc81cb03217916c22c1e86a0e4fcd5ea0e249c0917a"} {:id "defn/rect", :kind "defn", :line 3, :end-line 4, :hash "ec7a35fd34d2262a23c55fa2ca5f3e12fd37413ac47ee72d1fb22a885d71b249"} {:id "defn/right", :kind "defn", :line 6, :end-line 7, :hash "9a18e384b9aa604b79cd4f4dd65fe50f89ca46c191aaf2fbe294e81d85fb7335"} {:id "defn/bottom", :kind "defn", :line 9, :end-line 10, :hash "8f88456897ed83bdbcaa4b2303b6a8e32cd4c5ff93fa009605964b333d788407"} {:id "defn/cx", :kind "defn", :line 12, :end-line 13, :hash "6048cee9daefe31f1dfdaee608d4745c91e3c2851d14ad5866ce0b4fa97703e2"} {:id "defn/cy", :kind "defn", :line 15, :end-line 16, :hash "de347b3f9abb3c8a6bd15024d89ba2477d1b71434031ac0bd82864bfeda7059c"} {:id "defn/inside?", :kind "defn", :line 18, :end-line 22, :hash "bca8c9186c074a960f3f460769728ceeb090db28f8f64d3b43f089b904e1d01f"} {:id "defn/intersect-rect", :kind "defn", :line 24, :end-line 40, :hash "0fa98a39f6fe4720d956e874796e3653eba1d3d8a9f64d666a468db3938b097d"} {:id "defn/segment-hits-rect?", :kind "defn", :line 42, :end-line 54, :hash "57fe46be83f19c36769b4164618dd1eccdd43af08fb88fa13c92b0c08e66d38c"} {:id "defn/inflate", :kind "defn", :line 56, :end-line 60, :hash "4616a1a35a0506ddd7bbe1697c71f56e6fc8a84406e325515fd84fcdc44d03f5"} {:id "defn/union", :kind "defn", :line 62, :end-line 69, :hash "5c3d774d590db9b55c1181f3591e7d25f4e1242e82b89e9c287f7f32a0ca5046"}]}
;; clj-mutate-manifest-end
