(ns uml-viewer.core
  (:require [uml-viewer.sketch :as sketch])
  (:gen-class))

(defn -main [& args]
  (let [path (or (first args) "examples/library.edn")]
    (sketch/start! path)
    (println "Watching" path)
    (println "Click a class. Scroll to pan (Shift-scroll for horizontal). R reloads.")))
