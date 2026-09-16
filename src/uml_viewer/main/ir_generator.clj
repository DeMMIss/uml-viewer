(ns uml-viewer.main.ir-generator
  (:require [uml-viewer.clojure-language.graph-clojure :as clj-graph]
            [uml-viewer.application.ir-generator :as ir-generator])
  (:gen-class))

(defn -main [& args]
  (let [policy (or (first args) "examples/uml-viewer.policy.edn")
        out (second args)]
    (println "Wrote" (ir-generator/generate clj-graph/impl policy out))))
