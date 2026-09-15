(ns uml-viewer.core
  (:require [uml-viewer.document :as document]
            [uml-viewer.sketch :as sketch]))

(defn start!
  "Launch the viewer. `source-impl` satisfies `LanguageSource`."
  [source-impl & args]
  (let [path (or (first args) "examples/library.edn")
        boot (document/load-path path)]
    (when-let [err (:error boot)]
      (binding [*out* *err*]
        (println "UML viewer:" err)))
    (sketch/start! path source-impl)
    (println "Watching" path)
    (println "Double-click a class for its card. Scroll to pan (Shift-scroll for horizontal). R reloads.")))
