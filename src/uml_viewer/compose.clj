(ns uml-viewer.compose
  (:require [uml-viewer.geom :as geom]
            [uml-viewer.layout :as layout]
            [uml-viewer.route :as route]))

(defn compile-diagram [diagram]
  (route/route (layout/layout diagram)))

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

(defn compile-document
  "Layout and route each diagram, then stack them top to bottom."
  [doc]
  (let [gap 64
        title-h 40
        raw (map-indexed
              (fn [i d]
                (-> (route/route (layout/layout d))
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
