(ns uml-viewer.main.ir-generator
  (:require [uml-viewer.clojure-language.graph-clojure :as clj-graph]
            [uml-viewer.application.ir-generator :as ir-generator])
  (:gen-class))

(defn graph-impl [lang]
  (case (or lang :clojure)
    :clojure clj-graph/impl
    :kotlin (try
              @(requiring-resolve 'uml-viewer.kotlin-language.graph-kotlin/impl)
              (catch ClassNotFoundException e
                (throw (ex-info "Kotlin parsing requires the :kotlin alias (-M:kotlin:ir)."
                                {:lang lang} e))))
    (throw (ex-info "Unsupported source language" {:lang lang}))))

(defn regenerate [policy-path out]
  (let [policy (ir-generator/read-policy policy-path)]
    (ir-generator/generate (graph-impl (:lang policy)) policy-path out)))

(defn -main [& args]
  (let [policy (or (first args) "examples/uml-viewer.policy.edn")
        out (second args)]
    (println "Wrote" (regenerate policy out))))
