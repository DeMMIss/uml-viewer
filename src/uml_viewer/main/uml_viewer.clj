(ns uml-viewer.main.uml-viewer
  (:require [uml-viewer.adapters.core :as core]
            [uml-viewer.clojure-language.source-clojure :as clj-source]
            [uml-viewer.kotlin-language.source-kotlin]
            [uml-viewer.main.ir-generator :as ir-generator])
  (:gen-class))

(defn -main [& args]
  (core/launch! clj-source/impl ir-generator/regenerate args))
