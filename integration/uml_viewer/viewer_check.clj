(ns uml-viewer.viewer-check
  "Real file/renderer integration. Add --gui to render and capture a desktop window."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [quil.core :as q]
            [uml-viewer.adapters.draw :as draw]
            [uml-viewer.adapters.sketch :as sketch]
            [uml-viewer.adapters.source-window :as source-window]
            [uml-viewer.application.detail :as detail]
            [uml-viewer.application.document :as document]
            [uml-viewer.application.events :as events]
            [uml-viewer.engine.layout :as layout]
            [uml-viewer.kotlin-language.source-kotlin]
            [uml-viewer.main.ir-generator :as generator]
            [uml-viewer.source :as source]))

(defn- check! [ok message]
  (when-not ok (throw (ex-info message {}))))

(defn- regenerate! [state]
  (let [button (layout/regen-button sketch/window-width sketch/window-height)
        started (#'sketch/on-main-press state {:x (inc (:x button)) :y (inc (:y button))})
        deadline (+ (System/nanoTime) 15000000000)]
    (check! (= "Regenerating..." (:mail-status started)) "Regen button did not start local generation")
    (while (and (:regenerating? @sketch/!bridge) (< (System/nanoTime) deadline))
      (Thread/sleep 25))
    (check! (not (:regenerating? @sketch/!bridge)) "Local regeneration timed out")
    (sketch/update-state state)))

(defn- pipeline! []
  (let [path ".uml-viewer/integration.edn"
        _ (generator/regenerate "examples/android.policy.edn" path)
        state (document/load-path path)
        domain (events/drill state :domain)
        c (:class (detail/model (events/card-scene domain) :domain.Outer))
        op (first (filter #(str/starts-with? (:name %) "run(") (:ops c)))
        opened (source/member-source (merge c op))]
    (check! (nil? (:error state)) (str "IR failed to load: " (:error state)))
    (check! (= #{:app :domain :data} (set (map :id (get-in state [:scene :classes]))))
            "Module hierarchy was lost")
    (check! (and c op opened (= (:line op) (:line opened)))
            "IR/card/source navigation lost Kotlin metadata")
    (check! (str/includes? (source-window/source->html "Kotlin" (:body opened) (:line opened) :kotlin)
                          "<a name='here'></a>") "Source highlight was lost")
    (check! (nil? (draw/grade-of c)) "Missing Kotlin metrics received a grade")
    (swap! sketch/!bridge assoc :standalone? true :regenerate generator/regenerate)
    (let [updated (regenerate! state)
          before (slurp path)
          failed (regenerate! (assoc-in updated [:doc :policy-file] ".uml-viewer/nonexistent.policy.edn"))]
      (check! (= "Regenerated from local sources." (:mail-status updated)) "Local regeneration failed")
      (check! (str/starts-with? (:mail-status failed) "Regeneration failed:") "Failure was not reported")
      (check! (= before (slurp path)) "Failed regeneration damaged the existing graph"))
    (println "IR -> hierarchy -> class card -> source integration passed")
    (println "Local Regen and failed-generation preservation passed")
    state))

(defn- gui! [initial]
  (let [finished (promise)
        original-draw draw/draw-state
        frames (atom 0)]
    (with-redefs [draw/draw-state
                  (fn [state]
                    (try
                      (check! (not (:waiting state)) "Standalone viewer waited for an agent")
                      (original-draw (if (> @frames 20) (events/drill state :domain) state))
                      (case (swap! frames inc)
                        20 (q/save-frame ".uml-viewer/integration-root.png")
                        40 (do (q/save-frame ".uml-viewer/integration-domain.png")
                               (deliver finished true))
                        nil)
                      (catch Throwable e (deliver finished e))))]
      (sketch/start! (:path initial) nil false
                     {:standalone? true :regenerate generator/regenerate})
      (let [result (deref finished 30000 :timeout)]
        (when (instance? Throwable result) (throw result))
        (check! (= true result) "Desktop rendering timed out")
        (doseq [path [".uml-viewer/integration-root.png" ".uml-viewer/integration-domain.png"]]
          (check! (> (.length (io/file path)) 1000) (str "Missing capture: " path)))))
    (println "Desktop render passed; captures in .uml-viewer/")))

(defn -main [& args]
  (try
    (let [state (pipeline!)]
      (when (some #{"--gui"} args) (gui! state)))
    (shutdown-agents)
    (System/exit 0)
    (catch Throwable e
      (.printStackTrace e)
      (System/exit 1))))
