(ns uml-viewer.architecture-spec
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [speclj.core :refer :all]))

(defn- source-files []
  (->> (file-seq (io/file "src"))
       (filter #(re-matches #".*\.clj[cs]?$" (.getName %)))))

(defn- read-ns-form [file]
  (read-string {:read-cond :allow :features #{:clj}} (slurp file)))

(defn- ns-name-of [ns-form] (second ns-form))

(defn- ns-clauses [ns-form]
  (->> ns-form (drop 2)
       (filter seq)
       (filter #(#{:require :import} (first %)))))

(defn- required-lib [spec]
  (cond
    (symbol? spec) spec
    (vector? spec) (first spec)
    :else spec))

(defn- required-libs [ns-form]
  (->> (ns-clauses ns-form)
       (mapcat rest)
       (map required-lib)))

(defn- quil-lib? [sym]
  (let [s (str sym)]
    (or (= s "quil.core")
        (str/starts-with? s "quil."))))

(defn- quil-adapter? [ns-name]
  (contains? #{"uml-viewer.draw" "uml-viewer.sketch" "uml-viewer.core"}
             (str ns-name)))

(defn- logic-ns? [ns-name]
  (not (quil-adapter? ns-name)))

(defn- violations [from-pred to-pred]
  (for [file (source-files)
        :let [ns-form (read-ns-form file)
              ns-name (ns-name-of ns-form)]
        :when (from-pred ns-name)
        lib (required-libs ns-form)
        :when (to-pred lib)]
    {:ns ns-name :requires lib :file (str file)}))

(describe "architecture"
  (it "keeps layout, IR, and hit-testing free of Quil"
    (should= [] (violations logic-ns? quil-lib?)))

  (it "confines Processing to draw and sketch"
    (let [owners (set (map (comp str :ns) (violations (constantly true) quil-lib?)))]
      (should= #{"uml-viewer.draw" "uml-viewer.sketch"} owners)))

  (it "keeps source lookup and grok spawn free of Swing and Quil"
    (should= [] (violations #(contains? #{"uml-viewer.source"
                                         "uml-viewer.source.clojure"
                                         "uml-viewer.grok"} (str %))
                            #(or (quil-lib? %)
                                 (#{'javax.swing 'java.awt} %))))))
