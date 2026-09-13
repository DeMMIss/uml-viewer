(ns uml-viewer.layout
  (:require [uml-viewer.geom :as geom]
            [uml-viewer.ir :as ir]
            [uml-viewer.metrics :as m]))

(defn- stereotype-line [c]
  (when-let [st (:stereotype c)]
    (str "«" (name st) "»")))

(defn class-lines [c]
  (let [crap (m/format-crap (:crap c))
        fields (mapv :text (:fields c))
        ops (mapv :text (:ops c))]
    (cond-> []
      (stereotype-line c) (conj {:kind :stereo :text (stereotype-line c)})
      true (conj {:kind :name :text (:name c)})
      crap (conj {:kind :crap :text crap})
      (seq fields) (conj {:kind :rule :text nil})
      true (into (map (fn [t] {:kind :field :text t}) fields))
      (seq ops) (conj {:kind :rule :text nil})
      true (into (map (fn [t] {:kind :op :text t}) ops)))))

(defn- class-box-size [c]
  (let [lines (class-lines c)
        texts (keep :text lines)
        w (max 150 (+ (* 2 m/pad) (apply max 0 (map m/text-w texts))))
        h (+ (* 2 m/pad)
             (* m/line-h (count (remove #(= :rule (:kind %)) lines)))
             (* 8 (count (filter #(= :rule (:kind %)) lines))))]
    [w h lines]))

(defn- wrap-row [sizes max-w]
  (loop [remaining sizes
         rows []
         current []
         used 0]
    (if (empty? remaining)
      (if (seq current) (conj rows current) rows)
      (let [{:keys [w] :as item} (first remaining)
            next-used (if (seq current)
                        (+ used m/class-gap w)
                        w)]
        (if (and (seq current) (> next-used max-w))
          (recur remaining (conj rows current) [] 0)
          (recur (rest remaining)
                 rows
                 (conj current item)
                 next-used))))))

(defn- layout-package [pkg origin-x origin-y]
  (let [sized (mapv (fn [c]
                      (let [[w h lines] (class-box-size c)]
                        (assoc c :w w :h h :lines lines)))
                    (:classes pkg))
        rows (wrap-row sized 980)
        row-dims (mapv (fn [row]
                         {:w (+ (apply + (map :w row))
                                (* m/class-gap (max 0 (dec (count row)))))
                          :h (apply max 0 (map :h row))
                          :items row})
                       rows)
        inner-w (apply max 0 (map :w row-dims))
        title (str (:label pkg)
                   (when-let [c (m/format-crap (:crap pkg))]
                     (str "    " c)))
        pw (max (+ (* 2 m/pad) (m/text-w title))
                (+ (* 2 m/pad) inner-w)
                180)
        inner-h (apply + (map :h row-dims))
        inner-h (+ inner-h (* m/class-gap (max 0 (dec (count row-dims)))))
        ph (+ m/banner-h (* 2 m/pad) inner-h)
        pack-rect (geom/rect origin-x origin-y pw ph)
        classes (loop [rows row-dims
                       y (+ origin-y m/banner-h m/pad)
                       acc []]
                  (if (empty? rows)
                    acc
                    (let [row (first rows)
                          start-x (+ origin-x (/ (- pw (:w row)) 2.0))
                          placed (second
                                   (reduce
                                     (fn [[x out] c]
                                       [(+ x (:w c) m/class-gap)
                                        (conj out
                                              (assoc (dissoc c :w :h)
                                                :rect (geom/rect x y (:w c) (:h c))
                                                :package (:id pkg)))])
                                     [start-x []]
                                     (:items row)))]
                      (recur (rest rows)
                             (+ y (:h row) m/class-gap)
                             (into acc placed)))))]
    {:id (:id pkg)
     :label (:label pkg)
     :crap (:crap pkg)
     :title title
     :rect pack-rect
     :classes classes}))

(defn- inherit-edge? [e]
  (contains? #{:inheritance :implements} (:kind e)))

(defn- package-ranks [diagram]
  (let [cp (into {} (for [p (:packages diagram)
                          c (:classes p)]
                      [(:id c) (:id p)]))
        pkgs (mapv :id (:packages diagram))
        cross (fn [e]
                (let [fp (cp (:from e))
                      tp (cp (:to e))]
                  (when (and fp tp (not= fp tp))
                    [fp tp])))
        inherit (keep (fn [e]
                        (when (inherit-edge? e) (cross e)))
                      (:edges diagram))
        downward (keep (fn [e]
                         (when-not (inherit-edge? e) (cross e)))
                       (:edges diagram))]
    (loop [rank (zipmap pkgs (repeat 0)) n 0]
      (if (> n (* 2 (count pkgs)))
        rank
        (let [next (-> rank
                       (as-> r (reduce (fn [r [child parent]]
                                         (assoc r child (max (r child) (inc (r parent)))))
                                       r inherit))
                       (as-> r (reduce (fn [r [from to]]
                                         (assoc r to (max (r to) (inc (r from)))))
                                       r downward)))]
          (if (= next rank)
            rank
            (recur next (inc n))))))))

(defn layout
  "Place packages in ranks and classes inside them. Returns a scene."
  [diagram]
  (let [ranks (package-ranks diagram)
        grouped (->> (:packages diagram)
                     (group-by #(ranks (:id %)))
                     (sort-by key))
        laid (second
               (reduce
                 (fn [[y packs] [_ pkgs]]
                   (let [placed (second
                                  (reduce
                                    (fn [[x acc] pkg]
                                      (let [lp (layout-package pkg x y)]
                                        [(+ x (get-in lp [:rect :w]) m/pack-gap)
                                         (conj acc lp)]))
                                    [m/margin []]
                                    pkgs))
                         row-h (apply max 0 (map #(get-in % [:rect :h]) placed))]
                     [(+ y row-h m/rank-gap)
                      (into packs placed)]))
                 [m/margin []]
                 grouped))
        classes (mapcat :classes laid)
        bounds (or (geom/union (map :rect laid))
                   (geom/rect 0 0 400 300))]
    {:diagram diagram
     :packages (mapv #(dissoc % :classes) laid)
     :classes (vec classes)
     :size {:w (+ (geom/right bounds) m/margin)
            :h (+ (geom/bottom bounds) m/margin)}}))
