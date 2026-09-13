(ns uml-viewer.draw
  (:require [clojure.string :as str]
            [quil.core :as q]
            [uml-viewer.geom :as geom]
            [uml-viewer.hit :as hit]
            [uml-viewer.metrics :as m]
            [uml-viewer.theme :as theme]))

(defn- rgb
  ([c] (apply q/fill c))
  ([c a] (apply q/fill (conj (vec c) a))))

(defn- stroke-rgb
  ([c] (apply q/stroke c))
  ([c w]
   (apply q/stroke c)
   (q/stroke-weight w)))

(defn- arrowhead [kind [x y] [px py]]
  (let [ang (Math/atan2 (- y py) (- x px))
        size 12
        left (+ ang (/ Math/PI 6))
        right (- ang (/ Math/PI 6))
        x1 (- x (* size (Math/cos left)))
        y1 (- y (* size (Math/sin left)))
        x2 (- x (* size (Math/cos right)))
        y2 (- y (* size (Math/sin right)))]
    (case kind
      :triangle (do
                  (q/fill 22 28 32)
                  (q/stroke-weight 1.5)
                  (q/triangle x y x1 y1 x2 y2))
      :diamond (let [back (- ang Math/PI)
                     bx (+ x (* size (Math/cos back)))
                     by (+ y (* size (Math/sin back)))]
                 (q/fill 22 28 32)
                 (q/quad x y x1 y1 bx by x2 y2))
      :diamond-fill (let [back (- ang Math/PI)
                          bx (+ x (* size (Math/cos back)))
                          by (+ y (* size (Math/sin back)))]
                      (rgb theme/ink)
                      (q/quad x y x1 y1 bx by x2 y2))
      (do
        (q/line x y x1 y1)
        (q/line x y x2 y2)))))

(defn- draw-polyline [pts dashed?]
  (when (next pts)
    (when dashed?
      (q/stroke-cap :round))
    (doseq [[a b] (partition 2 1 pts)]
      (if dashed?
        (let [[x1 y1] a [x2 y2] b
              dx (- x2 x1) dy (- y2 y1)
              len (Math/hypot dx dy)
              n (max 1 (int (/ len 10)))]
          (dotimes [i n]
            (when (even? i)
              (let [t0 (/ i n)
                    t1 (/ (min (inc i) n) n)]
                (q/line (+ x1 (* dx t0)) (+ y1 (* dy t0))
                        (+ x1 (* dx t1)) (+ y1 (* dy t1)))))))
        (q/line (first a) (second a) (first b) (second b))))))

(defn- draw-edge [e selected?]
  (let [pts (:points e)]
    (stroke-rgb (if selected? theme/gold theme/muted) (if selected? 2.2 1.3))
    (q/no-fill)
    (draw-polyline pts (:dashed? e))
    (when (and (next pts) (:head e))
      (arrowhead (:head e) (last pts) (last (butlast pts))))))

(defn- draw-package [p selected?]
  (let [r (:rect p)
        crap (:crap p)]
    (rgb (theme/fill-for crap) 80)
    (stroke-rgb (if selected? theme/gold (theme/stroke-for crap))
                (if selected? 2.5 1.4))
    (q/rect (:x r) (:y r) (:w r) (:h r) 8)
    (rgb theme/gold)
    (q/text-align :left :center)
    (q/text-size 14)
    (q/text (:title p) (+ (:x r) m/pad) (+ (:y r) (/ m/banner-h 2)))))

(defn- draw-class [c selected? hovered?]
  (let [r (:rect c)
        crap (:crap c)]
    (rgb (theme/fill-for crap))
    (stroke-rgb (cond
                  selected? theme/gold
                  hovered? theme/ink
                  :else (theme/stroke-for crap))
                (if selected? 2.6 1.3))
    (q/rect (:x r) (:y r) (:w r) (:h r) 4)
    (loop [lines (:lines c)
           y (+ (:y r) m/pad 4)]
      (when (seq lines)
        (let [line (first lines)]
          (if (= :rule (:kind line))
            (do
              (stroke-rgb (theme/stroke-for crap) 1)
              (q/line (+ (:x r) 6) (+ y 4)
                      (- (geom/right r) 6) (+ y 4))
              (recur (rest lines) (+ y 8)))
            (do
              (q/text-align :center :top)
              (q/text-size (if (= :name (:kind line)) 14 12))
              (rgb (case (:kind line)
                     :name theme/ink
                     :crap theme/gold
                     :stereo theme/muted
                     theme/ink))
              (q/text (:text line) (geom/cx r) y)
              (recur (rest lines) (+ y m/line-h)))))))))

(defn- draw-sidebar [state]
  (let [w (q/width)
        h (q/height)
        sw 280
        x (- w sw)]
    (rgb [18 22 24] 230)
    (q/no-stroke)
    (q/rect x 0 sw h)
    (q/stroke 42 61 54)
    (q/stroke-weight 1)
    (q/line x 0 x h)
    (let [sel (:selected state)
          scene (:scene state)]
      (q/text-align :left :top)
      (q/text-size 16)
      (rgb theme/gold)
      (q/text "Inspector" (+ x 16) 16)
      (q/text-size 13)
      (rgb theme/ink)
      (cond
        (nil? sel)
        (do
          (rgb theme/muted)
          (q/text "Click a class or package.\nDrag empty space to pan.\nR reloads the EDN file."
                  (+ x 16) 48))

        (= :class (:kind sel))
        (when-let [c (hit/class-by-id scene (:id sel))]
          (q/text (:name c) (+ x 16) 48)
          (rgb theme/muted)
          (q/text (str "package  " (name (:package c))) (+ x 16) 72)
          (when-let [s (m/format-crap (:crap c))]
            (rgb theme/gold)
            (q/text s (+ x 16) 96))
          (rgb theme/ink)
          (q/text (str/join "\n" (keep :text (filter #(#{:field :op} (:kind %))
                                                     (:lines c))))
                  (+ x 16) 128))

        (= :package (:kind sel))
        (when-let [p (hit/package-by-id scene (:id sel))]
          (q/text (:label p) (+ x 16) 48)
          (when-let [s (m/format-crap (:crap p))]
            (rgb theme/gold)
            (q/text s (+ x 16) 80))
          (rgb theme/muted)
          (q/text (str (count (filter #(= (:id p) (:package %))
                                      (:classes scene)))
                       " classes")
                  (+ x 16) 112))))
    (when-let [err (:error state)]
      (rgb [224 122 74])
      (q/text (str "IR error:\n" err) (+ x 16) (- h 120)))))

(defn draw-state [state]
  (apply q/background theme/bg)
  (q/push-matrix)
  (q/translate (- (:cam-x state)) (- (:cam-y state)))
  (doseq [p (:packages (:scene state))]
    (draw-package p (and (= :package (get-in state [:selected :kind]))
                         (= (:id p) (get-in state [:selected :id])))))
  (let [sel-id (when (= :class (get-in state [:selected :kind]))
                 (get-in state [:selected :id]))
        hover-id (when (= :class (get-in state [:hover :kind]))
                   (get-in state [:hover :id]))]
    (doseq [e (:edges (:scene state))]
      (draw-edge e (or (= sel-id (:from e)) (= sel-id (:to e)))))
    (doseq [c (:classes (:scene state))]
      (draw-class c
                  (= sel-id (:id c))
                  (= hover-id (:id c)))))
  (q/pop-matrix)
  (draw-sidebar state)
  (when-let [title (get-in state [:scene :diagram :title])]
    (rgb theme/muted)
    (q/text-align :left :top)
    (q/text-size 12)
    (q/text title 12 8)))
