(ns uml-viewer.compose
  (:require [uml-viewer.curve :as curve]
            [uml-viewer.geom :as geom]
            [uml-viewer.layout :as layout]
            [uml-viewer.route :as route]))

(defn- qid [idx id]
  (keyword (str "d" idx "-" (name id))))

(defn- move-rect [r dx dy]
  (geom/rect (+ (:x r) dx) (+ (:y r) dy) (:w r) (:h r)))

(defn- qualify [idx scene]
  (let [q #(qid idx %)]
    (-> scene
        (update :packages
                (fn [ps]
                  (mapv #(assoc % :id (q (:id %))) ps)))
        (update :classes
                (fn [cs]
                  (mapv #(assoc %
                           :id (q (:id %))
                           :package (when-let [p (:package %)] (q p)))
                        cs)))
        (update :edges
                (fn [es]
                  (mapv #(assoc % :from (q (:from %)) :to (q (:to %))) es))))))

(defn- translate [scene dx dy]
  (-> scene
      (update :packages (fn [ps] (mapv #(update % :rect move-rect dx dy) ps)))
      (update :classes (fn [cs] (mapv #(update % :rect move-rect dx dy) cs)))
      (update :edges
              (fn [es]
                (mapv (fn [e]
                        (update e :points
                                (fn [pts]
                                  (mapv (fn [[x y]] [(+ x dx) (+ y dy)]) pts))))
                      es)))))

(defn- edge-sample-rects [e]
  (let [pts (vec (:points e))]
    (when (next pts)
      (let [samples (try
                      (curve/flatten-path (curve/basis-path pts))
                      (catch Exception _ pts))]
        (map (fn [[x y]] (geom/rect x y 0 0))
             (concat pts samples))))))

(defn- content-rects [scene]
  (concat (keep :rect (:packages scene))
          (keep :rect (:classes scene))
          (mapcat edge-sample-rects (:edges scene))))

(defn- fit-scene
  "Shift and size the scene so routed edges that swing past the boxes stay on canvas."
  [scene]
  (let [bounds (or (geom/union (content-rects scene))
                   (geom/rect 0 0 400 300))
        dx (max 0 (- layout/margin (:x bounds)))
        dy (max 0 (- layout/margin (:y bounds)))
        scene (if (and (zero? dx) (zero? dy))
                scene
                (translate scene dx dy))
        bounds (or (geom/union (content-rects scene)) bounds)]
    (assoc scene :size {:w (+ (geom/right bounds) layout/margin)
                        :h (+ (geom/bottom bounds) layout/margin)})))

(defn compile-diagram [diagram]
  (fit-scene (route/route (layout/layout diagram))))

(defn compile-document
  "Layout and route each diagram, then stack them top to bottom."
  [doc]
  (let [gap 64
        title-h 40
        raw (map-indexed
              (fn [i d]
                (-> (compile-diagram d)
                    (assoc :title (:title d) :crap (:crap d))
                    (#(qualify i %))))
              (:diagrams doc))
        [total-h sections]
        (reduce
          (fn [[y acc] s]
            (let [s' (translate s layout/margin (+ y title-h))]
              [(+ y title-h (get-in s [:size :h]) gap)
               (conj acc (assoc s' :title-y y))]))
          [24 []]
          raw)]
    {:title (:title doc)
     :sections sections
     :classes (vec (mapcat :classes sections))
     :packages (vec (mapcat :packages sections))
     :edges (vec (mapcat :edges sections))
     :diagram {:title (:title doc)}
     :size {:w (+ layout/margin (apply max 400 (map #(get-in % [:size :w]) sections)))
            :h total-h}}))
