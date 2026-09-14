(ns uml-viewer.sketch
  (:require [quil.applet :as applet]
            [quil.core :as q]
            [quil.middleware :as m]
            [uml-viewer.detail :as detail]
            [uml-viewer.draw :as draw]
            [uml-viewer.events :as events])
  (:import [processing.event MouseEvent]))

(def window-width 1500)
(def window-height 920)

(defonce !bridge
  (atom {:applet nil :model nil :pick nil :closed? false :exiting false}))

(defn- live? [applet]
  (boolean
    (and applet
         (try
           (not (.-finished applet))
           (catch Exception _ false)))))

(defn- raise-applet! [applet]
  (when applet
    (try
      (let [surface (.getSurface applet)]
        (.setAlwaysOnTop surface true)
        (.setAlwaysOnTop surface false)
        (when-let [native (try (.getNative surface) (catch Exception _ nil))]
          (when (instance? java.awt.Window native)
            (doto ^java.awt.Window native
              (.setVisible true)
              (.toFront)
              (.requestFocus)))))
      (catch Exception _))))

(defn- close-detail-window! []
  (when-let [ap (:applet @!bridge)]
    (swap! !bridge assoc :exiting true :applet nil)
    (try
      (applet/with-applet ap (q/exit))
      (catch Exception _))))

(defn- take-flag! [k]
  (let [v (get @!bridge k)]
    (swap! !bridge assoc k (if (identical? v true) false nil))
    v))

(defn- detail-setup []
  (q/frame-rate 30)
  (q/color-mode :rgb)
  (q/smooth)
  (q/text-font (q/create-font "SansSerif" 14 true))
  {:scroll 0 :shown nil})

(defn- detail-update [state]
  (let [id (get-in @!bridge [:model :class :id])]
    (if (not= id (:shown state))
      (assoc state :scroll 0 :shown id)
      state)))

(defn- detail-scroll [state amount]
  (let [model (:model @!bridge)
        h (detail/content-h (detail/rows model))
        max-y (max 0 (- h detail/height))
        dy (* (cond
                (number? amount) amount
                (map? amount) (or (:count amount) 0)
                :else 0)
              24)]
    (update state :scroll #(max 0 (min max-y (+ % dy))))))

(defn- start-detail-window! []
  (let [ap (q/sketch
             :title "Class"
             :size [detail/width detail/height]
             :setup detail-setup
             :update detail-update
             :draw (fn [state]
                     (when-let [model (:model @!bridge)]
                       (draw/draw-detail model (:scroll state 0))))
             :mouse-wheel (fn [state event]
                            (detail-scroll state event))
             :mouse-pressed (fn [state event]
                              (when-let [model (:model @!bridge)]
                                (when-let [id (detail/rel-at
                                                (detail/rows model)
                                                (+ (:y event) (:scroll state 0)))]
                                  (swap! !bridge assoc :pick id)))
                              state)
             :key-pressed (fn [state event]
                            (when (= :esc (:key event))
                              (swap! !bridge assoc :closed? true)
                              (q/exit))
                            state)
             :on-close (fn [state]
                         (let [exiting (:exiting @!bridge)]
                           (swap! !bridge assoc
                             :applet nil
                             :exiting false
                             :closed? (not exiting)))
                         state)
             :middleware [m/fun-mode])]
    (swap! !bridge assoc :applet ap :closed? false :exiting false)))

(defn- ensure-detail-window! [model]
  (swap! !bridge assoc :model model)
  (if (live? (:applet @!bridge))
    (raise-applet! (:applet @!bridge))
    (when-not (:starting @!bridge)
      (swap! !bridge assoc :starting true)
      (javax.swing.SwingUtilities/invokeLater
        (fn []
          (try
            (start-detail-window!)
            (finally
              (swap! !bridge assoc :starting false))))))))

(defn setup [path]
  (q/frame-rate 30)
  (q/color-mode :rgb)
  (q/smooth)
  (q/text-font (q/create-font "SansSerif" 14 true))
  (events/load-path path))

(defn update-state [state]
  (let [state (events/maybe-reload state)
        state (if (take-flag! :closed?)
                (events/close-detail state)
                state)
        state (if-let [id (take-flag! :pick)]
                (events/select-class state id)
                state)]
    (if-let [id (:detail-id state)]
      (when-let [model (detail/model (:scene state) id)]
        (swap! !bridge assoc :model model))
      (close-detail-window!))
    state))

(defn start! [path]
  (q/sketch
    :title "UML viewer"
    :size [window-width window-height]
    :setup (fn [] (setup path))
    :update update-state
    :draw draw/draw-state
    :mouse-pressed (fn [state event]
                     (let [state (events/on-press state (:x event) (:y event))]
                       (when-let [id (:detail-id state)]
                         (when-let [model (detail/model (:scene state) id)]
                           (ensure-detail-window! model)))
                       state))
    :mouse-moved (fn [state event]
                   (events/on-move state (:x event) (:y event)))
    :mouse-wheel (fn [state event]
                   (let [shift? (boolean
                                  (or (when (instance? MouseEvent event)
                                        (.isShiftDown ^MouseEvent event))
                                      (try (.isShiftDown ^MouseEvent
                                                         (.-mouseEvent (applet/current-applet)))
                                           (catch Exception _ false))))]
                     (events/on-scroll state event
                                       {:horizontal? shift?
                                        :window-w (q/width)
                                        :window-h (q/height)})))
    :key-pressed (fn [state event]
                   (events/on-key state (:key event)
                                 {:window-w (q/width) :window-h (q/height)}))
    :middleware [m/fun-mode]))
