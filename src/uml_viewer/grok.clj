(ns uml-viewer.grok
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [uml-viewer.theme :as theme]))

(def standing-rules
  (str "You are working in the uml-viewer project. The diagram is already on screen.\n"
       "After every change to Clojure source or the policy, always:\n"
       "1. Edit examples/uml-viewer.policy.edn only if layering, diagrams, or\n"
       "   association-vs-dependency changed. Do not edit examples/uml-viewer.edn.\n"
       "2. Run clj -M:crap.\n"
       "3. Run clj -M:mutate on each changed file under src/ (differential:\n"
       "   snapshots skip unchanged forms).\n"
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
       "set background color of grokTab to " (applescript-rgb theme/bg) "\n"
       "set normal text color of grokTab to " (applescript-rgb theme/ink) "\n"
       "set bold text color of grokTab to " (applescript-rgb theme/gold) "\n"
       "set cursor color of grokTab to " (applescript-rgb theme/gold) "\n"
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
