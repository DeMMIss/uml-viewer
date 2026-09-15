(ns uml-viewer.sketch
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [quil.applet :as applet]
            [quil.core :as q]
            [quil.middleware :as m]
            [uml-viewer.detail :as detail]
            [uml-viewer.document :as document]
            [uml-viewer.draw :as draw]
            [uml-viewer.events :as events]
            [uml-viewer.layout :as layout]
            [uml-viewer.source-window :as source-window])
  (:import [java.awt Frame]
           [javax.swing SwingUtilities]
           [processing.event MouseEvent]))

(def window-width 1500)
(def window-height 920)

(defonce !bridge
  (atom {:applet nil :model nil :pick nil :closed? false :exiting false}))

(def standing-rules
  (str "You are working in the uml-viewer project. The diagram is already on screen.\n"
       "After every change to Clojure source or the policy, always:\n"
       "1. Edit examples/uml-viewer.policy.edn only if layering, diagrams, or\n"
       "   association-vs-dependency changed. Do not edit examples/uml-viewer.edn.\n"
       "   Policy describes the real dependency structure of the source. Do\n"
       "   not re-home a namespace in the policy to create a partition the\n"
       "   code does not have. If a layer is wrong, change the requires (or\n"
       "   the ns), then update the policy to match.\n"
       "2. Run clj -M:crap.\n"
       "3. Run clj -M:mutate on each changed file under src/ (differential:\n"
       "   snapshots skip unchanged forms). Uncovered mutants are coverage\n"
       "   gaps, not a failed run: keep the snapshot and do not re-run the\n"
       "   file, drop differential, or pass --mutate-all because of them.\n"
       "4. Run clj -M:ir so the viewer reloads topology and the new .metrics/.\n"
       "Do not start the viewer; it reloads when the EDN mtime changes.\n"
       "Do not commit or push unless asked.\n"))

(defn grok-executable
  []
  (let [home (System/getenv "HOME")
        named (System/getenv "GROK_BIN")
        candidates (filter identity
                           [named
                            (when home (str home "/.grok/bin/grok"))
                            "/usr/local/bin/grok"
                            "/opt/homebrew/bin/grok"])]
    (or (first (filter (fn [p]
                         (let [f (io/file p)]
                           (and (.isFile f) (.canExecute f))))
                       candidates))
        "grok")))

(def session-name "uml-viewer-grok")

(defn tmux!
  "Run tmux with `args`. Returns the process exit code (1 if tmux is missing)."
  [& args]
  (try
    (let [p (.start (ProcessBuilder. (into-array String (cons "tmux" args))))]
      (.waitFor p))
    (catch Exception _ 1)))

(defn rgb-16
  "Terminal.app AppleScript colors are 16-bit (0–65535)."
  [[r g b]]
  [(* (int r) 257) (* (int g) 257) (* (int b) 257)])

(defn- applescript-rgb [c]
  (let [[r g b] (rgb-16 c)]
    (str "{" r ", " g ", " b "}")))

(defn new-session-args
  [cwd]
  ["new-session" "-d" "-s" session-name "-c" cwd
   "-e" "GROK_THEME=terminal"
   "-e" "GROK_TERMINAL_THEME=1"
   "-e" "COLORTERM=truecolor"
   (grok-executable) "--yolo" "--trust" "--rules" standing-rules
   ";" "set-option" "status" "off"])

(defn kill-session-args
  []
  ["kill-session" "-t" session-name])

(def terminal-title "Grok")

(defonce !terminal-window-id (atom nil))

(defn attach-command
  []
  (str "tmux attach -t " session-name "; exit"))

(defn osascript
  "AppleScript that opens a Terminal window on `shell-cmd`, painted like the diagram.
  Raises only that window, not every Terminal window. Returns the new window id."
  [shell-cmd]
  (str "tell application \"Terminal\"\n"
       "launch\n"
       "set grokTab to do script " (pr-str shell-cmd) "\n"
       "set background color of grokTab to " (applescript-rgb draw/bg) "\n"
       "set normal text color of grokTab to " (applescript-rgb draw/ink) "\n"
       "set bold text color of grokTab to " (applescript-rgb draw/gold) "\n"
       "set cursor color of grokTab to " (applescript-rgb draw/gold) "\n"
       "set font name of grokTab to \"Menlo\"\n"
       "set font size of grokTab to 13\n"
       "set custom title of grokTab to \"" terminal-title "\"\n"
       "set title displays custom title of grokTab to true\n"
       "set title displays device name of grokTab to false\n"
       "set title displays shell path of grokTab to false\n"
       "set title displays settings name of grokTab to false\n"
       "set winID to id of front window\n"
       "end tell\n"
       "tell application \"System Events\"\n"
       "tell process \"Terminal\"\n"
       "try\n"
       "perform action \"AXRaise\" of (first window whose name contains \"" terminal-title "\")\n"
       "end try\n"
       "end tell\n"
       "end tell\n"
       "return winID"))

(defn close-terminal-script
  "AppleScript that closes the Grok Terminal window. Does not launch Terminal."
  ([] (close-terminal-script nil))
  ([win-id]
   (str "tell application \"System Events\"\n"
        "if not (exists process \"Terminal\") then return\n"
        "end tell\n"
        "tell application \"Terminal\"\n"
        (when win-id
          (str "try\n"
               "close (first window whose id is " win-id ") saving no\n"
               "end try\n"))
        "repeat with w in (get windows)\n"
        "try\n"
        "if custom title of selected tab of w is \"" terminal-title "\" then\n"
        "close w saving no\n"
        "end if\n"
        "end try\n"
        "end repeat\n"
        "end tell")))

(defn run-osascript
  "Run `script` with osascript. Returns trimmed stdout, or \"\"."
  [script]
  (try
    (let [p (.start (doto (ProcessBuilder. (into-array String ["osascript" "-e" script]))
                      (.redirectErrorStream true)))
          out (slurp (.getInputStream p))]
      (.waitFor p)
      (str/trim out))
    (catch Exception _ "")))

(defn close-terminal-window!
  "Close the Terminal window that attached to the grok session."
  []
  (run-osascript (close-terminal-script @!terminal-window-id))
  (reset! !terminal-window-id nil))

(defn open-in-terminal!
  "Start grok in tmux session uml-viewer-grok and attach a Terminal window."
  ([] (open-in-terminal! (System/getProperty "user.dir")))
  ([cwd]
   (apply tmux! (kill-session-args))
   (let [code (apply tmux! (new-session-args cwd))]
     (when-not (zero? code)
       (binding [*out* *err*]
         (println "UML viewer: could not start tmux session" session-name))))
   (let [script (osascript (attach-command))
         out (run-osascript script)
         win-id (re-find #"\d+" out)]
     (reset! !terminal-window-id win-id)
     {:script script :session session-name :window-id win-id})))

(defn shutdown-children!
  "Kill the grok tmux session and close its Terminal window."
  []
  (apply tmux! (kill-session-args))
  (close-terminal-window!))

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
  (shutdown-children!)
  (halt-vm!))

(defn- class-title [model]
  (or (get-in model [:class :name]) "Class"))

(defn- set-card-title! [title]
  (when-let [native (native-window (:applet @!bridge))]
    (when (instance? Frame native)
      (.setTitle ^Frame native (str title)))))

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
        (source-window/open-member-window! (:source @!bridge) (:ns model) op)
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
             :title (class-title (:model @!bridge))
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
  (set-card-title! (class-title model))
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
  (document/load-path path))

(defn- view-dims []
  (let [w (q/width)
        h (q/height)]
    {:window-w w
     :window-h h
     :view-w (max 0 (- w layout/sidebar-w))}))

(defn update-state [state]
  (let [state (document/maybe-reload state)
        state (if (take-flag! :closed?)
                (events/close-detail state)
                state)
        state (if-let [id (take-flag! :pick)]
                (events/select-class state id)
                state)]
    (if-let [id (:detail-id state)]
      (when-let [model (detail/model (:scene state) id)]
        (swap! !bridge assoc :model model)
        (set-card-title! (class-title model)))
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
                      (assoc (view-dims) :horizontal? shift?))))

(defn- on-main-close [state]
  (close-detail-window!)
  (exit-app!)
  state)

(defn start! [path source-impl]
  (swap! !bridge assoc :source source-impl)
  (open-in-terminal!)
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
                   (events/on-key state (:key event) (view-dims)))
    :on-close on-main-close
    :middleware [m/fun-mode]))
