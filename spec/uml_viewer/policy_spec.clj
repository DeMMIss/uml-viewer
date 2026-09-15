(ns uml-viewer.policy-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.policy :as policy]))

(def graph
  {:classes [{:id :ir :name "Ir" :ns "uml-viewer.ir"}
             {:id :layout :name "Layout" :ns "uml-viewer.layout"}
             {:id :events :name "Events" :ns "uml-viewer.events"}
             {:id :compose :name "Compose" :ns "uml-viewer.compose"}
             {:id :source :name "Source" :ns "uml-viewer.source"
              :stereotype :interface}
             {:id :source.clojure :name "SourceClojure"
              :ns "uml-viewer.source.clojure"}
             {:id :orphan :name "Orphan" :ns "uml-viewer.orphan"}
             {:id :quil.core :name "quil.core" :ns "quil.core" :foreign true}
             {:id :quil.middleware :name "quil.middleware" :ns "quil.middleware"
              :foreign true}
             {:id :clojure.string :name "clojure.string" :ns "clojure.string"
              :foreign true}]
   :edges [{:from :layout :to :ir :kind :dependency}
           {:from :layout :to :compose :kind :dependency}
           {:from :compose :to :ir :kind :dependency}
           {:from :events :to :layout :kind :dependency}
           {:from :source.clojure :to :source :kind :dependency}
           {:from :source.clojure :to :source :kind :implements}
           {:from :layout :to :quil.core :kind :dependency}
           {:from :layout :to :quil.middleware :kind :dependency}
           {:from :events :to :clojure.string :kind :dependency}]})

(def policy
  {:title "Demo"
   :foreign [:quil]
   :packages
   [{:id :domain :label "Domain" :nses [:ir :source :source.clojure]}
    {:id :engine :label "Engine" :nses [:layout]}
    {:id :app :label "Application" :nses [:events]}]
   :diagrams
   [{:title "Layers" :view :overview :hide-members true :direction :tb}
    {:title "Engine" :package :engine
     :edge-kinds {[:layout :ir] :association}}]})

(describe "policy"
  (it "lists scanned classes that no package claims"
    (let [extra (policy/unassigned policy graph)]
      (should= [:compose :orphan] (mapv :id extra))))

  (it "builds an overview with home packages and Unassigned"
    (let [doc (policy/apply-policy policy graph)
          layers (first (:diagrams doc))
          ids (fn [pkg] (map :id (:classes pkg)))
          by-id (into {} (map (juxt :id identity) (:packages layers)))]
      (should= "Demo" (:title doc))
      (should= :tb (:direction layers))
      (should= [:ir :source :source.clojure] (ids (by-id :domain)))
      (should (every? :hide-members (mapcat :classes (:packages layers))))
      (should= :interface (get-in by-id [:domain :classes 1 :stereotype]))
      (should= [:compose :orphan] (ids (by-id :unassigned)))
      (should (some #(= {:from :source.clojure :to :source :kind :implements} %)
                    (:edges layers)))
      (should-not (some #(= :dependency (:kind %))
                        (filter #(= :source.clojure (:from %))
                                (:edges layers))))))

  (it "adds one-hop stubs on a package diagram and honors edge-kinds"
    (let [doc (policy/apply-policy policy graph)
          engine (second (:diagrams doc))
          classes (:classes (first (:packages engine)))
          by-id (into {} (map (juxt :id identity) classes))]
      (should= :lr (:direction engine))
      (should= :layout (first (map :id classes)))
      (should (contains? by-id :ir))
      (should-not (contains? by-id :events))
      (should-be-nil (:hide-members (by-id :layout)))
      (should (:hide-members (by-id :ir)))
      (should= :association
               (:kind (first (filter #(= :ir (:to %)) (:edges engine)))))
      (should (contains? by-id :compose))
      (should-not (some #(and (= :compose (:from %)) (= :ir (:to %)))
                        (:edges engine)))))

  (it "collapses listed foreign prefixes and drops the rest"
    (let [g (policy/collapse-graph policy graph)
          by-id (into {} (map (juxt :id identity) (:classes g)))
          edges (set (map (juxt :from :to) (:edges g)))]
      (should (:foreign (by-id :quil)))
      (should= "quil" (:name (by-id :quil)))
      (should-not (contains? by-id :quil.core))
      (should-not (contains? by-id :clojure.string))
      (should (contains? edges [:layout :quil]))
      (should-not (some #(= :clojure.string (second %)) edges))))

  (it "places foreign ovals outside packages on overview and package diagrams"
    (let [doc (policy/apply-policy policy graph)
          layers (first (:diagrams doc))
          engine (second (:diagrams doc))]
      (should= [{:id :quil :name "quil" :shape :oval}] (:foreign layers))
      (should (some #(and (= :layout (:from %)) (= :quil (:to %))) (:edges layers)))
      (should-not (some #(= :quil (:id %))
                        (mapcat :classes (:packages layers))))
      (should= [{:id :quil :name "quil" :shape :oval}] (:foreign engine))
      (should-not (some #(= :quil (:id %))
                        (:classes (first (:packages engine)))))))

  (it "throws when a diagram names a missing package"
    (should-throw
      (policy/apply-policy
        (assoc policy :diagrams [{:title "X" :package :nope}])
        graph))))
