(ns uml-viewer.main.ir-generator
  (:require [uml-viewer.clojure-language.graph-clojure :as clj-graph]
            [uml-viewer.application.ir-generator :as ir-generator])
  (:gen-class))

(defn graph-impl [lang]
  (case (or lang :clojure)
    :clojure clj-graph/impl
    :kotlin (try
              @(requiring-resolve 'uml-viewer.kotlin-language.graph-kotlin/impl)
              (catch Exception e
                (if-let [missing (some #(when (instance? ClassNotFoundException %) %)
                                       (take-while some? (iterate ex-cause e)))]
                  (throw (ex-info (str "Kotlin parsing requires the :kotlin alias (-M:kotlin:ir) "
                                       "and a working compiler classpath. Missing class: " (.getMessage missing))
                                  {:lang lang :missing-class (.getMessage missing)}))
                  (throw e))))
    (throw (ex-info "Unsupported source language" {:lang lang}))))

(defn regenerate [policy-path out]
  (let [policy (ir-generator/read-policy policy-path)]
    (ir-generator/generate (graph-impl (:lang policy)) policy-path out)))

(defn -main [& args]
  (let [policy (or (first args) "examples/uml-viewer.policy.edn")
        out (second args)]
    (println "Wrote" (regenerate policy out))))
