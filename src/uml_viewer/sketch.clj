(ns uml-viewer.sketch
  (:require [quil.applet :as applet]
            [quil.core :as q]
            [quil.middleware :as m]
            [uml-viewer.draw :as draw]
            [uml-viewer.events :as events])
  (:import [processing.event MouseEvent]))

(def window-width 1500)
(def window-height 920)

(defn setup [path]
  (q/frame-rate 30)
  (q/color-mode :rgb)
  (q/smooth)
  (q/text-font (q/create-font "SansSerif" 14 true))
  (events/load-path path))

(defn update-state [state]
  (events/maybe-reload state))

(defn start! [path]
  (q/sketch
    :title "UML viewer"
    :size [window-width window-height]
    :setup (fn [] (setup path))
    :update update-state
    :draw draw/draw-state
    :mouse-pressed (fn [state event]
                     (events/on-press state (:x event) (:y event)))
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
