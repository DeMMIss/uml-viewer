(ns uml-viewer.ir-generator-spec
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [speclj.core :refer :all]
            [uml-viewer.graph :as graph]
            [uml-viewer.graph.clojure]
            [uml-viewer.ir :as ir]
            [uml-viewer.ir-generator :as ir-generator]
            [uml-viewer.policy :as policy]))

(describe "ir-generator emit"
  (it "writes a do-not-edit header and readable EDN"
    (let [s (ir-generator/emit {:title "T" :diagrams []})
          doc (edn/read-string s)]
      (should (str/starts-with? s ";; Generated"))
      (should= "T" (:title doc))
      (should= [] (:diagrams doc)))))

(describe "this project's policy"
  (it "assigns every scanned namespace and keeps Source as an interface"
    (let [policy (ir-generator/read-policy "examples/uml-viewer.policy.edn")
          graph (graph/scan-project (:src policy) {:prefix (:prefix policy)})
          extra (policy/unassigned policy graph)
          doc (policy/apply-policy policy graph)
          layers (->> doc :diagrams (filter #(= "Layers" (:title %))) first)
          lang (->> doc :diagrams (filter #(= "LanguageImplementation" (:title %))) first)
          factory (->> doc :diagrams (filter #(= "SourceFactory" (:title %))) first)
          by-pkg (into {} (map (juxt :id identity) (:packages layers)))
          ids (fn [pkg] (set (map :id (:classes (by-pkg pkg)))))
          source (first (filter #(= :source (:id %))
                                (mapcat :classes (:packages lang))))
          impl-edge (first (filter #(and (= :source.clojure (:from %))
                                         (= :source (:to %)))
                                   (:edges factory)))]
      (should= [] extra)
      (should= :interface (:stereotype source))
      (should-not (contains? (ids :domain) :source))
      (should-not (contains? (ids :domain) :graph))
      (should (contains? (ids :language-implementation) :source))
      (should (contains? (ids :language-implementation) :graph))
      (should-not (contains? (ids :main) :source.clojure))
      (should-not (contains? (ids :main) :graph.clojure))
      (should (contains? (ids :main) :main.uml-viewer))
      (should (contains? (ids :main) :main.ir-generator))
      (should (contains? (ids :source-factory) :source.clojure))
      (should (contains? (ids :source-factory) :graph.clojure))
      (should= :implements (:kind impl-edge))
      (should= ["Layers" "Main" "Adapters" "Application" "Engine"
                "SourceFactory" "LanguageImplementation" "Domain"]
               (map :title (:diagrams doc)))
      (doseq [d (:diagrams (edn/read-string (ir-generator/emit doc)))]
        (ir/normalize d)))))

(describe "ir-generator document"
  (it "scans with the given graph implementation"
    (let [impl (reify graph/LanguageGraph
                 (scan [_ root opts]
                   (should= "src" root)
                   (should= {:prefix "uml-viewer"} opts)
                   {:classes [{:id :a :name "A" :ns "demo.a"}]
                    :edges []}))
          policy {:title "T"
                  :packages [{:id :p :label "P" :nses [:a]}]
                  :diagrams [{:title "P" :package :p}]}
          doc (ir-generator/document impl policy)]
      (should= "T" (:title doc))
      (should= ["P"] (map :title (:diagrams doc))))))
