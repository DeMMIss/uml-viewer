(ns uml-viewer.policy)

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

(defn unassigned
  "Scanned classes that no package lists."
  [policy graph]
  (->> (:classes graph)
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
    (:stereotype c) (assoc :stereotype (:stereotype c))
    hide? (assoc :hide-members true)))

(defn- visible-class [idx id hide?]
  (when-let [c (lookup-class idx id)]
    (ir-class c hide?)))

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
        ids (mapcat (fn [p] (map :id (:classes p))) pkgs)]
    {:title (:title diagram)
     :direction (keyword (or (:direction diagram) :tb))
     :packages pkgs
     :edges (edges-among graph ids diagram)}))

(defn- neighbors [graph home-ids]
  (let [homes (set home-ids)]
    (vec (distinct
           (for [e (:edges graph)
                 :when (homes (:from e))
                 :when (not (homes (:to e)))]
             (:to e))))))

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
          stubs (neighbors graph home)
          hide-home? (boolean (:hide-members diagram))
          classes (into []
                        (concat
                          (keep #(visible-class idx % hide-home?) home)
                          (keep #(visible-class idx % true) stubs)))
          ids (map :id classes)]
      {:title (:title diagram)
       :direction (keyword (or (:direction diagram) :lr))
       :packages [{:id (as-id (:id pkg))
                   :label (:label pkg)
                   :classes classes}]
       :edges (edges-among graph ids diagram home)})))

(defn apply-policy
  "Turn a scanned graph and a policy into an IR document."
  [policy graph]
  {:title (or (:title policy) "UML")
   :diagrams (mapv (fn [d]
                     (if (= :overview (:view d))
                       (overview-diagram policy graph d)
                       (package-diagram policy graph d)))
                   (:diagrams policy))})
