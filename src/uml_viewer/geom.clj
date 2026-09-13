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
