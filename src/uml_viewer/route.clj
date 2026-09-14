(ns uml-viewer.route
  (:require [uml-viewer.geom :as geom]
            [uml-viewer.metrics :as m]))

(defn- class-by-id [scene]
  (into {} (map (juxt :id identity) (:classes scene))))

(defn- head [kind]
  (case kind
    :inheritance :triangle
    :implements :triangle
    :composition :diamond-fill
    :aggregation :diamond
    :association :open
    :dependency :open
    :open))

(defn- port-on-t [r face t]
  (let [t (max 0.12 (min 0.88 t))]
    (case face
      :top [(+ (:x r) (* t (:w r))) (:y r)]
      :bottom [(+ (:x r) (* t (:w r))) (geom/bottom r)]
      :left [(:x r) (+ (:y r) (* t (:h r)))]
      :right [(geom/right r) (+ (:y r) (* t (:h r)))])))

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

(defn- try-paths [candidates from-id to-id classes]
  (or (first (filter #(and (seq %)
                           (not (path-hits? % from-id to-id classes)))
                     candidates))
      (first candidates)))

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

(defn- clamp [v lo hi]
  (max lo (min hi v)))

(defn- face-span [r face]
  (case face
    (:top :bottom) [(:x r) (geom/right r)]
    (:left :right) [(:y r) (geom/bottom r)]))

(defn- overlap [[a0 a1] [b0 b1]]
  (let [lo (max a0 b0)
        hi (min a1 b1)]
    (when (< (+ lo 1.0) hi)
      [lo hi])))

(defn- stack-faces [from to lr?]
  (if lr?
    (if (< (geom/cy (:rect from)) (geom/cy (:rect to)))
      {:from-face :bottom :to-face :top}
      {:from-face :top :to-face :bottom})
    (if (< (geom/cx (:rect from)) (geom/cx (:rect to)))
      {:from-face :right :to-face :left}
      {:from-face :left :to-face :right})))

(defn- flow-faces [from to lr?]
  (if lr?
    (if (<= (geom/cx (:rect from)) (geom/cx (:rect to)))
      {:from-face :right :to-face :left}
      {:from-face :left :to-face :right})
    (if (<= (geom/cy (:rect from)) (geom/cy (:rect to)))
      {:from-face :bottom :to-face :top}
      {:from-face :top :to-face :bottom})))

(defn- side-faces [side]
  (case side
    :east {:from-face :right :to-face :right}
    :west {:from-face :left :to-face :left}
    :south {:from-face :bottom :to-face :bottom}
    :north {:from-face :top :to-face :top}))

(defn- port [node face t]
  (port-on-t (:rect node) face t))

(defn- through-channel [start end ch i n lr?]
  (let [lane (lane-coord ch i n)]
    (if lr?
      (collapse [start [lane (second start)] [lane (second end)] end])
      (collapse [start [(first start) lane] [(first end) lane] end]))))

(defn- along-stack [from to from-t to-t lr?]
  (let [{:keys [from-face to-face]} (stack-faces from to lr?)
        start (port from from-face from-t)
        end (port to to-face to-t)
        ov (overlap (face-span (:rect from) from-face)
                    (face-span (:rect to) to-face))]
    (if ov
      (let [[lo hi] ov
            span (- hi lo)
            a (+ lo (* from-t span))
            b (+ lo (* to-t span))]
        (if lr?
          (collapse [[a (second start)] [b (second end)]])
          (collapse [[(first start) a] [(first end) b]])))
      (if lr?
        (let [gap-y (/ (+ (second start) (second end)) 2.0)]
          (collapse [start [(first start) gap-y] [(first end) gap-y] end]))
        (let [gap-x (/ (+ (first start) (first end)) 2.0)]
          (collapse [start [gap-x (second start)] [gap-x (second end)] end]))))))

(defn- pair-u [from to from-t to-t side i]
  (let [{:keys [from-face to-face]} (side-faces side)
        start (port from from-face from-t)
        end (port to to-face to-t)
        bb (or (geom/union [(:rect from) (:rect to)]) (:rect from))
        pad (+ m/lane-gap (* i m/lane-gap))]
    (case side
      :east (let [track (+ (geom/right bb) pad)]
              (collapse [start [track (second start)] [track (second end)] end]))
      :west (let [track (- (:x bb) pad)]
              (collapse [start [track (second start)] [track (second end)] end]))
      :south (let [track (+ (geom/bottom bb) pad)]
               (collapse [start [(first start) track] [(first end) track] end]))
      :north (let [track (- (:y bb) pad)]
               (collapse [start [(first start) track] [(first end) track] end])))))

(defn- around-bbox [bb start end i lr?]
  (let [pad (+ (* 2 m/lane-gap) (* i m/lane-gap))]
    (if lr?
      (let [track (+ (geom/right bb) pad)]
        (collapse [start [track (second start)] [track (second end)] end]))
      (let [track (+ (geom/bottom bb) pad)]
        (collapse [start [(first start) track] [(first end) track] end])))))

(defn- via-rank [classes rank start end i lr?]
  (when-let [bb (rank-bbox classes rank)]
    (if lr?
      (let [y (clamp (/ (+ (second start) (second end)) 2.0)
                     (:y bb)
                     (geom/bottom bb))
            left (- (:x bb) (+ m/lane-gap (* i m/lane-gap)))
            right (+ (geom/right bb) (+ m/lane-gap (* i m/lane-gap)))]
        [[left y] [right y]])
      (let [x (clamp (/ (+ (first start) (first end)) 2.0)
                     (:x bb)
                     (geom/right bb))
            top (- (:y bb) (+ m/lane-gap (* i m/lane-gap)))
            bot (+ (geom/bottom bb) (+ m/lane-gap (* i m/lane-gap)))]
        [[x top] [x bot]]))))

(defn- through-intermediates [from to start end classes i lr?]
  (let [rf (:rank from 0)
        rt (:rank to 0)
        mids (vec (if (< rf rt)
                    (range (inc rf) rt)
                    (range (dec rf) rt -1)))
        hops (mapcat #(via-rank classes % start end i lr?) mids)]
    (when (seq hops)
      (collapse (concat [start] hops [end])))))

(defn- same-rank-candidates [from to from-t to-t i classes lr?]
  (let [outer (if lr? :east :south)
        inner (if lr? :west :north)
        {:keys [from-face to-face]} (side-faces outer)
        fallback-start (port from from-face from-t)
        fallback-end (port to to-face to-t)
        rank-bb (or (rank-bbox classes (:rank from 0)) (:rect from))]
    (remove nil?
            [(along-stack from to from-t to-t lr?)
             (pair-u from to from-t to-t outer i)
             (pair-u from to from-t to-t inner i)
             (around-bbox rank-bb fallback-start fallback-end i lr?)])))

(defn- attach-face [r [x y]]
  (let [dl (abs (- x (:x r)))
        dr (abs (- x (geom/right r)))
        dt (abs (- y (:y r)))
        db (abs (- y (geom/bottom r)))
        m (min dl dr dt db)]
    (cond
      (= m dl) :left
      (= m dr) :right
      (= m dt) :top
      :else :bottom)))

(defn- slide-to-cone
  "Keep `p` on its face and slide it until the segment to `other` is at least 45° to that face."
  [r p other]
  (let [face (attach-face r p)
        [px py] p
        [ox oy] other
        lo-x (+ (:x r) (* 0.12 (:w r)))
        hi-x (- (geom/right r) (* 0.12 (:w r)))
        lo-y (+ (:y r) (* 0.12 (:h r)))
        hi-y (- (geom/bottom r) (* 0.12 (:h r)))]
    (case face
      (:left :right)
      (let [lim (max 1.0 (abs (- px ox)))
            py' (max lo-y (min hi-y (max (- oy lim) (min (+ oy lim) py))))]
        [px py'])
      (:top :bottom)
      (let [lim (max 1.0 (abs (- py oy)))
            px' (max lo-x (min hi-x (max (- ox lim) (min (+ ox lim) px))))]
        [px' py]))))

(defn- sync-stub
  "Keep the first/last inner bend aligned with the attach point so the
   stub leaves the face instead of reversing along it."
  [pts which p face]
  (let [pts (vec pts)
        n (count pts)
        along-y? (#{:left :right} face)]
    (if (< n 3)
      (if (= :start which)
        (assoc pts 0 p)
        (assoc pts (dec n) p))
      (if (= :start which)
        (let [inner (nth pts 1)
              inner (if along-y?
                      [(first inner) (second p)]
                      [(first p) (second inner)])]
          (-> pts (assoc 0 p) (assoc 1 inner)))
        (let [inner (nth pts (- n 2))
              inner (if along-y?
                      [(first inner) (second p)]
                      [(first p) (second inner)])]
          (-> pts (assoc (dec n) p) (assoc (- n 2) inner)))))))

(defn- constrain-attach [from-r to-r pts]
  (let [pts (vec pts)]
    (if (< (count pts) 2)
      pts
      (let [n (count pts)
            s (slide-to-cone from-r (nth pts 0) (nth pts 1))
            e (slide-to-cone to-r (nth pts (dec n)) (nth pts (- n 2)))]
        (-> pts
            (sync-stub :start s (attach-face from-r s))
            (sync-stub :end e (attach-face to-r e)))))))

(defn- face-along [face p]
  (if (#{:top :bottom} face) (first p) (second p)))

(defn- set-face-along [face p v]
  (if (#{:top :bottom} face) [v (second p)] [(first p) v]))

(defn- face-limits [r face]
  (let [pad 0.12]
    (case face
      (:top :bottom) [(+ (:x r) (* pad (:w r)))
                      (- (geom/right r) (* pad (:w r)))]
      (:left :right) [(+ (:y r) (* pad (:h r)))
                      (- (geom/bottom r) (* pad (:h r)))])))

(defn- spread-group [r face items]
  (if (< (count items) 2)
    items
    (let [[lo hi] (face-limits r face)
          ordered (vec (sort-by #(face-along face (:p %)) items))
          n (count ordered)
          step (/ (- hi lo) (double (inc n)))]
      (map-indexed (fn [k it]
                     (assoc it :p (set-face-along face (:p it)
                                                  (+ lo (* (inc k) step)))))
                   ordered))))

(defn- apply-ports [edges placements]
  (reduce (fn [es pl]
            (let [e (nth es (:i pl))
                  pts (sync-stub (:points e) (:which pl) (:p pl) (:face pl))]
              (assoc es (:i pl) (assoc e :points pts))))
          (vec edges)
          placements))

(defn- separate-ports [scene]
  (let [idx (class-by-id scene)
        edges (vec (:edges scene))
        atts (mapcat (fn [i e]
                       (let [pts (:points e)]
                         (when (next pts)
                           [{:i i :which :start :id (:from e) :p (first pts)}
                            {:i i :which :end :id (:to e) :p (last pts)}])))
                     (range)
                     edges)
        placed (mapcat (fn [[[id face] group]]
                         (let [r (:rect (idx id))]
                           (spread-group r face group)))
                       (->> atts
                            (remove nil?)
                            (map (fn [a]
                                   (assoc a :face (attach-face (:rect (idx (:id a)))
                                                               (:p a)))))
                            (group-by (juxt :id :face))))]
    (assoc scene :edges (apply-ports edges placed))))

(defn- hop-candidates [from to from-t to-t i n classes lr?]
  (let [{:keys [from-face to-face]} (flow-faces from to lr?)
        start (port from from-face from-t)
        end (port to to-face to-t)
        ch (or (channel classes (:rank from 0) (:rank to 0) lr?)
               (let [a (:rect from) b (:rect to)]
                 (if lr?
                   (let [lo (min (geom/right a) (geom/right b))
                         hi (max (:x a) (:x b))]
                     {:lo lo :hi hi :span (- hi lo)})
                   (let [lo (min (geom/bottom a) (geom/bottom b))
                         hi (max (:y a) (:y b))]
                     {:lo lo :hi hi :span (- hi lo)}))))
        primary (through-channel start end ch i n lr?)
        alt (through-channel start end ch (- n i 1) n lr?)
        via (through-intermediates from to start end classes i lr?)]
    (remove nil? [primary via alt (collapse [start end])])))

(defn- port-t [groups sort-key]
  (into {}
        (mapcat (fn [[_ group]]
                  (let [g (vec (sort-by sort-key group))
                        n (count g)]
                    (map-indexed (fn [i e]
                                   [e (/ (double (inc i)) (inc n))])
                                 g)))
                groups)))

(defn route [scene]
  (let [idx (class-by-id scene)
        classes (:classes scene)
        lr? (lr? scene)
        edges (:edges (:diagram scene))
        annotated
        (mapv (fn [e]
                (assoc e :from-n (idx (:from e)) :to-n (idx (:to e))))
              edges)
        from-t (port-t (group-by :from annotated)
                       (fn [e] (if lr?
                                 (geom/cy (:rect (:to-n e)))
                                 (geom/cx (:rect (:to-n e))))))
        to-t (port-t (group-by :to annotated)
                     (fn [e] (if lr?
                               (geom/cy (:rect (:from-n e)))
                               (geom/cx (:rect (:from-n e))))))
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
    (separate-ports
      (assoc scene
        :edges
        (mapv (fn [e]
                (let [from (:from-n e)
                      to (:to-n e)
                      [i n] (hop-index e [0 1])
                      same? (= (:rank from 0) (:rank to 0))
                      raw (if same?
                            (try-paths (same-rank-candidates from to
                                                            (from-t e 0.5)
                                                            (to-t e 0.5)
                                                            i classes lr?)
                                       (:id from) (:id to) classes)
                            (try-paths (hop-candidates from to
                                                       (from-t e 0.5)
                                                       (to-t e 0.5)
                                                       i n classes lr?)
                                       (:id from) (:id to) classes))
                      pts (constrain-attach (:rect from) (:rect to) raw)]
                  (assoc (dissoc e :from-n :to-n)
                    :points (vec pts)
                    :head (head (:kind e)))))
              annotated)))))
