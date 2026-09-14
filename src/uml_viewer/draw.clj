(ns uml-viewer.draw
  (:require [clojure.string :as str]
            [quil.core :as q]
            [uml-viewer.geom :as geom]
            [uml-viewer.curve :as curve]
            [uml-viewer.detail :as detail]
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
  ;; Mermaid class markers: long shallow chevron (half-angle ~19°), orient=auto.
  (let [ang (Math/atan2 (- y py) (- x px))
        size m/head-size
        half (/ Math/PI 9)
        left (+ ang half)
        right (- ang half)
        x1 (- x (* size (Math/cos left)))
        y1 (- y (* size (Math/sin left)))
        x2 (- x (* size (Math/cos right)))
        y2 (- y (* size (Math/sin right)))]
    (case kind
      :triangle (do
                  (rgb theme/bg)
                  (q/stroke-weight 1.5)
                  (q/triangle x y x1 y1 x2 y2))
      :diamond (let [back (- ang Math/PI)
                     bx (+ x (* size (Math/cos back)))
                     by (+ y (* size (Math/sin back)))]
                 (rgb theme/bg)
                 (q/quad x y x1 y1 bx by x2 y2))
      :diamond-fill (let [back (- ang Math/PI)
                          bx (+ x (* size (Math/cos back)))
                          by (+ y (* size (Math/sin back)))]
                      (rgb theme/ink)
                      (q/quad x y x1 y1 bx by x2 y2))
      (do
        (q/line x y x1 y1)
        (q/line x y x2 y2)))))

(defn- draw-polyline [pts]
  (doseq [[[x1 y1] [x2 y2]] (partition 2 1 pts)]
    (q/line x1 y1 x2 y2)))

(defn- obstacle-rects [scene e]
  (let [ends #{(:from e) (:to e)}]
    (mapv :rect
          (remove (fn [c]
                    (or (:dummy? c) (contains? ends (:id c))))
                  (:classes scene)))))

(defn- draw-edge [e selected? scene]
  (let [pts (vec (:points e))]
    (when (next pts)
      (stroke-rgb (if selected? theme/gold theme/muted) (if selected? 2.2 1.4))
      (q/no-fill)
      (q/stroke-cap :round)
      (let [from (hit/class-by-id scene (:from e))
            to (hit/class-by-id scene (:to e))
            path (-> (curve/basis-path pts)
                     (curve/constrain-ends (:rect from) (:rect to)))
            [behind tip] (curve/end-tangent path)
            samples (curve/flatten-path path)
            obstacles (obstacle-rects scene e)
            strokes (geom/gap-polyline samples obstacles m/under-gap)]
        (doseq [sub strokes]
          (draw-polyline sub))
        (when (:head e)
          (arrowhead (:head e) tip behind))))))

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
        sw m/sidebar-w
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
          (q/text "Click a class or package.\nScroll vertically; Shift-scroll horizontally.\nArrow keys also pan.\nR reloads the EDN file."
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
  (doseq [sec (:sections (:scene state))]
    (rgb theme/gold)
    (q/text-align :left :top)
    (q/text-size 20)
    (q/text (or (:title sec) "") m/pad (:title-y sec)))
  (doseq [p (:packages (:scene state))]
    (draw-package p (and (= :package (get-in state [:selected :kind]))
                         (= (:id p) (get-in state [:selected :id])))))
  (let [sel-id (when (= :class (get-in state [:selected :kind]))
                 (get-in state [:selected :id]))
        hover-id (when (= :class (get-in state [:hover :kind]))
                   (get-in state [:hover :id]))]
    (doseq [e (:edges (:scene state))]
      (draw-edge e
                 (or (= sel-id (:from e)) (= sel-id (:to e)))
                 (:scene state)))
    (doseq [c (remove :dummy? (:classes (:scene state)))]
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

(defn- detail-row-color [row]
  (case (:kind row)
    :name theme/ink
    :crap theme/gold
    :heading theme/gold
    :field theme/ink
    :rel theme/ink
    :stats theme/ink
    theme/muted))

(defn- cell-color [row col]
  (case (:id col)
    :cov (theme/coverage-ink (:coverage row))
    :crap (theme/stroke-for {:mu (:crap-n row)})
    :survived (if (pos? (or (:survived row) 0))
                [224 122 74]
                theme/muted)
    :killed theme/muted
    :cc theme/muted
    theme/muted))

(defn- draw-detail-cells [row y]
  (doseq [col (detail/column-layout)]
    (let [s (if (= :col-header (:kind row))
              (:label col)
              (get row (:key col)))]
      (when s
        (q/text-align :right :top)
        (q/text-size 13)
        (rgb (if (= :col-header (:kind row))
               theme/gold
               (cell-color row col)))
        (q/text s (:right col) y)))))

(defn- draw-detail-row [row]
  (let [x detail/pad
        y (:y row)
        cols (detail/column-layout)
        name-right (if (seq cols)
                     (- (:left (first cols)) detail/col-gap)
                     (- detail/width detail/pad))]
    (when (and (:text row) (not= :col-header (:kind row)))
      (q/text-align :left :top)
      (q/text-size (if (= :name (:kind row)) 20 13))
      (rgb (if (:private row) theme/muted (detail-row-color row)))
      (q/text (or (:text row) "") x y
              (max 0 (- name-right x)) (:h row)))
    (when (#{:col-header :stats} (:kind row))
      (draw-detail-cells row y))))

(defn draw-detail [model scroll]
  (apply q/background theme/bg)
  (q/push-matrix)
  (q/translate 0 (- scroll))
  (doseq [row (detail/rows model)]
    (draw-detail-row row))
  (q/pop-matrix))
