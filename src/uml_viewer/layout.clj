(ns uml-viewer.layout
  (:require [uml-viewer.geom :as geom]
            [uml-viewer.metrics :as m]))

(defn- stereotype-line [c]
  (when-let [st (:stereotype c)]
    (str "«" (name st) "»")))

(defn class-lines [c]
  (let [crap (m/format-crap (:crap c))
        fields (when-not (:hide-members c)
                 (mapv :text (or (:fields c) [])))
        ops (when-not (:hide-members c)
              (mapv :text (remove :private (or (:ops c) []))))]
    (cond-> []
      (stereotype-line c) (conj {:kind :stereo :text (stereotype-line c)})
      true (conj {:kind :name :text (:name c)})
      crap (conj {:kind :crap :text crap})
      (seq fields) (conj {:kind :rule :text nil})
      true (into (map (fn [t] {:kind :field :text t}) fields))
      (seq ops) (conj {:kind :rule :text nil})
      true (into (map (fn [t] {:kind :op :text t}) ops)))))

(defn class-box-size [c]
  (let [lines (class-lines c)
        texts (keep :text lines)
        content-w (apply max 0 (map m/text-w texts))
        w (max 120 (+ (* 2 m/pad) content-w))
        text-lines (count (remove #(= :rule (:kind %)) lines))
        rules (count (filter #(= :rule (:kind %)) lines))
        h (+ (* 2 m/pad)
             (* m/line-h text-lines)
             (* m/pad rules))]
    [w h lines]))

(defn- inherit-edge? [e]
  (contains? #{:inheritance :implements} (:kind e)))

(defn- bfs-ranks
  "Shortest-path ranks from roots so a hub (Game) keeps all targets on the next rank."
  [ids edges]
  (let [idset (set ids)
        out (reduce (fn [m e]
                      (if-not (and (idset (:from e)) (idset (:to e)))
                        m
                        (if (inherit-edge? e)
                          (update m (:to e) (fnil conj []) (:from e))
                          (update m (:from e) (fnil conj []) (:to e)))))
                    {}
                    edges)
        high (set (mapcat val out))
        roots (let [r (filterv #(not (high %)) ids)]
                (if (seq r) r ids))]
    (loop [q (into clojure.lang.PersistentQueue/EMPTY (map #(vector % 0) roots))
           rank (zipmap roots (repeat 0))]
      (if (empty? q)
        (merge (zipmap ids (repeat 0)) rank)
        (let [[n r] (peek q)
              kids (get out n [])
              fresh (remove #(contains? rank %) kids)]
          (recur (into (pop q) (map #(vector % (inc r)) fresh))
                 (reduce (fn [rk k] (assoc rk k (inc r))) rank fresh)))))))

(defn- neighbors [ids edges]
  (let [idset (set ids)]
    (reduce (fn [m e]
              (if (and (idset (:from e)) (idset (:to e)))
                (-> m
                    (update (:from e) (fnil conj #{}) (:to e))
                    (update (:to e) (fnil conj #{}) (:from e)))
                m))
            {}
            edges)))

(defn- barycenter-order [rank-ids pos nbr]
  (vec
    (sort-by (fn [id]
               (let [xs (keep pos (nbr id))]
                 (if (seq xs)
                   (/ (double (reduce + xs)) (count xs))
                   (double (pos id 0)))))
             rank-ids)))

(defn- place-classes [classes edges direction]
  (let [sized (mapv (fn [c]
                      (let [[w h lines] (class-box-size c)]
                        (assoc c :w w :h h :lines lines)))
                    classes)
        by-id (into {} (map (juxt :id identity) sized))
        ids (mapv :id sized)
        ranks (bfs-ranks ids edges)
        nbr (neighbors ids edges)
        grouped (group-by ranks ids)
        rank-keys (sort (keys grouped))
        pos0 (into {} (map-indexed (fn [i id] [id (* i 80)]) ids))
        pos (loop [p pos0 k 0]
              (if (> k 4)
                p
                (recur
                  (reduce (fn [p r]
                            (let [ordered (barycenter-order (grouped r) p nbr)]
                              (into p (map-indexed (fn [i id] [id (* i 80)]) ordered))))
                          p
                          rank-keys)
                  (inc k))))
        groups (mapv (fn [r]
                       (let [ordered (barycenter-order (grouped r) pos nbr)
                             items (mapv by-id ordered)]
                         {:rank r
                          :items items
                          :w (apply max 0 (map :w items))
                          :h (apply max 0 (map :h items))}))
                     rank-keys)
        lr? (contains? #{:lr :rl} direction)]
    (if lr?
      (let [cols (mapv (fn [g]
                         (let [h (+ (apply + (map :h (:items g)))
                                    (* m/class-gap (max 0 (dec (count (:items g))))))]
                           (assoc g :col-h h)))
                       groups)
            total-h (apply max 0 (map :col-h cols))]
        (second
          (reduce
            (fn [[x acc] col]
              (let [y0 (/ (max 0 (- total-h (:col-h col))) 2.0)
                    placed (second
                             (reduce
                               (fn [[y out] c]
                                 [(+ y (:h c) m/class-gap)
                                  (conj out (assoc (dissoc c :w :h)
                                              :rank (:rank col)
                                              :rect (geom/rect x y (:w c) (:h c))))])
                               [y0 []]
                               (:items col)))]
                [(+ x (:w col) m/class-rank-gap)
                 (into acc placed)]))
            [0 []]
            cols)))
      (second
        (reduce
          (fn [[y acc] row]
            (let [row-placed (second
                               (reduce
                                 (fn [[x out] c]
                                   [(+ x (:w c) m/class-gap)
                                    (conj out (assoc (dissoc c :w :h)
                                                :rank (:rank row)
                                                :rect (geom/rect x y (:w c) (:h c))))])
                                 [0 []]
                                 (:items row)))]
              [(+ y (:h row) m/class-rank-gap)
               (into acc row-placed)]))
          [0 []]
          groups)))))

(defn- layout-package [pkg origin-x origin-y edges direction rank-base]
  (let [inner (place-classes (:classes pkg) edges direction)
        inner (mapv #(assoc %
                       :package (:id pkg)
                       :rank (+ rank-base (:rank % 0))
                       :rect (geom/rect (+ origin-x m/pad (get-in % [:rect :x]))
                                        (+ origin-y m/banner-h m/pad (get-in % [:rect :y]))
                                        (get-in % [:rect :w])
                                        (get-in % [:rect :h])))
                    inner)
        title (str (:label pkg)
                   (when-let [c (m/format-crap (:crap pkg))]
                     (str "    " c)))
        body (or (geom/union (map :rect inner))
                 (geom/rect (+ origin-x m/pad)
                            (+ origin-y m/banner-h m/pad)
                            160 40))
        pack-w (max (- (+ (geom/right body) m/pad) origin-x)
                    (+ (* 2 m/pad) (m/text-w title))
                    180)
        pack-h (- (+ (geom/bottom body) m/pad) origin-y)
        pack-rect (geom/rect origin-x origin-y pack-w pack-h)]
    {:id (:id pkg)
     :label (:label pkg)
     :crap (:crap pkg)
     :title title
     :rect pack-rect
     :classes inner}))

(defn layout
  "Content-size classes, Sugiyama-place them inside packages, stack packages
   in document order."
  [diagram]
  (let [edges (:edges diagram)
        direction (:direction diagram :tb)
        pkgs (:packages diagram)
        stride (inc (apply max 1 (map #(count (:classes %)) pkgs)))
        laid (second
               (reduce
                 (fn [[y packs] [i pkg]]
                   (let [lp (layout-package pkg m/margin y edges direction (* i stride))]
                     [(+ y (get-in lp [:rect :h]) m/rank-gap)
                      (conj packs lp)]))
                 [m/margin []]
                 (map-indexed vector pkgs)))
        classes (mapcat :classes laid)
        bounds (or (geom/union (map :rect laid))
                   (geom/rect 0 0 400 300))]
    {:diagram diagram
     :packages (mapv #(dissoc % :classes) laid)
     :classes (vec classes)
     :size {:w (+ (geom/right bounds) m/margin)
            :h (+ (geom/bottom bounds) m/margin)}}))
