(ns uml-viewer.viewer-check
  "Real file/renderer integration. Add --gui to render and capture a desktop window."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [quil.core :as q]
            [uml-viewer.adapters.draw :as draw]
            [uml-viewer.adapters.sketch :as sketch]
            [uml-viewer.adapters.source-window :as source-window]
            [uml-viewer.application.detail :as detail]
            [uml-viewer.application.document :as document]
            [uml-viewer.application.events :as events]
            [uml-viewer.domain.hierarchy :as hierarchy]
            [uml-viewer.engine.layout :as layout]
            [uml-viewer.engine.hit :as hit]
            [uml-viewer.kotlin-language.source-kotlin]
            [uml-viewer.main.ir-generator :as generator]
            [uml-viewer.source :as source])
  (:import [java.nio.file Files]))

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

(defn- relocation! []
  (let [dir (.toFile (Files/createTempDirectory (.toPath (io/file ".uml-viewer")) "relocation-"
                                               (make-array java.nio.file.attribute.FileAttribute 0)))
        original (io/file dir "original")
        moved (io/file dir "moved")
        policy (io/file original "model.policy.edn")
        graph (io/file original "model.edn")
        moved-graph (io/file moved "model.edn")
        moved-policy (io/file moved "model.policy.edn")]
    (.mkdirs original)
    (.mkdirs moved)
    (spit policy (pr-str (assoc (edn/read-string (slurp "examples/android.policy.edn"))
                               :src (.getCanonicalPath (io/file "integration/fixtures/kotlin-project")))))
    (generator/regenerate (str policy) (str graph))
    (check! (= "model.policy.edn" (:policy-file (edn/read-string (slurp graph))))
            "Policy reference is not portable")
    (io/copy policy moved-policy)
    (io/copy graph moved-graph)
    (Files/delete (.toPath policy))
    (let [doc (edn/read-string (slurp moved-graph))
          legacy (assoc doc :policy-file (.getAbsolutePath policy))
          resolved (document/source-policy-path (str moved-graph) legacy)]
      (check! (= (.getCanonicalPath moved-policy) (.getCanonicalPath (io/file resolved)))
              "Moved legacy absolute policy reference did not find its sibling")
      (document/write-proposals! (str moved-graph)
                                (assoc doc :proposals [{:id :kept :name "Keep after move" :layers []}]))
      (generator/regenerate (document/source-policy-path (str moved-graph) doc) (str moved-graph))
      (check! (= :kept (get-in (edn/read-string (slurp moved-graph)) [:proposals 0 :id]))
              "Regeneration lost a proposal after relocation"))
    (println "Relocated policy, legacy path fallback and proposal persistence passed")))

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
    (doseq [edges [(get-in state [:doc :edges])
                   (get-in state [:scene :edges])
                   (:edges (hierarchy/collapse-arrows (hierarchy/view-at (:doc state) [])))]]
      (let [pair (filter #(or (= [:app.Consumer :data.impl.PluginImpl] [(:from %) (:to %)])
                             (= [:app :data] [(:from %) (:to %)])) edges)]
        (check! (some #(and (= :dependency (:kind %)) (not (:derived %))) pair)
                "Hilt wiring erased a source dependency")
        (check! (some #(and (= :association (:kind %)) (:derived %)) pair)
                "Derived Hilt association was lost during policy/rendering")
        (check! (str/includes? (#'draw/dep-label (first (hit/deps-of (first (filter :derived pair)))))
                              "[Hilt set]") "Hilt label was lost in the arrow tooltip")))
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
                      (original-draw
                        (if (> @frames 20)
                          (events/drill state :domain)
                          (assoc state :pointer [390 340]
                                       :hover {:kind :edge :deps (hit/deps-of
                                                                  (first (filter :derived (get-in state [:scene :edges]))))})))
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
      (relocation!)
      (when (some #{"--gui"} args) (gui! state)))
    (shutdown-agents)
    (System/exit 0)
    (catch Throwable e
      (.printStackTrace e)
      (System/exit 1))))
