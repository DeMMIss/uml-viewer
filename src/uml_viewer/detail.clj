(ns uml-viewer.detail
  (:require [uml-viewer.hit :as hit]
            [uml-viewer.layout :as layout]
            [uml-viewer.overlay :as overlay]))

(def width 640)
(def height 700)
(def pad 16)
(def col-gap 10)

(def columns
  [{:id :crap :label "Crap" :key :crap-s :w 56}
   {:id :cc :label "CC" :key :cc-s :w 32}
   {:id :cov :label "Cov" :key :cov-s :w 44}
   {:id :killed :label "killed" :key :killed-s :w 52}
   {:id :survived :label "survived" :key :survived-s :w 68}])

(def ^:private rel-phrases
  {:inheritance ["extends" "extended by"]
   :implements ["implements" "implemented by"]
   :association ["associates with" "associated from"]
   :dependency ["depends on" "used by"]
   :aggregation ["aggregates" "aggregated by"]
   :composition ["composes" "composed in"]})

(defn- rel-phrase [kind outgoing?]
  (let [[out in] (get rel-phrases kind ["to" "from"])]
    (if outgoing? out in)))

(defn model
  "Class card for the detail window, or nil if `id` is unknown."
  [scene id]
  (when-let [c (hit/class-by-id scene id)]
    {:class c
     :ns (overlay/class-namespace c)
     :package (hit/package-by-id scene (:package c))
     :title (get-in scene [:diagram :title])
     :rels (mapv (fn [e]
                   (let [out? (= id (:from e))
                         oid (if out? (:to e) (:from e))
                         other (hit/class-by-id scene oid)]
                     {:id oid
                      :name (or (:name other) (name oid))
                      :kind (:kind e)
                      :label (:label e)
                      :outgoing? out?
                      :phrase (rel-phrase (:kind e) out?)}))
                 (hit/connected-edges scene id))}))

(defn column-layout
  "Columns from the right edge. Each has :left and :right."
  []
  (loop [cols (reverse columns) x (- width pad) acc ()]
    (if (empty? cols)
      (vec acc)
      (let [c (first cols)
            right x
            left (- x (:w c))]
        (recur (rest cols) (- left col-gap)
               (cons (assoc c :left left :right right) acc))))))

(defn- crap-mu [crap]
  (cond
    (nil? crap) nil
    (number? crap) (double crap)
    :else (some-> (:mu crap) double)))

(defn- sum-key [xs k]
  (when (some #(some? (get % k)) xs)
    (long (reduce + 0 (map #(or (get % k) 0) xs)))))

(defn- format-num [n]
  (when n
    (format "%.1f" (double n))))

(defn- format-cells [{:keys [crap-mu cc coverage killed survived class-row?]}]
  {:crap-s (when crap-mu
             (if class-row?
               (str (format-num crap-mu) "μ")
               (format-num crap-mu)))
   :crap-n crap-mu
   :cc-s (when (and cc (not class-row?)) (str (long cc)))
   :cov-s (layout/format-coverage coverage)
   :coverage coverage
   :killed-s (when killed (str (long killed)))
   :survived-s (when survived (str (long survived)))
   :killed killed
   :survived survived})

(defn- class-metrics [c]
  (let [ops (:ops c)]
    {:class-row? true
     :crap-mu (crap-mu (:crap c))
     :coverage (:coverage c)
     :killed (or (:killed c) (sum-key ops :killed))
     :survived (or (:survived c) (sum-key ops :survived))}))

(defn- op-metrics [op]
  {:crap-mu (crap-mu (:crap op))
   :cc (:cc op)
   :coverage (:coverage op)
   :killed (:killed op)
   :survived (:survived op)})

(defn- emit [acc kind text extra]
  (let [{:keys [rows y]} acc
        h (or (:h extra) layout/line-h)]
    {:rows (conj rows (merge {:kind kind :text text :y y :h h} extra))
     :y (+ y h)}))

(defn- heading [acc label]
  (-> acc
      (update :y + 12)
      (emit :heading label {})))

(defn- emit-table [acc c]
  (let [acc (update acc :y + 12)
        acc (emit acc :col-header "" {})
        acc (emit acc :stats (:name c) (format-cells (class-metrics c)))]
    (reduce (fn [acc op]
              (let [label (str (if (:private op) "- " "+ ") (:text op))]
                (emit acc :stats label
                      (assoc (format-cells (op-metrics op))
                        :private (boolean (:private op))
                        :op-name (:name op)))))
            acc
            (:ops c))))

(defn rows
  "Laid-out lines for `model`. Y is in content space (scroll separately)."
  [model]
  (when model
    (let [c (:class model)
          pack (str "package  "
                    (or (:label (:package model))
                        (some-> (:package c) name)))
          acc {:rows [] :y pad}
          acc (emit acc :name (:name c) {})
          acc (if-let [st (:stereotype c)]
                (emit acc :muted (str "«" (name st) "»") {})
                acc)
          acc (emit acc :muted pack {})
          acc (if-let [t (:title model)]
                (emit acc :muted t {})
                acc)
          acc (if-let [s (layout/format-crap (:crap c))]
                (emit acc :crap s {})
                acc)
          acc (if (or (seq (:ops c))
                      (crap-mu (:crap c))
                      (:coverage c)
                      (:cc c)
                      (:killed c)
                      (:survived c))
                (emit-table acc c)
                acc)
          acc (if (seq (:fields c))
                (reduce (fn [acc f]
                          (emit acc :field (:text f) {}))
                        (heading acc "Fields")
                        (:fields c))
                acc)
          acc (if (seq (:rels model))
                (reduce (fn [acc r]
                          (let [text (str (:phrase r) "  " (:name r)
                                          (when (:label r)
                                            (str "  «" (:label r) "»")))]
                            (emit acc :rel text {:id (:id r)})))
                        (heading acc "Relationships")
                        (:rels model))
                acc)]
      (:rows acc))))

(defn content-h [rows]
  (if (seq rows)
    (+ pad (:y (last rows)) (:h (last rows)))
    (* 2 pad)))

(defn rel-at
  "Class id of the relationship row under content-y, or nil."
  [rows y]
  (some (fn [row]
          (when (and (= :rel (:kind row))
                     (<= (:y row) y (+ (:y row) (:h row) -1)))
            (:id row)))
        rows))

(defn member-at
  "Op name of the member row under content-y, or nil."
  [rows y]
  (some (fn [row]
          (when (and (:op-name row)
                     (<= (:y row) y (+ (:y row) (:h row) -1)))
            (:op-name row)))
        rows))
