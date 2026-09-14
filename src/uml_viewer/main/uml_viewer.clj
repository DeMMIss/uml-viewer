(ns uml-viewer.main.uml-viewer
  (:require [uml-viewer.core :as core]
            [uml-viewer.source.clojure :as clj-source])
  (:gen-class))

(defn -main [& args]
  (apply core/start! clj-source/impl args))
