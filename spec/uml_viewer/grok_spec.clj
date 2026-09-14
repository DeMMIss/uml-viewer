(ns uml-viewer.grok-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.grok :as grok]))

(describe "grok"
  (it "wraps a user directive with standing diagram rules"
    (let [p (grok/wrap-prompt "add a Foo class")]
      (should (re-find #"topology-only" p))
      (should (re-find #"clj -M:crap" p))
      (should (re-find #"Directive:\nadd a Foo class" p))))

  (it "builds a headless grok command on a TTY"
    (let [cmd (grok/command-line "fix layout" {:cwd "/tmp/proj" :continue? false})]
      (should (some #{"-p"} cmd))
      (should (some #{"--yolo"} cmd))
      (should (some #{"--cwd"} cmd))
      (should-not (some #{"-c"} cmd))
      (should= "/usr/bin/script" (first cmd))))

  (it "continues the previous grok session when asked"
    (let [cmd (grok/command-line "again" {:cwd "/tmp" :continue? true})]
      (should (some #{"-c"} cmd))))

  (it "writes two Esc bytes and does not kill"
    (let [buf (java.io.ByteArrayOutputStream.)]
      (grok/write-escapes! buf)
      (should= [0x1b 0x1b] (mapv #(bit-and % 0xff) (.toByteArray buf)))))

  (it "names a tmux session and attaches Terminal to it"
    (let [args (grok/new-session-args "/tmp/proj")
          script (grok/osascript (grok/attach-command))]
      (should= "uml-viewer-grok" grok/session-name)
      (should= ["kill-session" "-t" "uml-viewer-grok"] (grok/kill-session-args))
      (should (some #{"new-session"} args))
      (should (some #{"--yolo"} args))
      (should (re-find #"tmux attach -t uml-viewer-grok" (grok/attach-command)))
      (should (re-find #"tell application \"Terminal\"" script))))

  (it "kills the tmux session on shutdown"
    (let [seen (atom [])]
      (with-redefs [grok/tmux! (fn [& args] (reset! seen args) 0)]
        (grok/shutdown-children!)
        (should= ["kill-session" "-t" "uml-viewer-grok"] @seen)))))
