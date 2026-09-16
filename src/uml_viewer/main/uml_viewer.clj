(ns uml-viewer.main.uml-viewer
  (:require [uml-viewer.adapters.core :as core]
            [uml-viewer.clojure-language.source-clojure :as clj-source])
  (:gen-class))

(defn -main [& args]
  (apply core/start! clj-source/impl args))
