(ns uml-viewer.events
  (:require [uml-viewer.detail :as detail]
            [uml-viewer.hit :as hit]))

(defn select-class [state id]
  (if (hit/class-by-id (:scene state) id)
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

(defn on-press [state x y]
  (let [[wx wy] (world-xy state x y)
        hit (hit/at (:scene state) wx wy)]
    (assoc state :selected hit)))

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
        max-x (max 0 (- (:w size) view-w))
        max-y (max 0 (- (:h size) window-h))]
    (if horizontal?
      (update state :cam-x #(max 0 (min max-x (+ % (* amount 48)))))
      (update state :cam-y #(max 0 (min max-y (+ % (* amount 48))))))))

(defn on-key
  ([state k] (on-key state k {:window-w 1500 :window-h 900}))
  ([state k dims]
   (case k
     :left (on-scroll state -2 (assoc dims :horizontal? true))
     :right (on-scroll state 2 (assoc dims :horizontal? true))
     :up (on-scroll state -2 dims)
     :down (on-scroll state 2 dims)
     :esc (assoc state :selected nil)
     :r (assoc state :mtime 0)
     state)))
