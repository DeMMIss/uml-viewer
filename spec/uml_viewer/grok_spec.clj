(ns uml-viewer.grok-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.grok :as grok]
            [uml-viewer.theme :as theme]))

(describe "grok"
  (it "names a tmux session and attaches Terminal to it"
    (let [args (grok/new-session-args "/tmp/proj")
          script (grok/osascript (grok/attach-command))
          [br bg bb] (grok/rgb-16 theme/bg)
          [gr gg gb] (grok/rgb-16 theme/gold)]
      (should= "uml-viewer-grok" grok/session-name)
      (should= ["kill-session" "-t" "uml-viewer-grok"] (grok/kill-session-args))
      (should (some #{"new-session"} args))
      (should (some #{"--yolo"} args))
      (should (some #{"--rules"} args))
      (should (some #{grok/standing-rules} args))
      (should (some #{"GROK_THEME=terminal"} args))
      (should (some #{"status"} args))
      (should (re-find #"tmux attach -t uml-viewer-grok" (grok/attach-command)))
      (should (re-find #"tell application \"Terminal\"" script))
      (should-not (re-find #"activate" script))
      (should (re-find #"^tell application \"Terminal\"\nlaunch" script))
      (should (re-find #"AXRaise" script))
      (should (re-find #"custom title of grokTab to \"Grok\"" script))
      (should (re-find #"return winID" script))
      (should (re-find (re-pattern (str "background color of grokTab to \\{" br ", " bg ", " bb "\\}"))
                       script))
      (should (re-find (re-pattern (str "cursor color of grokTab to \\{" gr ", " gg ", " gb "\\}"))
                       script))))

  (it "closes the Grok Terminal window by id and title"
    (let [script (grok/close-terminal-script "42")]
      (should (re-find #"exists process \"Terminal\"" script))
      (should (re-find #"whose id is 42" script))
      (should (re-find #"custom title of selected tab of w is \"Grok\"" script))
      (should (re-find #"close w saving no" script))))

  (it "scales theme RGB into Terminal's 16-bit colors"
    (should= [5654 7196 8224] (grok/rgb-16 [22 28 32]))
    (should= [0 65535 257] (grok/rgb-16 [0 255 1])))

  (it "kills the tmux session and closes Terminal on shutdown"
    (let [tmux-args (atom nil)
          scripts (atom [])]
      (reset! grok/!terminal-window-id "99")
      (with-redefs [grok/tmux! (fn [& args] (reset! tmux-args args) 0)
                    grok/run-osascript (fn [s] (swap! scripts conj s) "")]
        (grok/shutdown-children!)
        (should= ["kill-session" "-t" "uml-viewer-grok"] @tmux-args)
        (should= 1 (count @scripts))
        (should (re-find #"whose id is 99" (first @scripts)))
        (should-be-nil @grok/!terminal-window-id)))))
