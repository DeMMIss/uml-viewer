(ns uml-viewer.route
  (:require [uml-viewer.geom :as geom]
            [uml-viewer.metrics :as m]))

(defn- class-by-id [scene]
  (into {} (map (juxt :id identity) (:classes scene))))

(defn- dashed? [kind]
  (contains? #{:implements :dependency} kind))

(defn- head [kind]
  (case kind
    :inheritance :triangle
    :implements :triangle
    :composition :diamond-fill
    :aggregation :diamond
    :association :open
    :dependency :open
    :open))

(defn- lr? [scene]
  (contains? #{:lr :rl} (get-in scene [:diagram :direction] :tb)))

(defn- collinear? [[x1 y1] [x2 y2] [x3 y3]]
  (or (and (< (abs (- x1 x2)) 0.51) (< (abs (- x2 x3)) 0.51))
      (and (< (abs (- y1 y2)) 0.51) (< (abs (- y2 y3)) 0.51))))

(defn- between? [a b c]
  (and (collinear? a b c)
       (let [[x1 y1] a [x2 y2] b [x3 y3] c]
         (and (<= (min x1 x3) x2 (max x1 x3))
              (<= (min y1 y3) y2 (max y1 y3))))))

(defn- collapse [pts]
  (reduce (fn [acc p]
            (cond
              (empty? acc) [p]
              (= p (last acc)) acc
              (and (>= (count acc) 2)
                   (between? (last (butlast acc)) (last acc) p))
              (let [without (pop acc)]
                (if (= p (last without))
                  without
                  (conj without p)))
              :else (conj acc p)))
          []
          pts))

(defn- path-hits? [pts from-id to-id classes]
  (let [others (remove #(or (= (:id %) from-id) (= (:id %) to-id) (:dummy? %))
                       classes)]
    (some (fn [[a b]]
            (some #(geom/segment-hits-rect? a b (:rect %)) others))
          (partition 2 1 pts))))

(defn- rank-extent [classes rank lr? side]
  (let [ns (filter #(= rank (:rank % 0)) classes)]
    (when (seq ns)
      (if lr?
        (if (= side :after)
          (apply max (map #(geom/right (:rect %)) ns))
          (apply min (map #(:x (:rect %)) ns)))
        (if (= side :after)
          (apply max (map #(geom/bottom (:rect %)) ns))
          (apply min (map #(:y (:rect %)) ns)))))))

(defn- channel [classes from-rank to-rank lr?]
  (let [lo-rank (min from-rank to-rank)
        hi-rank (max from-rank to-rank)
        a (rank-extent classes lo-rank lr? :after)
        b (rank-extent classes hi-rank lr? :before)]
    (when (and a b)
      {:lo a :hi b :span (- b a)})))

(defn- lane-coord [ch i n]
  (let [span (max m/lane-gap (:span ch 0))
        step (/ span (double (inc (max n 1))))]
    (+ (:lo ch) (* (inc i) step))))

(defn- rank-bbox [classes rank]
  (geom/union (map :rect (filter #(= rank (:rank % 0)) classes))))

(defn- clip-through
  "Mermaid-style: last direction is the ray from the class center through the
  adjacent waypoint, so the arrowhead is not forced orthogonal to a face."
  [from-r to-r waypoints]
  (let [wps (vec waypoints)]
    (if (empty? wps)
      [(geom/intersect-rect from-r [(geom/cx to-r) (geom/cy to-r)])
       (geom/intersect-rect to-r [(geom/cx from-r) (geom/cy from-r)])]
      (collapse
        (concat [(geom/intersect-rect from-r (first wps))]
                wps
                [(geom/intersect-rect to-r (last wps))])))))

(defn- channel-waypoints [from to ch i n lr?]
  (let [lane (lane-coord ch i n)
        fy (geom/cy (:rect from))
        ty (geom/cy (:rect to))
        fx (geom/cx (:rect from))
        tx (geom/cx (:rect to))]
    (if lr?
      [[lane fy] [lane ty]]
      [[fx lane] [tx lane]])))

(defn- same-rank-waypoints [classes from to i lr?]
  (let [bb (or (rank-bbox classes (:rank from 0)) (:rect from))
        fy (geom/cy (:rect from))
        ty (geom/cy (:rect to))
        fx (geom/cx (:rect from))
        tx (geom/cx (:rect to))]
    (if lr?
      (let [track (+ (geom/right bb) (* 2 m/lane-gap) (* (inc i) m/lane-gap))]
        [[track fy] [track ty]])
      (let [track (+ (geom/bottom bb) (* 2 m/lane-gap) (* (inc i) m/lane-gap))]
        [[fx track] [tx track]]))))

(defn- try-paths [candidates from-id to-id classes]
  (or (first (filter #(not (path-hits? % from-id to-id classes)) candidates))
      (first candidates)))

(defn route [scene]
  (let [idx (class-by-id scene)
        classes (:classes scene)
        lr? (lr? scene)
        edges (:edges (:diagram scene))
        annotated
        (mapv (fn [e]
                (assoc e :from-n (idx (:from e)) :to-n (idx (:to e))))
              edges)
        hop-groups (group-by (fn [e]
                               (let [a (:rank (:from-n e) 0)
                                     b (:rank (:to-n e) 0)]
                                 [(min a b) (max a b)]))
                             annotated)
        hop-index (into {}
                        (mapcat (fn [[k group]]
                                  (map-indexed (fn [i e] [e [i (count group)]])
                                               (sort-by (juxt :from :to) group)))
                                hop-groups))]
    (assoc scene
      :edges
      (mapv (fn [e]
              (let [from (:from-n e)
                    to (:to-n e)
                    [i n] (hop-index e [0 1])
                    same? (= (:rank from 0) (:rank to 0))
                    wps (if same?
                          (same-rank-waypoints classes from to i lr?)
                          (if-let [ch (channel classes (:rank from 0) (:rank to 0) lr?)]
                            (channel-waypoints from to ch i n lr?)
                            [[(/ (+ (geom/cx (:rect from)) (geom/cx (:rect to))) 2.0)
                              (/ (+ (geom/cy (:rect from)) (geom/cy (:rect to))) 2.0)]]))
                    primary (clip-through (:rect from) (:rect to) wps)
                    alt (when-not same?
                          (when-let [ch (channel classes (:rank from 0) (:rank to 0) lr?)]
                            (clip-through (:rect from) (:rect to)
                                          (channel-waypoints from to ch (- n i 1) n lr?))))
                    pts (try-paths (remove nil? [primary alt])
                                   (:id from) (:id to) classes)]
                (assoc (dissoc e :from-n :to-n)
                  :points (vec pts)
                  :dashed? (dashed? (:kind e))
                  :head (head (:kind e)))))
            annotated))))
