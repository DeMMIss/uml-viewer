(ns uml-viewer.adapters.core
  (:require [uml-viewer.adapters.sketch :as sketch]))

(defn parse-args
  "EDN path and flags. `--restart` skips spawning a new agent."
  [args]
  (let [args (keep identity args)
        restart? (boolean (some #{"--restart"} args))
        path (->> args (remove #{"--restart"}) first)]
    {:restart? restart?
     :path (or path "examples/library.edn")}))

(defn start!
  "Launch the viewer. `source-impl` satisfies `LanguageSource`."
  [source-impl & args]
  (let [{:keys [path restart?]} (parse-args args)]
    (sketch/start! path source-impl restart?)
    (println "Watching" path)
    (println "Double-click a class for its card. Scroll to pan (Shift-scroll for horizontal). R reloads.")))
