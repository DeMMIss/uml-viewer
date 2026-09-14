(ns uml-viewer.core
  (:require [uml-viewer.events :as events]
            [uml-viewer.grok :as grok]
            [uml-viewer.sketch :as sketch])
  (:gen-class))

(defn -main [& args]
  (let [path (or (first args) "examples/library.edn")
        boot (events/load-path path)]
    (when-let [err (:error boot)]
      (binding [*out* *err*]
        (println "UML viewer:" err)))
    (sketch/start! path)
    (grok/open-in-terminal!)
    (println "Watching" path)
    (println "Click a class. Scroll to pan (Shift-scroll for horizontal). R reloads.")))
