(ns uml-viewer.domain.hierarchy-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.domain.hierarchy :as hierarchy]
            [uml-viewer.domain.policy :as policy]))

(def graph
  {:classes [{:id :ir :name "Ir" :ns "uml-viewer.domain.ir"}
             {:id :source :name "Source" :ns "uml-viewer.source"
              :stereotype :interface}
             {:id :source.clojure :name "SourceClojure"
              :ns "uml-viewer.source.clojure"}
             {:id :layout :name "Layout" :ns "uml-viewer.engine.layout"}
             {:id :quil.core :name "quil.core" :foreign true}
             {:id :javax.swing :name "javax.swing" :foreign true}]
   :edges [{:from :source.clojure :to :source :kind :implements}
           {:from :layout :to :ir :kind :dependency}
           {:from :layout :to :quil.core :kind :dependency}
           {:from :source.clojure :to :javax.swing :kind :dependency}]})

(def policy
  {:title "Demo"
   :hierarchical true
   :foreign [:quil :javax.swing]
   :order [:source :layout :ir]})

(describe "hierarchy"
  (it "collapses leaf edges onto the first namespace segment"
    (let [doc (policy/apply-policy policy graph)
          view (hierarchy/view-at doc [])
          ids (set (map :id (mapcat :classes (:packages view))))
          edges (set (map (juxt :from :to :kind) (:edges view)))]
      (should (:hierarchical doc))
      (should= #{:source :layout :ir} ids)
      (should (contains? edges [:layout :ir :dependency]))
      (should (contains? edges [:layout :quil :dependency]))
      (should (contains? edges [:source :javax.swing :dependency]))
      (should= #{:quil :javax.swing} (set (map :id (:foreign view))))
      (should-be-nil (:out-deps (first (filter #(= :layout (:id %))
                                              (mapcat :classes (:packages view))))))
      (should-be-nil (:out-deps (first (filter #(= :source (:id %))
                                              (mapcat :classes (:packages view))))))
      (should-not (some #(= :source.clojure (% 0)) edges))
      (should-not (some #(and (= :source (first %)) (= :source (second %)))
                        edges))))

  (it "lists nested namespaces as clickable contents of a layer"
    (let [doc (policy/apply-policy policy graph)
          view (hierarchy/view-at doc [])
          source (first (filter #(= :source (:id %))
                                (mapcat :classes (:packages view))))]
      (should (:drill? source))
      (should= #{:source :source.clojure} (set (map :id (:contents source))))
      (should-not (:drill? (first (filter #(= :source (:id %)) (:contents source)))))
      (should (:hide-members source))))

  (it "drills into a namespace and shows the next level"
    (let [doc (policy/apply-policy policy graph)
          view (hierarchy/view-at doc [:source])
          ids (set (map :id (mapcat :classes (:packages view))))
          edges (set (map (juxt :from :to :kind) (:edges view)))]
      (should= #{:source :source.clojure} ids)
      (should (contains? edges [:source.clojure :source :implements]))
      (should-not (contains? ids :layout))))

  (it "keeps Source as an interface on the module box"
    (let [doc (policy/apply-policy policy graph)
          view (hierarchy/view-at doc [:source])
          source (first (filter #(= :source (:id %))
                                (mapcat :classes (:packages view))))]
      (should= :interface (:stereotype source))))

  (it "shows foreign deps as ovals with arrows at the nested level that requires them"
    (let [doc (policy/apply-policy policy graph)
          view (hierarchy/view-at doc [:source])
          edges (set (map (juxt :from :to :kind) (:edges view)))
          impl (first (filter #(= :source.clojure (:id %))
                              (mapcat :classes (:packages view))))]
      (should (contains? edges [:source.clojure :source :implements]))
      (should (contains? edges [:source.clojure :javax.swing :dependency]))
      (should= #{:javax.swing} (set (map :id (:foreign view))))
      (should-be-nil (:out-deps impl))
      (should-not (some #(= :quil (% 1)) edges))))

  (it "rolls a parent layer's crap up from the worst μ+σ descendant"
    (let [g (update graph :classes
                    (fn [cs]
                      (mapv (fn [c]
                              (case (:id c)
                                :layout (assoc c :crap {:mu 2.0 :max 3.0 :sigma 1.0})
                                :source.clojure (assoc c :crap {:mu 8.0 :max 9.0 :sigma 2.0})
                                :ir (assoc c :crap {:mu 1.0 :max 1.0 :sigma 0.0})
                                c))
                            cs)))
          doc (policy/apply-policy policy g)
          view (hierarchy/view-at doc [])
          source (first (filter #(= :source (:id %))
                                (mapcat :classes (:packages view))))
          layout (first (filter #(= :layout (:id %))
                                (mapcat :classes (:packages view))))]
      (should= {:mu 8.0 :max 9.0 :sigma 2.0} (:crap source))
      (should= {:mu 2.0 :max 3.0 :sigma 1.0} (:crap layout))))

  (it "keeps arrows between classes in the same view"
    (let [g {:classes [{:id :engine :name "Engine" :ns "demo.engine"}
                       {:id :engine.layout :name "Layout" :ns "demo.engine.layout"}
                       {:id :engine.route :name "Route" :ns "demo.engine.route"}
                       {:id :domain :name "Domain" :ns "demo.domain"}
                       {:id :domain.ir :name "Ir" :ns "demo.domain.ir"}]
             :edges [{:from :engine.layout :to :engine.route :kind :dependency}
                     {:from :engine.layout :to :domain.ir :kind :dependency}]}
          pol {:title "Demo" :hierarchical true :order [:engine :domain]}
          doc (policy/apply-policy pol g)
          root (hierarchy/view-at doc [])
          inner (hierarchy/view-at doc [:engine])
          layout (first (filter #(= :engine.layout (:id %))
                                (mapcat :classes (:packages inner))))
          root-edges (set (map (juxt :from :to) (:edges root)))
          inner-edges (set (map (juxt :from :to) (:edges inner)))]
      (should (contains? root-edges [:engine :domain]))
      (should (contains? inner-edges [:engine.layout :engine.route]))
      (should= [{:id :domain :name "Domain"}] (:out-deps layout))
      (should-not (contains? inner-edges [:engine.layout :domain])))))

