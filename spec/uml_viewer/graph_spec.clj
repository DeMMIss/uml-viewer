(ns uml-viewer.graph-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [uml-viewer.graph :as graph]
            [uml-viewer.graph.clojure]))

(defn- spit-ns [dir rel content]
  (let [f (io/file dir rel)]
    (io/make-parents f)
    (spit f content)
    f))

(describe "LanguageGraph"
  (it "throws when no scanner is registered"
    (should-throw (graph/scan-project :cobol "src" {})))

  (it "dispatches clojure by :lang"
    (let [g (graph/scan-project :clojure "src" {:prefix "uml-viewer"})]
      (should (some #(= :source (:id %)) (:classes g)))
      (should (some #(= :interface (:stereotype %))
                    (filter #(= :source (:id %)) (:classes g)))))))

(describe "clojure graph"
  (it "reads requires, protocols, and record implementations from a tree"
    (let [dir (io/file (System/getProperty "java.io.tmpdir")
                       (str "uml-graph-" (System/nanoTime)))]
      (spit-ns dir "demo/a.clj"
               "(ns demo.a
                  (:require [demo.b :as b]
                            [clojure.string :as str]
                            [demo [c :as c]]))
                (defrecord R []
                  b/Q)")
      (spit-ns dir "demo/b.clj"
               "(ns demo.b)
                (defprotocol Q)")
      (spit-ns dir "demo/c.clj"
               "(ns demo.c)")
      (let [g (graph/scan (graph/lookup :clojure) dir {:prefix "demo"})
            by-id (into {} (map (juxt :id identity) (:classes g)))
            edges (set (map (juxt :from :to :kind) (:edges g)))]
        (should= #{:a :b :c} (set (keys by-id)))
        (should= "A" (get-in by-id [:a :name]))
        (should= :interface (:stereotype (by-id :b)))
        (should-be-nil (:stereotype (by-id :a)))
        (should (contains? edges [:a :b :dependency]))
        (should (contains? edges [:a :c :dependency]))
        (should (contains? edges [:a :b :implements]))
        (should-not (some #(= "clojure.string" (str (:to %))) (:edges g))))))

  (it "names nested namespaces like source.clojure"
    (let [dir (io/file (System/getProperty "java.io.tmpdir")
                       (str "uml-graph-nest-" (System/nanoTime)))]
      (spit-ns dir "demo/source/clojure.clj"
               "(ns demo.source.clojure)")
      (let [g (graph/scan (graph/lookup :clojure) dir {:prefix "demo"})
            c (first (:classes g))]
        (should= :source.clojure (:id c))
        (should= "SourceClojure" (:name c)))))

  (it "scans this project for Source and its Clojure impl"
    (let [g (graph/scan-project "src" {:prefix "uml-viewer"})
          by-id (into {} (map (juxt :id identity) (:classes g)))
          edges (set (map (juxt :from :to :kind) (:edges g)))]
      (should= :interface (:stereotype (by-id :source)))
      (should= "SourceClojure" (:name (by-id :source.clojure)))
      (should (contains? edges [:source.clojure :source :implements]))
      (should (contains? edges [:events :compose :dependency]))
      (should-not (some (fn [e]
                          (or (= :quil.core (:to e))
                              (= :clojure.string (:to e))))
                        (:edges g))))))
