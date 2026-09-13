(ns uml-viewer.core
  (:require [uml-viewer.sketch :as sketch])
  (:gen-class))

(defn -main [& args]
  (let [path (or (first args) "examples/library.edn")]
    (sketch/start! path)
    (println "Watching" path)
    (println "Click a class. Drag empty space to pan. R reloads.")))
