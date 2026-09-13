(ns uml-viewer.sketch
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [uml-viewer.draw :as draw]
            [uml-viewer.events :as events]))

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
    :mouse-dragged (fn [state event]
                     (events/on-drag state (:x event) (:y event)))
    :mouse-released (fn [state _]
                      (events/on-release state))
    :mouse-moved (fn [state event]
                   (events/on-move state (:x event) (:y event)))
    :key-pressed (fn [state event]
                   (events/on-key state (:key event)))
    :middleware [m/fun-mode]
    :features [:keep-on-top]))
