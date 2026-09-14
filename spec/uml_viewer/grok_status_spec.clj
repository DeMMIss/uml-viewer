(ns uml-viewer.grok-status-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.grok-status :as status]))

(describe "grok-status"
  (it "picks I-status sentences from grok prose"
    (let [text (str "I'll inspect layout next.\n"
                    "I'm updating the IR.\n")]
      (should= ["I'll inspect layout next." "I'm updating the IR."]
               (status/latest-status text))))

  (it "ignores chrome and timer lines"
    (let [text "thinking 3.2s\nesc to interrupt\nWorked for 12s\n"]
      (should= [] (status/latest-status text))))

  (it "formats a status line for the log"
    (should= "◎ I'll inspect layout.\n"
             (status/format-status-line "I'll inspect layout."))
    (should= "◎ working 12s\n" (status/format-working 12))))
