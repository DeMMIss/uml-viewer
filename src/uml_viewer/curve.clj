(ns uml-viewer.curve
  (:require [uml-viewer.geom :as geom]))

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
          ;; D3 curveBasis would add a degenerate cubic along the last chord
          ;; and a lineTo the last point. That forces the end tangent onto the
          ;; last polyline segment (orthogonal when the router used a face
          ;; port). Mermaid's marker is orient=auto on the spline, so keep the
          ;; last real cubic's handles and only pin its end to the last point.
          (let [ops (cond
                      (>= point 3)
                      (let [last-pt [(double (first (last pts)))
                                     (double (second (last pts)))]]
                        (if (seq ops)
                          (conj (pop ops) (assoc (last ops) :p last-pt))
                          ops))
                      (= point 2)
                      (conj ops {:op :line :p [x1 y1]})
                      :else ops)]
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

(defn face-of
  "Nearest side of `r` to point `p`."
  [r [x y]]
  (let [dl (abs (- x (:x r)))
        dr (abs (- x (geom/right r)))
        dt (abs (- y (:y r)))
        db (abs (- y (geom/bottom r)))
        m (min dl dr dt db)]
    (cond
      (= m dl) :left
      (= m dr) :right
      (= m dt) :top
      :else :bottom)))

(defn- clamp-to-face-cone
  "Rotate `(dx,dy)` so its angle with the class edge is at least 45°.
   `arriving?` true: path is heading into the box; false: leaving it."
  [dx dy face arriving?]
  (let [out (case face
              :left [-1.0 0.0]
              :right [1.0 0.0]
              :top [0.0 -1.0]
              :bottom [0.0 1.0])
        nx (if arriving? (- (first out)) (first out))
        ny (if arriving? (- (second out)) (second out))
        len (Math/hypot dx dy)]
    (if (< len 1.0e-6)
      [(* 1.0 nx) (* 1.0 ny)]
      (let [ux (/ dx len)
            uy (/ dy len)
            along (+ (* ux nx) (* uy ny))
            px (- ux (* along nx))
            py (- uy (* along ny))
            plen (Math/hypot px py)]
        (if (and (pos? along) (<= plen along))
          [dx dy]
          (let [sx (if (< plen 1.0e-9) (- ny) (/ px plen))
                sy (if (< plen 1.0e-9) nx (/ py plen))
                s (/ 1.0 (Math/sqrt 2.0))
                ux' (+ (* s nx) (* s sx))
                uy' (+ (* s ny) (* s sy))]
            [(* len ux') (* len uy')]))))))

(defn- constrain-start [path r]
  (let [ops (vec (:ops path))]
    (if (or (nil? r) (empty? ops) (= 1 (count ops)))
      path
      (let [face (face-of r (:start path))
            op (first ops)
            s (:start path)]
        (if (= :line (:op op))
          (let [p (:p op)
                [dx' dy'] (clamp-to-face-cone (- (first p) (first s))
                                              (- (second p) (second s))
                                              face
                                              false)]
            (assoc path :ops (assoc ops 0 (assoc op :p [(+ (first s) dx')
                                                       (+ (second s) dy')]))))
          (if (= :cubic (:op op))
            (let [c1 (:c1 op)
                  [dx' dy'] (clamp-to-face-cone (- (first c1) (first s))
                                                (- (second c1) (second s))
                                                face
                                                false)]
              (assoc path :ops (assoc ops 0 (assoc op :c1 [(+ (first s) dx')
                                                          (+ (second s) dy')]))))
            path))))))

(defn- constrain-end [path r]
  (let [ops (vec (:ops path))]
    (if (or (nil? r) (empty? ops))
      path
      (let [op (last ops)
            face (face-of r (:p op))]
        (if (= :cubic (:op op))
          (let [p (:p op)
                c2 (:c2 op)
                [dx' dy'] (clamp-to-face-cone (- (first p) (first c2))
                                              (- (second p) (second c2))
                                              face
                                              true)
                c2' [(- (first p) dx') (- (second p) dy')]]
            (assoc path :ops (conj (pop ops) (assoc op :c2 c2'))))
          path)))))

(defn constrain-ends
  "Keep the stroke and its end tangent at least 45° to the class edges."
  [path start-r end-r]
  (-> path
      (constrain-start start-r)
      (constrain-end end-r)))

(defn- cubic-at [[x0 y0] [x1 y1] [x2 y2] [x3 y3] t]
  (let [u (- 1.0 t)
        a (* u u u)
        b (* 3.0 u u t)
        c (* 3.0 u t t)
        d (* t t t)]
    [(+ (* a x0) (* b x1) (* c x2) (* d x3))
     (+ (* a y0) (* b y1) (* c y2) (* d y3))]))

(defn flatten-path
  "Sample `path` into a polyline. `step` is the cubic parameter increment."
  ([path] (flatten-path path 0.0625))
  ([{:keys [start ops]} step]
   (loop [cur start ops ops out [start]]
     (if (empty? ops)
       out
       (let [op (first ops)
             nxt (:p op)]
         (if (= :cubic (:op op))
           (recur nxt (rest ops)
                  (into out (map #(cubic-at cur (:c1 op) (:c2 op) nxt %)
                                 (rest (range 0.0 1.0000001 step)))))
           (recur nxt (rest ops) (conj out nxt))))))))
