(ns uml-viewer.main.ir-generator
  (:require [uml-viewer.graph.clojure :as clj-graph]
            [uml-viewer.ir-generator :as ir-generator])
  (:gen-class))

(defn -main [& args]
  (let [policy (or (first args) "examples/uml-viewer.policy.edn")
        out (second args)]
    (println "Wrote" (ir-generator/generate clj-graph/impl policy out))))
