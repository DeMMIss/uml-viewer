(ns uml-viewer.curve)

(defn- cubic [x0 y0 x1 y1 x y]
  {:op :cubic
   :c1 [(/ (+ (* 2 x0) x1) 3.0) (/ (+ (* 2 y0) y1) 3.0)]
   :c2 [(/ (+ x0 (* 2 x1)) 3.0) (/ (+ y0 (* 2 y1)) 3.0)]
   :p  [(/ (+ x0 (* 4 x1) x) 6.0) (/ (+ y0 (* 4 y1) y) 6.0)]})

(defn basis-path
  "D3 curveBasis through `pts`. Returns {:start [x y] :ops [...]}."
  [pts]
  (let [pts (vec pts)
        n (count pts)]
    (cond
      (zero? n) {:start [0 0] :ops []}
      (= n 1) {:start (first pts) :ops []}
      :else
      (loop [i 0
             point 0
             x0 0.0 y0 0.0
             x1 0.0 y1 0.0
             start (first pts)
             ops []]
        (if (< i n)
          (let [x (double (first (nth pts i)))
                y (double (second (nth pts i)))]
            (case (int point)
              0 (recur (inc i) 1 x y x1 y1 (nth pts i) ops)
              1 (recur (inc i) 2 x0 y0 x y start ops)
              2 (let [mx (/ (+ (* 5 x0) x1) 6.0)
                      my (/ (+ (* 5 y0) y1) 6.0)
                      ops (conj ops {:op :line :p [mx my]} (cubic x0 y0 x1 y1 x y))]
                  (recur (inc i) 3 x1 y1 x y start ops))
              (let [ops (conj ops (cubic x0 y0 x1 y1 x y))]
                (recur (inc i) point x1 y1 x y start ops))))
          (let [ops (if (>= point 3)
                      (conj ops
                            (cubic x0 y0 x1 y1 x1 y1)
                            {:op :line :p [x1 y1]})
                      (if (= point 2)
                        (conj ops {:op :line :p [x1 y1]})
                        ops))]
            {:start start :ops ops}))))))

(defn end-tangent
  "Point just behind the path end, along the last stroke (curve or line)."
  [{:keys [start ops]}]
  (loop [cur start ops ops from start]
    (if (empty? ops)
      [from cur]
      (let [op (first ops)
            nxt (:p op)
            from (if (= :cubic (:op op)) (:c2 op) cur)]
        (recur nxt (rest ops) from)))))
