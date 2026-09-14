(ns uml-viewer.grok
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def standing-rules
  (str "You are working in the uml-viewer project. The diagram is already on screen.\n"
       "After you change Clojure source:\n"
       "- Update examples/uml-viewer.edn if packages, classes, or edges changed.\n"
       "  Keep that file topology-only (no authored CRAP/coverage/killed numbers).\n"
       "- Do not start the viewer; it reloads when the EDN mtime changes.\n"
       "If the user asked to recompute CRAP or mutation metrics, run\n"
       "`clj -M:crap` and `clj -M:mutate` on the affected files under src/.\n"
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

(defn wrap-prompt
  [user-text]
  (str standing-rules "\nDirective:\n" (str/trim (or user-text ""))))

(def session-name "uml-viewer-grok")

(defn tmux!
  "Run tmux with `args`. Returns the process exit code (1 if tmux is missing)."
  [& args]
  (try
    (let [p (.start (ProcessBuilder. (into-array String (cons "tmux" args))))]
      (.waitFor p))
    (catch Exception _ 1)))

(defn new-session-args
  [cwd]
  ["new-session" "-d" "-s" session-name "-c" cwd
   (grok-executable) "--yolo" "--trust"])

(defn kill-session-args
  []
  ["kill-session" "-t" session-name])

(defn attach-command
  []
  (str "tmux attach -t " session-name "; exit"))

(defn osascript
  "AppleScript that opens Terminal.app on `shell-cmd`."
  [shell-cmd]
  (str "tell application \"Terminal\"\n"
       "activate\n"
       "do script " (pr-str shell-cmd) "\n"
       "end tell"))

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
         proc (.start (ProcessBuilder.
                        (into-array String ["osascript" "-e" script])))]
     {:proc proc :script script :session session-name})))

(defn- with-tty
  "Run under script(1) so grok sees a TTY and reads Esc as in the TUI."
  [cmd]
  (if (.isFile (io/file "/usr/bin/script"))
    (into ["/usr/bin/script" "-q" "/dev/null"] cmd)
    cmd))

(defn command-line
  [user-text {:keys [cwd continue?]}]
  (with-tty
    (cond-> [(grok-executable)]
      continue? (conj "-c")
      true (conj "-p" (wrap-prompt user-text)
                 "--cwd" (or cwd (System/getProperty "user.dir"))
                 "--yolo"
                 "--output-format" "plain"))))

(defn touch-edn!
  [path]
  (let [f (io/file path)]
    (when (.exists f)
      (.setLastModified f (System/currentTimeMillis)))
    path))

(defonce !live-proc (atom nil))

(defn- pump-chars
  "Forward stdout as soon as bytes arrive, not only on newlines."
  [^java.io.InputStream in on-out]
  (let [buf (byte-array 2048)]
    (loop []
      (let [n (.read in buf)]
        (when (pos? n)
          (on-out (String. buf 0 n "UTF-8"))
          (recur))))))

(defn run-directive!
  "Start grok in a future. `on-out` gets stdout chunks; `on-done` gets {:exit n}.
  Returns the Future. Store `!proc` (atom) to allow cancel via destroy."
  [{:keys [prompt cwd continue? on-out on-done !proc]
    :or {on-out identity on-done identity}}]
  (future
    (try
      (let [cwd (or cwd (System/getProperty "user.dir"))
            cmd (command-line prompt {:cwd cwd :continue? continue?})
            proc (.start (doto (ProcessBuilder. (into-array String cmd))
                           (.directory (io/file cwd))
                           (.redirectErrorStream true)))]
        (when !proc (reset! !proc proc))
        (reset! !live-proc proc)
        (on-out (str "Running grok in " cwd " …\n"))
        (let [pump (future
                     (try
                       (pump-chars (.getInputStream proc) on-out)
                       (catch Exception _)))]
          (let [code (.waitFor proc)]
            (deref pump 2000 nil)
            (when !proc (reset! !proc nil))
            (compare-and-set! !live-proc proc nil)
            (on-done {:exit code}))))
      (catch Exception e
        (when !proc (reset! !proc nil))
        (reset! !live-proc nil)
        (on-out (str "Grok failed: " (.getMessage e) "\n"))
        (on-done {:exit 1 :error e})))))

(def esc-byte (byte 0x1b))

(defn write-escapes!
  "Write two Esc bytes to a stream. Does not close or kill anything."
  [^java.io.OutputStream out]
  (let [esc (byte-array [esc-byte])]
    (.write out esc)
    (.flush out)
    (.write out esc)
    (.flush out))
  out)

(defn interrupt!
  "Send Esc Esc on grok's stdin. Does not destroy the process."
  [!proc]
  (when-let [p (and !proc @!proc)]
    (try
      (write-escapes! (.getOutputStream p))
      (catch Exception _))
    true))

(defn cancel!
  [!proc]
  (interrupt! !proc))

(defn shutdown-children!
  "Kill the grok tmux session and any ProcessBuilder grok child."
  []
  (apply tmux! (kill-session-args))
  (when-let [p @!live-proc]
    (try
      (interrupt! !live-proc)
      (Thread/sleep 400)
      (.destroyForcibly p)
      (catch Exception _))
    (reset! !live-proc nil)))
