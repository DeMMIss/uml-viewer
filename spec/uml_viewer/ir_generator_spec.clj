(ns uml-viewer.ir-generator-spec
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [speclj.core :refer :all]
            [uml-viewer.graph :as graph]
            [uml-viewer.graph.clojure]
            [uml-viewer.ir :as ir]
            [uml-viewer.ir-generator :as ir-generator]
            [uml-viewer.policy :as policy]))

(defn- stub-scan [seen classes]
  (reify graph/LanguageGraph
    (scan [_ root opts]
      (reset! seen {:root root :opts opts})
      {:classes classes :edges []})))

(defn- edn-file [m]
  (doto (java.io.File/createTempFile "uml-ir" ".edn")
    (spit (pr-str m))))

(defn- demo-policy [m]
  (merge {:title "T"
          :packages [{:id :p :label "P" :nses [:a]}]
          :diagrams [{:title "P" :package :p}]}
         m))

(def demo-a {:id :a :name "A" :ns "demo.a"})
(def demo-b {:id :b :name "B" :ns "demo.b"})
(def demo-c {:id :c :name "C" :ns "demo.c"})

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
      (should= ["P"] (map :title (:diagrams doc)))))

  (it "uses policy src and prefix when present"
    (let [impl (reify graph/LanguageGraph
                 (scan [_ root opts]
                   (should= "trees" root)
                   (should= {:prefix "demo"} opts)
                   {:classes [demo-a]
                    :edges []}))
          doc (ir-generator/document impl (demo-policy {:src "trees"
                                                       :prefix "demo"}))]
      (should= "T" (:title doc)))))

(describe "ir-generator generate"
  (it "writes the document to the given path"
    (let [seen (atom nil)
          policy-f (edn-file (demo-policy {}))
          out-f (java.io.File/createTempFile "uml-out" ".edn")
          out (ir-generator/generate (stub-scan seen [demo-a])
                                     (.getPath policy-f)
                                     (.getPath out-f))
          body (slurp out-f)
          doc (edn/read-string body)]
      (should= (.getPath out-f) out)
      (should= "src" (:root @seen))
      (should= {:prefix "uml-viewer"} (:opts @seen))
      (should (str/starts-with? body ";; Generated"))
      (should= "T" (:title doc))
      (should= ["P"] (map :title (:diagrams doc)))))

  (it "uses policy :out when no path is given"
    (let [out-f (java.io.File/createTempFile "uml-out" ".edn")
          policy-f (edn-file (demo-policy {:out (.getPath out-f)}))
          out (ir-generator/generate (stub-scan (atom nil) [demo-a])
                                     (.getPath policy-f))]
      (should= (.getPath out-f) out)
      (should= "T" (:title (edn/read-string (slurp out-f))))))

  (it "prefers the given path over policy :out"
    (let [policy-out (java.io.File/createTempFile "uml-policy-out" ".edn")
          out-f (java.io.File/createTempFile "uml-out" ".edn")
          policy-f (edn-file (demo-policy {:out (.getPath policy-out)}))
          out (ir-generator/generate (stub-scan (atom nil) [demo-a])
                                     (.getPath policy-f)
                                     (.getPath out-f))]
      (should= (.getPath out-f) out)
      (should= "T" (:title (edn/read-string (slurp out-f))))))

  (it "scans with policy src and prefix"
    (let [seen (atom nil)
          out-f (java.io.File/createTempFile "uml-out" ".edn")
          policy-f (edn-file (demo-policy {:src "trees" :prefix "demo"}))]
      (ir-generator/generate (stub-scan seen [demo-a])
                             (.getPath policy-f)
                             (.getPath out-f))
      (should= "trees" (:root @seen))
      (should= {:prefix "demo"} (:opts @seen))))

  (it "defaults src, prefix, and out when the policy omits them"
    (let [seen (atom nil)
          written (atom nil)
          policy-f (edn-file (demo-policy {}))]
      (with-redefs [spit (fn [path s] (reset! written [path s]))]
        (let [out (ir-generator/generate (stub-scan seen [demo-a])
                                         (.getPath policy-f))]
          (should= "examples/uml-viewer.edn" out)
          (should= "examples/uml-viewer.edn" (first @written))
          (should (str/starts-with? (second @written) ";; Generated"))
          (should= "src" (:root @seen))
          (should= {:prefix "uml-viewer"} (:opts @seen))))))

  (it "prints unassigned namespaces to stderr"
    (let [out-f (java.io.File/createTempFile "uml-out" ".edn")
          policy-f (edn-file (demo-policy {}))
          err (java.io.StringWriter.)
          out (java.io.StringWriter.)]
      (binding [*err* err
                *out* out]
        (ir-generator/generate (stub-scan (atom nil) [demo-a demo-b demo-c])
                               (.getPath policy-f)
                               (.getPath out-f)))
      (should= "" (str out))
      (should= "Unassigned namespaces: demo.b, demo.c\n" (str err))))

  (it "does not warn when every namespace is assigned"
    (let [out-f (java.io.File/createTempFile "uml-out" ".edn")
          policy-f (edn-file (demo-policy {}))
          err (java.io.StringWriter.)
          out (java.io.StringWriter.)]
      (binding [*err* err
                *out* out]
        (ir-generator/generate (stub-scan (atom nil) [demo-a])
                               (.getPath policy-f)
                               (.getPath out-f)))
      (should= "" (str err))
      (should= "" (str out)))))
