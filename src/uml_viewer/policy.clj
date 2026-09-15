(ns uml-viewer.policy
  (:require [clojure.string :as str]))

(defn- as-id [x]
  (keyword (name x)))

(defn- index-classes [graph]
  (into {} (map (juxt :id identity) (:classes graph))))

(defn- package-nses [pkg]
  (mapv as-id (:nses pkg)))

(defn assigned
  "Class ids listed in any package of `policy`."
  [policy]
  (set (mapcat package-nses (:packages policy))))

(defn- foreign-prefixes [policy]
  (mapv as-id (or (:foreign policy) [])))

(defn- matches-prefix? [id prefix]
  (let [s (name id)
        p (name prefix)]
    (or (= s p) (str/starts-with? s (str p ".")))))

(defn- collapse-id [id prefixes]
  (->> prefixes
       (filter #(matches-prefix? id %))
       (sort-by (comp - count name))
       first))

(defn unassigned
  "Scanned project classes that no package lists. Foreign classes are omitted."
  [policy graph]
  (->> (:classes graph)
       (remove :foreign)
       (remove #(contains? (assigned policy) (:id %)))
       (sort-by (comp name :id))
       vec))

(defn- lookup-class [idx id]
  (get idx (as-id id)))

(defn- kind-rank [k]
  (get {:implements 4 :inheritance 4 :composition 3 :aggregation 2
        :association 1 :dependency 0} k 0))

(defn- merge-edges [edges]
  (->> edges
       (group-by (juxt :from :to))
       vals
       (mapv (fn [es] (apply max-key #(kind-rank (:kind %)) es)))))

(defn collapse-graph
  "Rewrite foreign classes to policy `:foreign` prefixes. Unlisted externals drop."
  [policy graph]
  (let [prefixes (foreign-prefixes policy)
        remap (into {}
                    (keep (fn [c]
                            (when (:foreign c)
                              (when-let [p (collapse-id (:id c) prefixes)]
                                [(:id c) p])))
                          (:classes graph)))
        project (vec (remove :foreign (:classes graph)))
        foreigns (->> (vals remap)
                      distinct
                      (sort-by name)
                      (mapv (fn [id] {:id id :name (name id) :foreign true})))
        classes (into project foreigns)
        ids (set (map :id classes))
        edges (->> (:edges graph)
                   (map (fn [e]
                          (assoc e
                            :from (get remap (:from e) (:from e))
                            :to (get remap (:to e) (:to e)))))
                   (filter #(and (ids (:from %)) (ids (:to %))))
                   (remove #(= (:from %) (:to %)))
                   merge-edges)]
    {:classes classes :edges edges}))

(defn- apply-kinds [edges diagram]
  (let [kinds (or (:edge-kinds diagram) {})
        omit (set (map (fn [p] (mapv as-id p)) (or (:omit-edges diagram) [])))]
    (->> edges
         (remove #(contains? omit [(:from %) (:to %)]))
         (mapv (fn [e]
                 (if-let [k (get kinds [(:from e) (:to e)])]
                   (assoc e :kind k)
                   e))))))

(defn- edges-among
  ([graph ids diagram]
   (edges-among graph ids diagram nil))
  ([graph ids diagram home]
   (let [idset (set ids)
         homes (set home)]
     (-> (->> (:edges graph)
              (filter #(and (idset (:from %)) (idset (:to %))))
              (filter #(or (nil? home)
                           (homes (:from %))
                           (homes (:to %))
                           (= :implements (:kind %)))))
         merge-edges
         (apply-kinds diagram)))))

(defn- ir-class [c hide?]
  (cond-> {:id (:id c) :name (:name c)}
    (:ns c) (assoc :ns (:ns c))
    (:stereotype c) (assoc :stereotype (:stereotype c))
    (:foreign c) (assoc :shape :oval)
    (and hide? (not (:foreign c))) (assoc :hide-members true)))

(defn- visible-class [idx id hide?]
  (when-let [c (lookup-class idx id)]
    (ir-class c hide?)))

(defn- lookup [graph id]
  (first (filter #(= id (:id %)) (:classes graph))))

(defn- foreigns-from [graph from-ids]
  (let [from (set from-ids)
        wanted (set (for [e (:edges graph)
                          :when (from (:from e))
                          :let [c (lookup graph (:to e))]
                          :when (:foreign c)]
                      (:id c)))]
    (->> (:classes graph)
         (filter :foreign)
         (filter #(wanted (:id %)))
         (sort-by (comp name :id))
         vec)))

(defn- neighbors [graph home-ids]
  (let [homes (set home-ids)]
    (vec (distinct
           (for [e (:edges graph)
                 :when (homes (:from e))
                 :when (not (homes (:to e)))]
             (:to e))))))

(defn- overview-diagram [policy graph diagram]
  (let [idx (index-classes graph)
        hide? (boolean (:hide-members diagram))
        pkgs (mapv (fn [p]
                     {:id (as-id (:id p))
                      :label (:label p)
                      :classes (into []
                                     (keep #(visible-class idx % hide?)
                                           (package-nses p)))})
                   (:packages policy))
        extra (unassigned policy graph)
        pkgs (cond-> pkgs
               (seq extra)
               (conj {:id :unassigned
                      :label "Unassigned"
                      :classes (mapv #(ir-class % hide?) extra)}))
        pkg-ids (mapcat (fn [p] (map :id (:classes p))) pkgs)
        foreign (foreigns-from graph pkg-ids)
        ids (concat pkg-ids (map :id foreign))]
    (cond-> {:title (:title diagram)
             :direction (keyword (or (:direction diagram) :tb))
             :packages pkgs
             :edges (edges-among graph ids diagram)}
      (seq foreign) (assoc :foreign (mapv #(ir-class % false) foreign)))))

(defn- find-package [policy id]
  (let [want (as-id id)]
    (first (filter #(= want (as-id (:id %))) (:packages policy)))))

(defn- package-diagram [policy graph diagram]
  (let [pkg (find-package policy (:package diagram))]
    (when-not pkg
      (throw (ex-info (str "diagram package not in policy: " (:package diagram))
                      {:diagram diagram})))
    (let [idx (index-classes graph)
          home (package-nses pkg)
          nbrs (neighbors graph home)
          stubs (remove #(:foreign (lookup graph %)) nbrs)
          foreign (foreigns-from graph home)
          hide-home? (boolean (:hide-members diagram))
          classes (into []
                        (concat
                          (keep #(visible-class idx % hide-home?) home)
                          (keep #(visible-class idx % true) stubs)))
          ids (concat (map :id classes) (map :id foreign))]
      (cond-> {:title (:title diagram)
               :direction (keyword (or (:direction diagram) :lr))
               :packages [{:id (as-id (:id pkg))
                           :label (:label pkg)
                           :classes classes}]
               :edges (edges-among graph ids diagram home)}
        (seq foreign) (assoc :foreign (mapv #(ir-class % false) foreign))))))

(defn apply-policy
  "Turn a scanned graph and a policy into an IR document."
  [policy graph]
  (let [graph (collapse-graph policy graph)]
    {:title (or (:title policy) "UML")
     :diagrams (mapv (fn [d]
                       (if (= :overview (:view d))
                         (overview-diagram policy graph d)
                         (package-diagram policy graph d)))
                     (:diagrams policy))}))
