(ns uml-viewer.sketch
  (:require [quil.applet :as applet]
            [quil.core :as q]
            [quil.middleware :as m]
            [uml-viewer.detail :as detail]
            [uml-viewer.draw :as draw]
            [uml-viewer.events :as events]
            [uml-viewer.grok :as grok]
            [uml-viewer.source-window :as source-window])
  (:import [java.awt Frame]
           [javax.swing SwingUtilities]
           [processing.event MouseEvent]))

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

(defn- native-window [applet]
  (try
    (.getNative (.getSurface applet))
    (catch Exception _ nil)))

(defn- front! [native]
  (cond
    (instance? Frame native)
    (doto ^Frame native
      (.setExtendedState Frame/NORMAL)
      (.setVisible true)
      (.toFront)
      (.requestFocus)
      (.requestFocusInWindow))
    (instance? java.awt.Window native)
    (doto ^java.awt.Window native
      (.setVisible true)
      (.toFront)
      (.requestFocus)
      (.requestFocusInWindow))))

(defn- later! [f]
  (SwingUtilities/invokeLater f))

(defn- halt-vm! []
  (System/exit 0))

(defn- exit-app! []
  (grok/shutdown-children!)
  (halt-vm!))

(defn- pin-card! [on?]
  (when-let [ap (:applet @!bridge)]
    (try
      (when-let [surface (.getSurface ap)]
        (.setAlwaysOnTop surface (boolean on?)))
      (when on?
        (front! (native-window ap)))
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
  {:scroll 0 :shown nil :hover nil})

(defn- detail-update [state]
  (let [id (get-in @!bridge [:model :class :id])]
    (if (not= id (:shown state))
      (assoc state :scroll 0 :shown id :hover nil)
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

(defn- detail-draw [state]
  (when-let [model (:model @!bridge)]
    (draw/draw-detail model (:scroll state 0) (:hover state))))

(defn- detail-mouse-moved [state event]
  (if-let [model (:model @!bridge)]
    (assoc state :hover
           (detail/member-at (detail/rows model)
                             (+ (:y event) (:scroll state 0))))
    (assoc state :hover nil)))

(defn- detail-mouse-exited [state _event]
  (assoc state :hover nil))

(defn- click-count [event]
  (let [n (:count event)]
    (if (number? n)
      n
      (try
        (if-let [ev (.-mouseEvent (applet/current-applet))]
          (.getCount ^MouseEvent ev)
          1)
        (catch Exception _ 1)))))

(defn- detail-mouse-pressed [state event]
  (when-let [model (:model @!bridge)]
    (let [y (+ (:y event) (:scroll state 0))
          rows (detail/rows model)]
      (if-let [op (detail/member-at rows y)]
        (when (>= (click-count event) 2)
          (source-window/open-member-window! (:source @!bridge) (:ns model) op))
        (when-let [id (detail/rel-at rows y)]
          (swap! !bridge assoc :pick id)))))
  state)

(defn- detail-key-pressed [state event]
  (when (= :esc (:key event))
    (swap! !bridge assoc :closed? true)
    (q/exit))
  state)

(defn- detail-on-close [state]
  (let [exiting (:exiting @!bridge)]
    (swap! !bridge assoc
      :applet nil
      :exiting false
      :closed? (not exiting)))
  state)

(defn- start-detail-window! []
  (let [ap (q/sketch
             :title "Class"
             :size [detail/width detail/height]
             :setup detail-setup
             :update detail-update
             :draw detail-draw
             :mouse-moved detail-mouse-moved
             :mouse-exited detail-mouse-exited
             :mouse-wheel detail-scroll
             :mouse-pressed detail-mouse-pressed
             :key-pressed detail-key-pressed
             :on-close detail-on-close
             :middleware [m/fun-mode])]
    (swap! !bridge assoc :applet ap :closed? false :exiting false)))

(defn- ensure-detail-window! [model]
  (swap! !bridge assoc :model model)
  (when-not (or (live? (:applet @!bridge)) (:starting @!bridge))
    (swap! !bridge assoc :starting true)
    (later!
      (fn []
        (try
          (start-detail-window!)
          (pin-card! true)
          (finally
            (swap! !bridge assoc :starting false)))))))

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

(defn- on-main-press [state event]
  (let [state (events/on-press state (:x event) (:y event))
        class? (= :class (:kind (:selected state)))]
    (cond
      (and class? (>= (click-count event) 2))
      (let [state (events/select-class state (get-in state [:selected :id]))]
        (when-let [model (detail/model (:scene state) (:detail-id state))]
          (ensure-detail-window! model))
        (pin-card! true)
        state)

      class?
      state

      :else
      (do (pin-card! false) state))))

(defn- applet-shift? []
  (try
    (let [ap ^Object (applet/current-applet)
          ev (.-mouseEvent ap)]
      (boolean (and (instance? MouseEvent ev)
                    (.isShiftDown ^MouseEvent ev))))
    (catch Exception _ false)))

(defn- on-main-wheel [state event]
  (let [shift? (boolean
                 (or (when (instance? MouseEvent event)
                       (.isShiftDown ^MouseEvent event))
                     (applet-shift?)))]
    (events/on-scroll state event
                      {:horizontal? shift?
                       :window-w (q/width)
                       :window-h (q/height)})))

(defn- on-main-close [state]
  (close-detail-window!)
  (exit-app!)
  state)

(defn start! [path source-impl]
  (swap! !bridge assoc :source source-impl)
  (q/sketch
    :title "UML viewer"
    :size [window-width window-height]
    :setup (fn [] (setup path))
    :update update-state
    :draw draw/draw-state
    :mouse-pressed on-main-press
    :mouse-moved (fn [state event]
                   (events/on-move state (:x event) (:y event)))
    :mouse-wheel on-main-wheel
    :key-pressed (fn [state event]
                   (events/on-key state (:key event)
                                 {:window-w (q/width) :window-h (q/height)}))
    :on-close on-main-close
    :middleware [m/fun-mode]))
