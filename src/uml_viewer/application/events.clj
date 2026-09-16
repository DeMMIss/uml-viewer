(ns uml-viewer.application.events
  (:require [clojure.string :as str]
            [uml-viewer.application.detail :as detail]
            [uml-viewer.application.document :as document]
            [uml-viewer.domain.hierarchy :as hierarchy]
            [uml-viewer.engine.hit :as hit]
            [uml-viewer.engine.layout :as layout]
            [uml-viewer.application.overlay :as overlay]))

(defn- last-seg [id]
  (keyword (last (str/split (name id) #"\."))))

(defn- rebuild [state]
  (if (and (:doc state) (:hierarchical (:doc state)))
    (assoc state
      :scene (document/compile-view (:doc state)
                                    (overlay/metrics-root (:path state))
                                    (or (:focus state) []))
      :cam-x 0
      :cam-y 0
      :selected nil)
    state))

(defn layer-id
  "Namespace to drill from a hit: the layer box, or a child's parent."
  [sel]
  (cond
    (and (= :class (:kind sel)) (:drill? sel)) (:id sel)
    (= :child (:kind sel)) (:parent sel)
    :else nil))

(defn drill
  "Open the namespace node `id` (next level down)."
  [state id]
  (rebuild (update state :focus (fnil conj []) (last-seg id))))

(defn back
  "Return to the parent namespace view."
  [state]
  (if (seq (:focus state))
    (rebuild (update state :focus pop))
    state))

(defn- view-classes [doc path]
  (mapcat :classes (:packages (hierarchy/view-at doc path))))

(defn card-scene
  "Scene used for the class card: the full hierarchical graph, or the view."
  [state]
  (if (and (:doc state) (:hierarchical (:doc state)))
    (let [doc (:doc state)
          by-id #(into {} (map (juxt :id identity) %))
          classes (->> (merge (by-id (view-classes doc []))
                              (by-id (view-classes doc (or (:focus state) [])))
                              (by-id (:classes doc)))
                       vals vec)]
      {:classes classes
       :edges (:edges doc)
       :packages []
       :diagram {:title (:title doc)}})
    (:scene state)))

(defn select-class [state id]
  (if (or (hit/class-by-id (:scene state) id)
          (some #(= id (:id %)) (:classes (card-scene state))))
    (assoc state :selected {:kind :class :id id} :detail-id id)
    state))

(defn close-detail [state]
  (dissoc state :detail-id))

(defn on-detail-press [state model scroll y]
  (if-let [id (detail/rel-at (detail/rows model) (+ y scroll))]
    (select-class state id)
    state))

(defn world-xy [state x y]
  [(+ x (:cam-x state)) (+ y (:cam-y state))])

(defn on-move [state x y]
  (let [[wx wy] (world-xy state x y)]
    (assoc state :hover (hit/at (:scene state) wx wy))))

(defn regen-hit?
  [x y window-w window-h]
  (let [r (layout/regen-button window-w window-h)]
    (and (>= x (:x r)) (< x (+ (:x r) (:w r)))
         (>= y (:y r)) (< y (+ (:y r) (:h r))))))

(defn on-press [state x y]
  (if (and (seq (:focus state)) (< y 44) (< x 320))
    (back state)
    (let [[wx wy] (world-xy state x y)
          hit (hit/at (:scene state) wx wy)]
      (assoc state :selected hit))))

(defn on-scroll [state amount opts]
  (let [opts (if (map? opts) opts {:window-h opts :window-w 1500})
        horizontal? (:horizontal? opts)
        window-w (or (:window-w opts) 1500)
        window-h (or (:window-h opts) 900)
        view-w (or (:view-w opts) window-w)
        amount (cond
                 (number? amount) amount
                 (map? amount) (or (:count amount) 0)
                 :else 0)
        size (get-in state [:scene :size] {:w 800 :h 600})
        min-x (or (:min-x size) 0)
        min-y (or (:min-y size) 0)
        max-x (max min-x (- (:w size) view-w))
        max-y (max min-y (- (:h size) window-h))]
    (if horizontal?
      (update state :cam-x #(max min-x (min max-x (+ % (* amount 48)))))
      (update state :cam-y #(max min-y (min max-y (+ % (* amount 48))))))))

(defn on-key
  ([state k] (on-key state k {:window-w 1500 :window-h 900}))
  ([state k dims]
   (case k
     :left (on-scroll state -2 (assoc dims :horizontal? true))
     :right (on-scroll state 2 (assoc dims :horizontal? true))
     :up (on-scroll state -2 dims)
     :down (on-scroll state 2 dims)
     :esc (if (seq (:focus state))
            (back state)
            (assoc state :selected nil))
     :r (-> state (dissoc :waiting) (assoc :mtime 0))
     state)))
