(ns uml-viewer.grok-window-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.grok :as grok]
            [uml-viewer.grok-window :as gw])
  (:import [java.awt Component Container]
           [java.awt.event ActionEvent KeyEvent]
           [java.util.concurrent CountDownLatch TimeUnit]
           [javax.swing JButton JFrame JLabel JTextArea Timer UIManager]))

(defn- call [sym & args]
  (apply (ns-resolve 'uml-viewer.grok-window sym) args))

(defn- ctx
  ([] (ctx {}))
  ([overrides]
   (merge {:input (JTextArea.)
           :log (JTextArea.)
           :send-btn (JButton. "Send")
           :interrupt-btn (JButton. "Interrupt")
           :header (JLabel. "idle")
           :edn-path "examples/uml-viewer.edn"
           :cwd "/tmp/proj"
           :!busy (atom false)
           :!continue (atom false)
           :!proc (atom :proc)
           :!transcript (atom "")
           :!emitted (atom [])
           :!started (atom 0)
           :!last-out (atom 0)
           :!timer (atom nil)}
          overrides)))

(defn- stop-timer [c]
  (call 'stop-heartbeat! (:!timer c)))

(defn- widgets [^Component root]
  (tree-seq (fn [c] (instance? Container c))
            (fn [^Container c] (seq (.getComponents c)))
            root))

(defn- all-of [root cls]
  (filter #(instance? cls %) (widgets root)))

(defn- button [root label]
  (first (filter #(= label (.getText ^JButton %)) (all-of root JButton))))

(defn- key-event [src code mods]
  (KeyEvent. src KeyEvent/KEY_PRESSED (System/currentTimeMillis) mods code
             (char 0)))

(describe "grok-window later!"
  (it "runs a function on the swing thread"
    (let [done (CountDownLatch. 1)
          ran (atom false)]
      (call 'later! (fn []
                      (reset! ran true)
                      (.countDown done)))
      (should (.await done 2 TimeUnit/SECONDS))
      (should @ran))))

(describe "grok-window widgets"
  (around [it]
    (with-redefs [uml-viewer.grok-window/later! (fn [f] (f))]
      (it)))

  (it "styles a text area for the dark grok card"
    (let [area (JTextArea.)]
      (should (identical? area (call 'style-area area)))
      (should (.getLineWrap area))
      (should (.getWrapStyleWord area))
      (should= 13 (.getSize (.getFont area)))))

  (it "appends log text and parks the caret at the end"
    (let [log (doto (JTextArea.) (.setText "ab"))]
      (call 'append! log "cd")
      (should= "abcd" (.getText log))
      (should= 4 (.getCaretPosition log))))

  (it "enables and disables widgets on the swing thread"
    (let [btn (JButton. "x")]
      (call 'set-enabled! [btn] false)
      (should-not (.isEnabled btn))
      (call 'set-enabled! [btn] true)
      (should (.isEnabled btn))))

  (it "sets the busy and idle header copy"
    (let [header (JLabel. "")]
      (call 'set-status! header true)
      (should (.contains (.getText header) "running"))
      (call 'set-status! header false)
      (should (.contains (.getText header) "Enter to send")))))

(describe "grok-window heartbeat"
  (around [it]
    (with-redefs [uml-viewer.grok-window/later! (fn [f] (f))]
      (it)))

  (it "ignores a missing timer and stops a running one"
    (let [!t (atom nil)
          t (Timer. 9999 (reify java.awt.event.ActionListener
                           (actionPerformed [_ _])))]
      (call 'stop-heartbeat! !t)
      (should-be-nil @!t)
      (.start t)
      (reset! !t t)
      (call 'stop-heartbeat! !t)
      (should-be-nil @!t)
      (should-not (.isRunning t))))

  (it "writes a working line only when the run has gone silent"
    (let [c (ctx)
          now (System/currentTimeMillis)]
      (call 'heartbeat-tick c)
      (should= "" (.getText (:log c)))
      (reset! (:!busy c) true)
      (reset! (:!started c) (- now 12000))
      (reset! (:!last-out c) now)
      (call 'heartbeat-tick c)
      (should= "" (.getText (:log c)))
      (reset! (:!last-out c) (- now 5000))
      (call 'heartbeat-tick c)
      (should (.contains (.getText (:log c)) "◎ working "))))

  (it "starts a timer that ticks the heartbeat and replaces an old one"
    (let [c (assoc (ctx) :!busy (atom true))
          old (doto (Timer. 9999 (reify java.awt.event.ActionListener
                                   (actionPerformed [_ _])))
                (.start))]
      (reset! (:!timer c) old)
      (reset! (:!started c) (- (System/currentTimeMillis) 12000))
      (reset! (:!last-out c) (- (System/currentTimeMillis) 5000))
      (try
        (call 'start-heartbeat! c)
        (should-not (identical? old @(:!timer c)))
        (should-not (.isRunning old))
        (should (.isRunning @(:!timer c)))
        (doseq [l (.getActionListeners @(:!timer c))]
          (.actionPerformed l (ActionEvent. @(:!timer c) 0 "tick")))
        (should (.contains (.getText (:log c)) "◎ working "))
        (finally
          (stop-timer c))))))

(describe "grok-window send"
  (around [it]
    (with-redefs [uml-viewer.grok-window/later! (fn [f] (f))]
      (it)))

  (it "records output without echoing it as a status line"
    (let [c (ctx)]
      (reset! (:!last-out c) 0)
      (call 'on-chunk c "I'll inspect layout next.\n")
      (should (pos? @(:!last-out c)))
      (should= "I'll inspect layout next.\n" (.getText (:log c)))
      (should-not (.contains (.getText (:log c)) "◎ "))))

  (it "restores the form after a successful run and keeps going"
    (let [c (ctx)
          touched (atom nil)
          focused (atom false)]
      (reset! (:!busy c) true)
      (.setEnabled (:input c) false)
      (.setEnabled (:send-btn c) false)
      (.setEnabled (:interrupt-btn c) true)
      (with-redefs [grok/touch-edn! (fn [p] (reset! touched p))
                    uml-viewer.grok-window/later! (fn [f]
                                                    (reset! focused true)
                                                    (f))]
        (call 'on-finished c {:exit 0})
        (should= "examples/uml-viewer.edn" @touched)
        (should @(:!continue c))
        (should-not @(:!busy c))
        (should (.isEnabled (:input c)))
        (should-not (.isEnabled (:interrupt-btn c)))
        (should (.contains (.getText (:header c)) "Enter to send"))
        (should (.contains (.getText (:log c)) "(exit 0"))
        (should @focused))))

  (it "does not continue after a failed run"
    (let [c (ctx)]
      (reset! (:!busy c) true)
      (with-redefs [grok/touch-edn! (fn [_])]
        (call 'on-finished c {:exit 1})
        (should-not @(:!continue c))
        (should-not @(:!busy c))
        (should (.contains (.getText (:log c)) "(exit 1"))))))

(describe "grok-window dispatch"
  (around [it]
    (with-redefs [uml-viewer.grok-window/later! (fn [f] (f))]
      (it)))

  (it "ignores blank or in-flight prompts"
    (let [c (ctx)
          ran (atom 0)]
      (with-redefs [grok/run-directive! (fn [_] (swap! ran inc))]
        (call 'send! c)
        (.setText (:input c) "   ")
        (call 'send! c)
        (reset! (:!busy c) true)
        (.setText (:input c) "do it")
        (call 'send! c)
        (should= 0 @ran))))

  (it "starts a grok run and wires output callbacks"
    (let [c (ctx)
          sent (atom nil)]
      (.setText (:input c) "  fix layout  ")
      (reset! (:!continue c) true)
      (try
        (with-redefs [grok/run-directive! (fn [opts] (reset! sent opts))]
          (call 'send! c)
          (should @(:!busy c))
          (should= "" (.getText (:input c)))
          (should (.isEnabled (:input c)))
          (should (.isEnabled (:interrupt-btn c)))
          (should (.contains (.getText (:log c)) "▸ fix layout"))
          (should= "fix layout" (:prompt @sent))
          (should= "/tmp/proj" (:cwd @sent))
          (should (:continue? @sent))
          (should (identical? (:!proc c) (:!proc @sent)))
          ((:on-out @sent) "I'll inspect layout next.\n")
          (should (.contains (.getText (:log c)) "I'll inspect layout next."))
          (with-redefs [grok/touch-edn! (fn [_])]
            ((:on-done @sent) {:exit 0}))
          (should @(:!continue c))
          (should-not @(:!busy c)))
        (finally
          (stop-timer c)))))

  (it "logs an interrupt against the live process"
    (let [c (ctx)
          hit (atom nil)]
      (with-redefs [grok/interrupt! (fn [p] (reset! hit p))]
        (call 'on-interrupt c)
        (should (identical? (:!proc c) @hit))
        (should (.contains (.getText (:log c)) "interrupt Esc Esc")))))

  (it "forwards Esc to grok while a run is in flight"
    (let [c (ctx)
          hit (atom nil)
          esc (key-event (:input c) KeyEvent/VK_ESCAPE 0)]
      (reset! (:!busy c) true)
      (with-redefs [grok/interrupt! (fn [p] (reset! hit p))]
        (call 'on-enter c esc)
        (should (.isConsumed esc))
        (should (identical? (:!proc c) @hit)))))

  (it "sends on enter and leaves shift-enter alone"
    (let [c (ctx)
          ran (atom 0)
          enter (key-event (:input c) KeyEvent/VK_ENTER 0)
          shift (key-event (:input c) KeyEvent/VK_ENTER KeyEvent/SHIFT_DOWN_MASK)
          other (key-event (:input c) KeyEvent/VK_A 0)]
      (.setText (:input c) "go")
      (with-redefs [grok/run-directive! (fn [_] (swap! ran inc))]
        (call 'on-enter c other)
        (should= 0 @ran)
        (should-not (.isConsumed other))
        (call 'on-enter c shift)
        (should= 0 @ran)
        (should-not (.isConsumed shift))
        (try
          (call 'on-enter c enter)
          (should= 1 @ran)
          (should (.isConsumed enter))
          (finally
            (stop-timer c)))))))

(describe "grok-window frame"
  (around [it]
    (with-redefs [uml-viewer.grok-window/later! (fn [f] (f))]
      (it)))

  (it "builds a window that sends, interrupts, and treats enter as send"
    (let [sent (atom nil)
          interrupted (atom nil)
          frame (atom nil)]
      (with-redefs [grok/run-directive! (fn [opts] (reset! sent opts))
                    grok/interrupt! (fn [p] (reset! interrupted p))
                    uml-viewer.grok-window/start-heartbeat! (fn [_])]
        (try
          (reset! frame (call 'build-frame! {:edn-path "doc.edn" :cwd "/tmp/proj"}))
          (should (instance? JFrame @frame))
          (should= "Grok" (.getTitle @frame))
          (let [input (first (filter #(.isEditable ^JTextArea %)
                                     (all-of @frame JTextArea)))
                send (button @frame "Send")
                interrupt (button @frame "Interrupt")]
            (should input)
            (should send)
            (should interrupt)
            (should-not (.isEnabled interrupt))
            (.setText input "again")
            (.dispatchEvent input (key-event input KeyEvent/VK_ENTER
                                             KeyEvent/SHIFT_DOWN_MASK))
            (should-be-nil @sent)
            (.setText input "fix sketch")
            (.doClick send)
            (should= "fix sketch" (:prompt @sent))
            (should= "/tmp/proj" (:cwd @sent))
            (.doClick interrupt)
            (should @interrupted))
          (finally
            (when @frame (.dispose @frame)))))))

  (it "defaults the working directory to the process cwd"
    (let [sent (atom nil)
          frame (atom nil)]
      (with-redefs [grok/run-directive! (fn [opts] (reset! sent opts))
                    uml-viewer.grok-window/start-heartbeat! (fn [_])]
        (try
          (reset! frame (call 'build-frame! {:edn-path "doc.edn"}))
          (let [input (first (filter #(.isEditable ^JTextArea %)
                                     (all-of @frame JTextArea)))]
            (.setText input "hello")
            (.doClick (button @frame "Send"))
            (should= (System/getProperty "user.dir") (:cwd @sent)))
          (finally
            (when @frame (.dispose @frame)))))))

  (it "installs the cross-platform look and feel"
    (let [prev (UIManager/getLookAndFeel)]
      (try
        (call 'apply-laf!)
        (should= (UIManager/getCrossPlatformLookAndFeelClassName)
                 (.getName (class (UIManager/getLookAndFeel))))
        (finally
          (UIManager/setLookAndFeel prev)))))

  (it "opens the window on the swing thread even if look-and-feel fails"
    (let [built (atom nil)]
      (with-redefs [uml-viewer.grok-window/apply-laf! (fn [] (throw (Exception. "laf")))
                    uml-viewer.grok-window/build-frame! (fn [opts]
                                                          (reset! built opts)
                                                          :frame)]
        (gw/open! {:edn-path "doc.edn" :cwd "/tmp"})
        (should= {:edn-path "doc.edn" :cwd "/tmp"} @built))))

  (it "applies look-and-feel then builds the frame"
    (let [order (atom [])]
      (with-redefs [uml-viewer.grok-window/apply-laf! (fn [] (swap! order conj :laf))
                    uml-viewer.grok-window/build-frame! (fn [opts]
                                                          (swap! order conj opts)
                                                          :frame)]
        (gw/open! {:edn-path "x.edn"})
        (should= [:laf {:edn-path "x.edn"}] @order)))))
