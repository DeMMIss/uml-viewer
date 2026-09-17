(ns uml-viewer.application.events-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.engine.compose :as compose]
            [uml-viewer.application.detail :as detail]
            [uml-viewer.application.events :as events]
            [uml-viewer.domain.geom :as geom]
            [uml-viewer.domain.ir :as ir]))

(defn scene []
  (compose/compile-diagram
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

(describe "card-scene"
  (it "resolves a layer id from the hierarchical view so a port can open a card"
    (let [doc {:hierarchical true
               :title "Demo"
               :classes [{:id :engine.layout :name "Layout"
                          :ns "demo.engine.layout"}
                         {:id :domain.ir :name "Ir" :ns "demo.domain.ir"}]
               :edges [{:from :engine.layout :to :domain.ir :kind :dependency}]
               :order [:engine :domain]}
          s {:doc doc :focus [:engine] :scene {:classes []}}
          scene (events/card-scene s)]
      (should (some #(= :domain (:id %)) (:classes scene)))
      (should (some #(= :engine.layout (:id %)) (:classes scene)))
      (should= :domain
               (:detail-id (events/select-class s :domain))))))

(describe "clicks"
  (it "selects the class under the cursor"
    (let [s (state)
          a (first (filter #(= :a (:id %)) (:classes (:scene s))))
          [x y] [(geom/cx (:rect a)) (geom/cy (:rect a))]
          next (events/on-press s x y)]
      (should= :class (get-in next [:selected :kind]))
      (should= :a (get-in next [:selected :id]))
      (should-be-nil (:detail-id next))))

  (it "deselects when clicking empty space"
    (let [s (assoc (state) :selected {:kind :class :id :a} :detail-id :a)
          next (events/on-press s 0 0)]
      (should-not (:selected next))
      (should= :a (:detail-id next))))

  (it "scrolls the camera vertically"
    (let [s (assoc (state) :scene {:size {:h 4000 :w 800}})
          next (events/on-scroll s 2 900)]
      (should= 96.0 (:cam-y next))))

  (it "scrolls the camera horizontally"
    (let [s (assoc (state) :scene {:size {:h 800 :w 4000}})
          next (events/on-scroll s 2 {:horizontal? true :window-w 900 :window-h 800})]
      (should= 96.0 (:cam-x next))))

  (it "pans far enough to slide content out from under the inspector"
    (let [s (assoc (state) :scene {:size {:h 800 :w 1400}})
          view-w 1220
          next (events/on-scroll s 100 {:horizontal? true :window-w 1500
                                       :window-h 800 :view-w view-w})]
      (should= (double (- 1400 view-w)) (:cam-x next))))

  (it "reloads on r by clearing mtime"
    (should= 0 (:mtime (events/on-key (assoc (state) :mtime 99) :r))))

  (it "reloads on r without waiting for the agent"
    (let [next (events/on-key (assoc (state) :waiting true :mtime 99) :r)]
      (should-not (:waiting next))
      (should= 0 (:mtime next))))

  (it "pans with the arrow keys"
    (let [s (assoc (state) :scene {:size {:h 4000 :w 4000}})]
      (should= 96.0 (:cam-x (events/on-key s :right)))
      (should= 96.0 (:cam-y (events/on-key s :down)))
      (should= 0 (:cam-x (events/on-key (assoc s :cam-x 10) :left)))
      (should= 0 (:cam-y (events/on-key (assoc s :cam-y 10) :up)))))

  (it "pans left far enough to reach content past the origin"
    (let [s (assoc (state) :scene {:size {:h 800 :w 1400 :min-x -400}})
          next (events/on-scroll s -100 {:horizontal? true :window-w 1500
                                        :window-h 800 :view-w 1220})]
      (should= -400 (:cam-x next))))

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
      (let [h (:hover (events/on-move s x y))]
        (should= :class (:kind h))
        (should= :a (:id h)))))

  (it "reads wheel amount from a map and ignores junk"
    (let [s (assoc (state) :scene {:size {:h 4000 :w 800}})]
      (should= 96.0 (:cam-y (events/on-scroll s {:count 2} 900)))
      (should= 0.0 (:cam-y (events/on-scroll s :nope 900)))))

  (it "zooms 10% with ctrl+ and ctrl- and restores with ctrl+0"
    (let [dims {:window-w 1500 :window-h 900 :view-w 1220 :control? true}
          s (state)
          in (events/on-key s :+ dims)
          eq (events/on-key s := dims)
          out (events/on-key in :- dims)
          reset (events/on-key in :0 dims)]
      (should= events/zoom-step (:zoom in))
      (should= events/zoom-step (:zoom eq))
      (should= 1.0 (:zoom out))
      (should= 1.0 (:zoom reset))
      (should (pos? (:cam-x in)))
      (should= 0.0 (:cam-x out))
      (should= 0.0 (:cam-x reset))
      (should= 1.0 (events/zoom-of (events/on-key s :+ {})))
      (should= s (events/on-key s :+ {:control? false}))))

  (it "zooms out on ctrl-minus even when Quil reports the key-code"
    (let [dims {:window-w 1500 :window-h 900 :view-w 1220 :control? true}
          s (assoc (state) :zoom events/zoom-step)
          by-code (events/on-key s :unknown-key (assoc dims :key-code 45))
          by-raw (events/on-key s :unknown-key (assoc dims :raw-key \-))
          by-us (events/on-key s :unknown-key (assoc dims :raw-key \u001f))]
      (should= :out (events/zoom-dir :unknown-key {:key-code 45}))
      (should= :out (events/zoom-dir :- {}))
      (should= 1.0 (:zoom by-code))
      (should= 1.0 (:zoom by-raw))
      (should= 1.0 (:zoom by-us))))

  (it "maps a screen point through the current zoom"
    (let [s (assoc (state) :zoom 2.0 :cam-x 10 :cam-y 20)]
      (should= [15.0 30.0] (events/world-xy s 10 20)))))

(describe "detail window"
  (it "lists methods from a hierarchical document on the class card"
    (let [doc {:hierarchical true
               :title "Demo"
               :classes [{:id :layout :name "Layout" :ns "uml-viewer.engine.layout"
                          :ops [{:name "place" :text "place"}]}]
               :edges []}
          s {:doc doc :scene {:classes []} :path "examples/library.edn"}
          rows (detail/rows (detail/model (events/card-scene s) :layout))]
      (should (some #(= "place" (:op-name %)) rows))))

  (it "retargets the open class when a relationship is clicked"
    (let [s (assoc (state) :detail-id :a)
          model (detail/model (:scene s) :a)
          rel (first (filter #(= :rel (:kind %)) (detail/rows model)))
          next (events/on-detail-press s model 0 (:y rel))]
      (should= :b (:detail-id next))
      (should= :class (get-in next [:selected :kind]))
      (should= :b (get-in next [:selected :id])))))

(describe "regen button"
  (it "hits the inspector Regen control"
    (should (events/regen-hit? 1300 880 1500 920))
    (should-not (events/regen-hit? 100 100 1500 920))))

(describe "hierarchy navigation"
  (it "opens a layer from the box or a child row"
    (should= :engine (events/layer-id {:kind :class :id :engine :drill? true}))
    (should= :engine (events/layer-id {:kind :child :id :engine.layout :parent :engine}))
    (should-be-nil (events/layer-id {:kind :class :id :layout})))

  (it "drills into a namespace and returns on back and esc"
    (let [doc {:hierarchical true
               :title "Demo"
               :classes [{:id :source :name "Source" :ns "demo.source"}
                         {:id :source.clojure :name "Clojure" :ns "demo.source.clojure"}]
               :edges []}
          s {:doc doc
             :focus []
             :path "examples/library.edn"
             :scene (scene)
             :cam-x 10 :cam-y 10}
          opened (events/drill s :source)]
      (should= [:source] (:focus opened))
      (should= 0 (:cam-x opened))
      (should= [] (:focus (events/on-key opened :esc)))
      (should= [] (:focus (events/back opened)))
      (should= s (events/back s)))))
