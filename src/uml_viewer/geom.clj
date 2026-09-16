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

(defn overlaps?
  "True if axis-aligned rects a and b intersect."
  [a b]
  (and (< (:x a) (right b))
       (> (right a) (:x b))
       (< (:y a) (bottom b))
       (> (bottom a) (:y b))))

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

(defn- lerp [[x1 y1] [x2 y2] t]
  [(+ x1 (* t (- x2 x1)))
   (+ y1 (* t (- y2 y1)))])

(defn- clip-param
  "Clip parametric interval [u1 u2] against one Liang-Barsky edge (p, q).
   Nil if the interval is empty."
  [[u1 u2] p q]
  (if (< (abs p) 1.0e-12)
    (when-not (neg? q) [u1 u2])
    (let [t (/ q p)]
      (if (neg? p)
        (when-not (> t u2) [(max u1 t) u2])
        (when-not (< t u1) [u1 (min u2 t)])))))

(defn- overlap-param
  "Param interval [u1 u2] where segment a->b is inside r, or nil."
  [a b r]
  (let [[x1 y1] a
        [x2 y2] b
        dx (double (- x2 x1))
        dy (double (- y2 y1))
        xmin (double (:x r))
        xmax (double (right r))
        ymin (double (:y r))
        ymax (double (bottom r))]
    (reduce (fn [uv [p q]]
              (when uv (clip-param uv p q)))
            [0.0 1.0]
            [[(- dx) (- (double x1) xmin)]
             [dx (- xmax (double x1))]
             [(- dy) (- (double y1) ymin)]
             [dy (- ymax (double y1))]])))

(defn- merge-intervals [ivs]
  (reduce (fn [acc [a b]]
            (if (empty? acc)
              [[a b]]
              (let [[c d] (peek acc)]
                (if (<= a (+ d 1.0e-9))
                  (conj (pop acc) [c (max d b)])
                  (conj acc [a b])))))
          []
          (sort-by first ivs)))

(defn- kept-params [removed]
  (let [ivs (merge-intervals (filter (fn [[a b]] (< a b)) removed))]
    (loop [u 0.0 ivs ivs out []]
      (if (empty? ivs)
        (cond-> out (< u 0.999999) (conj [u 1.0]))
        (let [[a b] (first ivs)
              out (if (< u (- a 1.0e-9)) (conj out [u a]) out)]
          (recur (max u b) (rest ivs) out))))))

(defn- near? [p q]
  (< (Math/hypot (- (first p) (first q))
                 (- (second p) (second q)))
     0.51))

(defn gap-segment
  "Pieces of a->b that stay `pad` outside every rect."
  [a b rects pad]
  (if (or (empty? rects) (near? a b))
    (if (near? a b) [] [[a b]])
    (let [removed (keep #(overlap-param a b (inflate % pad)) rects)]
      (if (empty? removed)
        [[a b]]
        (->> (kept-params removed)
             (map (fn [[u0 u1]] [(lerp a b u0) (lerp a b u1)]))
             (remove (fn [[p q]] (near? p q))))))))

(defn gap-polyline
  "Split `pts` into subpaths that stay `pad` outside `rects`."
  [pts rects pad]
  (let [pts (vec pts)]
    (cond
      (< (count pts) 2) []
      (empty? rects) [pts]
      :else
      (reduce (fn [paths [p q]]
                (let [cur (peek paths)]
                  (if (and cur (near? (peek cur) p))
                    (conj (pop paths) (conj cur q))
                    (conj paths [p q]))))
              []
              (mapcat (fn [[a b]] (gap-segment a b rects pad))
                      (partition 2 1 pts))))))
