(ns uml-viewer.draw
  (:require [clojure.string :as str]
            [quil.core :as q]
            [uml-viewer.geom :as geom]
            [uml-viewer.curve :as curve]
            [uml-viewer.detail :as detail]
            [uml-viewer.hit :as hit]
            [uml-viewer.layout :as layout]))

(def bg [22 28 32])
(def panel [26 36 40])
(def ink [236 236 228])
(def muted [157 184 168])
(def gold [232 196 72])
(def line [42 61 54])

(defn- risk [crap]
  (when-let [mu (:mu crap)]
    (+ (double mu) (double (or (:sigma crap) 0)))))

(defn- mix [a b t]
  (int (+ a (* t (- b a)) 0.5)))

(defn- mix-rgb [c1 c2 t]
  [(mix (nth c1 0) (nth c2 0) t)
   (mix (nth c1 1) (nth c2 1) t)
   (mix (nth c1 2) (nth c2 2) t)])

(defn- ramp [crap none green mid red]
  (let [r (risk crap)]
    (cond
      (nil? r) none
      (<= r 12.0) (mix-rgb green mid (/ r 12.0))
      :else (mix-rgb mid red (min 1.0 (/ (- r 12.0) 12.0))))))

(defn fill-for [crap]
  (ramp crap [36 52 48] [30 74 56] [61 58 24] [74 40 24]))

(defn stroke-for [crap]
  (ramp crap [90 110 100] [95 181 138] [212 192 90] [224 122 74]))

(defn coverage-ink [p]
  (cond
    (nil? p) muted
    (>= p 0.8) [95 181 138]
    (>= p 0.5) gold
    :else [224 122 74]))

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
        size layout/head-size
        half (/ Math/PI 9)
        left (+ ang half)
        right (- ang half)
        x1 (- x (* size (Math/cos left)))
        y1 (- y (* size (Math/sin left)))
        x2 (- x (* size (Math/cos right)))
        y2 (- y (* size (Math/sin right)))]
    (case kind
      :triangle (do
                  (rgb bg)
                  (q/stroke-weight 1.5)
                  (q/triangle x y x1 y1 x2 y2))
      :diamond (let [back (- ang Math/PI)
                     bx (+ x (* size (Math/cos back)))
                     by (+ y (* size (Math/sin back)))]
                 (rgb bg)
                 (q/quad x y x1 y1 bx by x2 y2))
      :diamond-fill (let [back (- ang Math/PI)
                          bx (+ x (* size (Math/cos back)))
                          by (+ y (* size (Math/sin back)))]
                      (rgb ink)
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
      (stroke-rgb (if selected? gold muted) (if selected? 2.2 1.4))
      (q/no-fill)
      (q/stroke-cap :round)
      (let [from (hit/class-by-id scene (:from e))
            to (hit/class-by-id scene (:to e))
            path (-> (curve/basis-path pts)
                     (curve/constrain-ends (:rect from) (:rect to)))
            [behind tip] (curve/end-tangent path)
            samples (curve/flatten-path path)
            obstacles (obstacle-rects scene e)
            strokes (geom/gap-polyline samples obstacles layout/under-gap)]
        (doseq [sub strokes]
          (draw-polyline sub))
        (when (:head e)
          (arrowhead (:head e) tip behind))))))

(defn- draw-package [p selected?]
  (let [r (:rect p)
        crap (:crap p)]
    (rgb (fill-for crap) 80)
    (stroke-rgb (if selected? gold (stroke-for crap))
                (if selected? 2.5 1.4))
    (q/rect (:x r) (:y r) (:w r) (:h r) 8)
    (rgb gold)
    (q/text-align :left :center)
    (q/text-size 14)
    (q/text (:title p) (+ (:x r) layout/pad) (+ (:y r) (/ layout/banner-h 2)))))

(defn- class-line-ink [kind]
  (case kind
    :crap gold
    :stereo muted
    ink))

(defn- draw-rule [r crap y]
  (stroke-rgb (stroke-for crap) 1)
  (q/line (+ (:x r) 6) (+ y 4)
          (- (geom/right r) 6) (+ y 4))
  (+ y layout/pad))

(defn- draw-text-line [r line y]
  (q/text-align :center :top)
  (q/text-size (if (= :name (:kind line)) 14 12))
  (rgb (class-line-ink (:kind line)))
  (q/text (:text line) (geom/cx r) y)
  (+ y layout/line-h))

(defn- draw-class-line [r crap line y]
  (if (= :rule (:kind line))
    (draw-rule r crap y)
    (draw-text-line r line y)))

(defn- corner-mark [c]
  (case (some-> (:stereotype c) name)
    "abstract" "α"
    "interface" "I"
    nil))

(defn- draw-corner-mark [r ch]
  (q/text-align :right :top)
  (q/text-size 12)
  (rgb [255 255 255])
  (q/text ch (- (geom/right r) 6) (+ (:y r) 4)))

(defn- draw-oval [c selected? hovered?]
  (let [r (:rect c)]
    (rgb (fill-for nil))
    (stroke-rgb (cond
                  selected? gold
                  hovered? ink
                  :else (stroke-for nil))
                (if selected? 2.6 1.3))
    (q/ellipse (geom/cx r) (geom/cy r) (:w r) (:h r))
    (q/text-align :center :center)
    (q/text-size 14)
    (rgb ink)
    (q/text (:name c) (geom/cx r) (geom/cy r))))

(defn- draw-class [c selected? hovered?]
  (if (= :oval (:shape c))
    (draw-oval c selected? hovered?)
    (let [r (:rect c)
          crap (:crap c)]
      (rgb (fill-for crap))
      (stroke-rgb (cond
                    selected? gold
                    hovered? ink
                    :else (stroke-for crap))
                  (if selected? 2.6 1.3))
      (q/rect (:x r) (:y r) (:w r) (:h r) 4)
      (reduce (fn [y line] (draw-class-line r crap line y))
              (+ (:y r) layout/pad 4)
              (:lines c))
      (when-let [ch (corner-mark c)]
        (draw-corner-mark r ch)))))

(defn- draw-sidebar-chrome []
  (let [w (q/width)
        h (q/height)
        sw layout/sidebar-w
        x (- w sw)]
    (rgb [18 22 24] 230)
    (q/no-stroke)
    (q/rect x 0 sw h)
    (q/stroke 42 61 54)
    (q/stroke-weight 1)
    (q/line x 0 x h)
    (q/text-align :left :top)
    (q/text-size 16)
    (rgb gold)
    (q/text "Inspector" (+ x 16) 16)
    (q/text-size 13)
    (rgb ink)
    x))

(defn- draw-sidebar-empty [x]
  (rgb muted)
  (q/text "Click a class or package.\nDouble-click a class for its card.\nScroll vertically; Shift-scroll horizontally.\nArrow keys also pan.\nR reloads the EDN file."
          (+ x 16) 48))

(defn- draw-sidebar-class [x scene id]
  (when-let [c (hit/class-by-id scene id)]
    (q/text (:name c) (+ x 16) 48)
    (rgb muted)
    (q/text (if-let [p (:package c)]
              (str "package  " (name p))
              "foreign")
            (+ x 16) 72)
    (when-let [s (layout/format-crap (:crap c))]
      (rgb gold)
      (q/text s (+ x 16) 96))
    (rgb ink)
    (q/text (str/join "\n" (keep :text (filter #(#{:field :op} (:kind %))
                                               (:lines c))))
            (+ x 16) 128)))

(defn- draw-sidebar-package [x scene id]
  (when-let [p (hit/package-by-id scene id)]
    (q/text (:label p) (+ x 16) 48)
    (when-let [s (layout/format-crap (:crap p))]
      (rgb gold)
      (q/text s (+ x 16) 80))
    (rgb muted)
    (q/text (str (count (filter #(= (:id p) (:package %))
                                (:classes scene)))
                 " classes")
            (+ x 16) 112)))

(defn- draw-sidebar-error [x h err]
  (rgb [224 122 74])
  (q/text (str "IR error:\n" err) (+ x 16) (- h 120)))

(defn- draw-sidebar [state]
  (let [x (draw-sidebar-chrome)
        sel (:selected state)
        scene (:scene state)]
    (case (:kind sel)
      nil (draw-sidebar-empty x)
      :class (draw-sidebar-class x scene (:id sel))
      :package (draw-sidebar-package x scene (:id sel))
      nil)
    (when-let [err (:error state)]
      (draw-sidebar-error x (q/height) err))))

(defn draw-state [state]
  (apply q/background bg)
  (q/push-matrix)
  (q/translate (- (:cam-x state)) (- (:cam-y state)))
  (doseq [sec (:sections (:scene state))]
    (rgb gold)
    (q/text-align :left :top)
    (q/text-size 20)
    (q/text (or (:title sec) "") layout/pad (:title-y sec)))
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
    (rgb muted)
    (q/text-align :left :top)
    (q/text-size 12)
    (q/text title 12 8)))

(defn- detail-row-color [row]
  (case (:kind row)
    :name ink
    :crap gold
    :heading gold
    :field ink
    :rel ink
    :stats ink
    muted))

(defn- cell-color [row col]
  (case (:id col)
    :cov (coverage-ink (:coverage row))
    :crap (stroke-for {:mu (:crap-n row)})
    :survived (if (pos? (or (:survived row) 0))
                [224 122 74]
                muted)
    :killed muted
    :cc muted
    muted))

(defn- draw-detail-cells [row y]
  (doseq [col (detail/column-layout)]
    (let [s (if (= :col-header (:kind row))
              (:label col)
              (get row (:key col)))]
      (when s
        (q/text-align :right :top)
        (q/text-size 13)
        (rgb (if (= :col-header (:kind row))
               gold
               (cell-color row col)))
        (q/text s (:right col) y)))))

(defn- draw-hover-wash [row]
  (q/no-stroke)
  (q/fill 232 196 72 48)
  (q/rect 0 (:y row) detail/width (:h row)))

(defn- draw-row-label [row hover?]
  (when (:text row)
    (let [x detail/pad
          y (:y row)
          cols (detail/column-layout)
          name-right (if (seq cols)
                       (- (:left (first cols)) detail/col-gap)
                       (- detail/width detail/pad))]
      (q/text-align :left :top)
      (q/text-size (if (= :name (:kind row)) 20 13))
      (rgb (cond
             hover? gold
             (:private row) muted
             :else (detail-row-color row)))
      (q/text (:text row) x y
              (max 0 (- name-right x)) (:h row)))))

(defn- draw-detail-row [row hover?]
  (when hover? (draw-hover-wash row))
  (case (:kind row)
    :col-header (draw-detail-cells row (:y row))
    :stats (do (draw-row-label row hover?)
               (draw-detail-cells row (:y row)))
    (draw-row-label row hover?)))

(defn draw-detail
  ([model scroll] (draw-detail model scroll nil))
  ([model scroll hover]
   (apply q/background bg)
   (q/push-matrix)
   (q/translate 0 (- scroll))
   (doseq [row (detail/rows model)]
     (draw-detail-row row (and hover (= hover (:op-name row)))))
   (q/pop-matrix)))
