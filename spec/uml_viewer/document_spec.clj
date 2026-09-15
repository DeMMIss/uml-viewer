(ns uml-viewer.document-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [uml-viewer.compose :as compose]
            [uml-viewer.document :as document]
            [uml-viewer.geom :as geom]
            [uml-viewer.ir :as ir]
            [uml-viewer.layout :as layout]))

(defn state []
  {:scene document/empty-scene
   :selected nil
   :hover nil
   :cam-x 0
   :cam-y 0
   :path "examples/library.edn"
   :mtime 0})

(describe "document"
  (it "loads a document from disk"
    (let [s (document/load-path "examples/library.edn")]
      (should (seq (:classes (:scene s))))
      (should (pos? (:mtime s)))))

  (it "does not throw on a missing file"
    (let [s (document/load-path "no-such-diagram.edn")]
      (should (re-find #"not found" (:error s)))
      (should= [] (get-in s [:scene :classes]))))

  (it "does not throw on invalid EDN"
    (let [f (java.io.File/createTempFile "bad" ".edn")]
      (spit f "{:packages")
      (let [s (document/load-path (.getPath f))]
        (should (string? (:error s)))
        (should= [] (get-in s [:scene :classes])))))

  (it "reloads when the file mtime changes"
    (let [s (assoc (document/load-path "examples/library.edn") :mtime 0)
          next (document/maybe-reload s)]
      (should (pos? (:mtime next)))
      (should-not (:error next))))

  (it "leaves state alone when mtime is unchanged"
    (let [s (document/load-path "examples/library.edn")]
      (should= s (document/maybe-reload s))))

  (it "records an error when reloaded IR is invalid"
    (let [f (java.io.File/createTempFile "bad" ".edn")]
      (spit f "{:packages [{:classes [{}]}]}")
      (let [next (document/maybe-reload (assoc (state) :path (.getPath f) :mtime 0))]
        (should (string? (:error next))))))

  (it "drops a detail id whose class vanished on reload"
    (let [f (java.io.File/createTempFile "uml" ".edn")]
      (spit f "{:packages [{:id :p :label \"P\" :classes [{:id :a :name \"A\"} {:id :b :name \"B\"}]}] :edges []}")
      (let [s (document/load-path (.getPath f))
            gone (first (filter #(= "B" (:name %)) (:classes (:scene s))))
            kept-name "A"]
        (spit f "{:packages [{:id :p :label \"P\" :classes [{:id :a :name \"A\"}]}] :edges []}")
        (let [next (document/maybe-reload (assoc s :mtime 0 :detail-id (:id gone)))]
          (should-not (:detail-id next))
          (should (some #(= kept-name (:name %)) (:classes (:scene next))))))))

  (it "paints class-card ops from .metrics keyed by :ns"
    (let [root (io/file "target" "doc-ns")
          examples (io/file root "examples")
          metrics (io/file root ".metrics")]
      (.mkdirs examples)
      (.mkdirs metrics)
      (spit (io/file metrics "crap.edn")
            (pr-str {:entries [{:name "place" :namespace "demo.board"
                                :complexity 1 :coverage 100.0 :crap 1.0}]}))
      (spit (io/file examples "diagram.edn")
            (pr-str {:packages [{:id :p :label "P"
                                 :classes [{:id :board :name "Board"
                                            :ns "demo.board"}]}]
                     :edges []}))
      (try
        (let [s (document/load-path (.getPath (io/file examples "diagram.edn")))
              c (first (filter #(= "Board" (:name %))
                               (get-in s [:scene :classes])))]
          (should (some #(= "place" (:name %)) (:ops c)))
          (should= "demo.board" (:ns c)))
        (finally
          (doseq [f (reverse (file-seq root))]
            (io/delete-file f true))))))

  (it "shifts a scene so routes that swing left of the boxes stay on canvas"
    (let [fit (ns-resolve 'uml-viewer.compose 'fit-scene)
          scene {:packages [{:id :p :rect (geom/rect 40 40 200 80)}]
                 :classes [{:id :a :rect (geom/rect 50 50 80 40)}]
                 :edges [{:from :a :to :a
                          :points [[50 70] [-80 70] [-80 120] [50 120]]}]
                 :size {:w 280 :h 160}}
          fitted (fit scene)
          xs (mapcat #(map first (:points %)) (:edges fitted))]
      (should (<= layout/margin (apply min xs)))
      (should (<= (apply max xs) (get-in fitted [:size :w])))
      (should (< 40 (get-in fitted [:classes 0 :rect :x])))))

  (it "stacks diagrams top to bottom"
    (let [d {:packages [{:id :p :label "P" :classes [{:id :a :name "A"}]}] :edges []}
          doc {:title "Doc"
               :diagrams [(assoc (ir/normalize d) :title "One")
                          (assoc (ir/normalize d) :title "Two")]}
          scene (document/compile-document doc)
          titles (map :title (:sections scene))]
      (should= ["One" "Two"] titles)
      (should (apply < (map :title-y (:sections scene)))))))
