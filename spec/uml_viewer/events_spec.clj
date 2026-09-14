(ns uml-viewer.events-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.detail :as detail]
            [uml-viewer.events :as events]
            [uml-viewer.geom :as geom]
            [uml-viewer.ir :as ir]
            [uml-viewer.metrics :as m]))

(defn scene []
  (events/compile-diagram
    (ir/normalize
      {:packages
       [{:id :p :label "P"
         :classes [{:id :a :name "A"} {:id :b :name "B"}]}]
       :edges [{:from :a :to :b :kind :association}]})))

(defn state []
  {:scene (scene)
   :selected nil
   :hover nil
   :cam-x 0
   :cam-y 0
   :path "examples/library.edn"
   :mtime 0})

(describe "clicks"
  (it "selects the class under the cursor"
    (let [s (state)
          a (first (filter #(= :a (:id %)) (:classes (:scene s))))
          [x y] [(geom/cx (:rect a)) (geom/cy (:rect a))]
          next (events/on-press s x y)]
      (should= {:kind :class :id :a} (:selected next))
      (should-be-nil (:detail-id next))))

  (it "deselects when clicking empty space"
    (let [s (assoc (state) :selected {:kind :class :id :a} :detail-id :a)
          next (events/on-press s 0 0)]
      (should-not (:selected next))
      (should= :a (:detail-id next))))

  (it "scrolls the camera vertically"
    (let [s (assoc (state) :scene {:size {:h 4000 :w 800}})
          next (events/on-scroll s 2 900)]
      (should= 96 (:cam-y next))))

  (it "scrolls the camera horizontally"
    (let [s (assoc (state) :scene {:size {:h 800 :w 4000}})
          next (events/on-scroll s 2 {:horizontal? true :window-w 900 :window-h 800})]
      (should= 96 (:cam-x next))))

  (it "pans far enough to slide content out from under the inspector"
    (let [s (assoc (state) :scene {:size {:h 800 :w 1400}})
          next (events/on-scroll s 100 {:horizontal? true :window-w 1500 :window-h 800})
          view-w (- 1500 m/sidebar-w)]
      (should= (- 1400 view-w) (:cam-x next))))

  (it "reloads on r by clearing mtime"
    (should= 0 (:mtime (events/on-key (assoc (state) :mtime 99) :r))))

  (it "pans with the arrow keys"
    (let [s (assoc (state) :scene {:size {:h 4000 :w 4000}})]
      (should= 96 (:cam-x (events/on-key s :right)))
      (should= 96 (:cam-y (events/on-key s :down)))
      (should= 0 (:cam-x (events/on-key (assoc s :cam-x 10) :left)))
      (should= 0 (:cam-y (events/on-key (assoc s :cam-y 10) :up)))))

  (it "clears selection on escape and ignores other keys"
    (let [s (assoc (state) :selected {:kind :class :id :a} :detail-id :a)
          next (events/on-key s :esc)]
      (should-not (:selected next))
      (should= :a (:detail-id next))
      (should= s (events/on-key s :x))))

  (it "tracks hover under the pointer"
    (let [s (state)
          a (first (filter #(= :a (:id %)) (:classes (:scene s))))
          [x y] [(geom/cx (:rect a)) (geom/cy (:rect a))]]
      (should= {:kind :class :id :a} (:hover (events/on-move s x y)))))

  (it "loads a document from disk"
    (let [s (events/load-path "examples/library.edn")]
      (should (seq (:classes (:scene s))))
      (should (pos? (:mtime s)))))

  (it "does not throw on a missing file"
    (let [s (events/load-path "no-such-diagram.edn")]
      (should (re-find #"not found" (:error s)))
      (should= [] (get-in s [:scene :classes]))))

  (it "does not throw on invalid EDN"
    (let [f (java.io.File/createTempFile "bad" ".edn")]
      (spit f "{:packages")
      (let [s (events/load-path (.getPath f))]
        (should (string? (:error s)))
        (should= [] (get-in s [:scene :classes])))))

  (it "reloads when the file mtime changes"
    (let [s (assoc (events/load-path "examples/library.edn") :mtime 0)
          next (events/maybe-reload s)]
      (should (pos? (:mtime next)))
      (should-not (:error next))))

  (it "leaves state alone when mtime is unchanged"
    (let [s (events/load-path "examples/library.edn")]
      (should= s (events/maybe-reload s))))

  (it "records an error when reloaded IR is invalid"
    (let [f (java.io.File/createTempFile "bad" ".edn")]
      (spit f "{:packages [{:classes [{}]}]}")
      (let [next (events/maybe-reload (assoc (state) :path (.getPath f) :mtime 0))]
        (should (string? (:error next))))))

  (it "reads wheel amount from a map and ignores junk"
    (let [s (assoc (state) :scene {:size {:h 4000 :w 800}})]
      (should= 96 (:cam-y (events/on-scroll s {:count 2} 900)))
      (should= 0 (:cam-y (events/on-scroll s :nope 900))))))

(describe "detail window"
  (it "retargets the open class when a relationship is clicked"
    (let [s (assoc (state) :detail-id :a)
          model (detail/model (:scene s) :a)
          rel (first (filter #(= :rel (:kind %)) (detail/rows model)))
          next (events/on-detail-press s model 0 (:y rel))]
      (should= :b (:detail-id next))
      (should= {:kind :class :id :b} (:selected next))))

  (it "drops a detail id whose class vanished on reload"
    (let [f (java.io.File/createTempFile "uml" ".edn")]
      (spit f "{:packages [{:id :p :label \"P\" :classes [{:id :a :name \"A\"} {:id :b :name \"B\"}]}] :edges []}")
      (let [s (events/load-path (.getPath f))
            gone (first (filter #(= "B" (:name %)) (:classes (:scene s))))
            kept-name "A"]
        (spit f "{:packages [{:id :p :label \"P\" :classes [{:id :a :name \"A\"}]}] :edges []}")
        (let [next (events/maybe-reload (assoc s :mtime 0 :detail-id (:id gone)))]
          (should-not (:detail-id next))
          (should (some #(= kept-name (:name %)) (:classes (:scene next)))))))))

(describe "document"
  (it "stacks diagrams top to bottom"
    (let [d {:packages [{:id :p :label "P" :classes [{:id :a :name "A"}]}] :edges []}
          doc {:title "Doc"
               :diagrams [(assoc (ir/normalize d) :title "One")
                          (assoc (ir/normalize d) :title "Two")]}
          scene (events/compile-document doc)
          titles (map :title (:sections scene))]
      (should= ["One" "Two"] titles)
      (should (apply < (map :title-y (:sections scene)))))))
