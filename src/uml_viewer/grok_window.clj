(ns uml-viewer.grok-window
  (:require [uml-viewer.grok :as grok]
            [uml-viewer.grok-status :as grok-status])
  (:import [java.awt BorderLayout Color Dimension Font Insets]
           [java.awt.event ActionListener KeyAdapter KeyEvent]
           [javax.swing
            BorderFactory JButton JFrame JLabel JPanel JScrollPane
            JTextArea SwingUtilities Timer UIManager]))

(def ^:private bg (Color. 22 28 32))
(def ^:private panel (Color. 26 36 40))
(def ^:private ink (Color. 236 236 228))
(def ^:private muted (Color. 157 184 168))
(def ^:private gold (Color. 232 196 72))
(def ^:private mono (Font. "Menlo" Font/PLAIN 13))
(def ^:private sans (Font. "SansSerif" Font/PLAIN 13))

(defn- later! [f]
  (SwingUtilities/invokeLater f))

(defn- apply-laf! []
  (UIManager/setLookAndFeel (UIManager/getCrossPlatformLookAndFeelClassName)))

(defn- style-area [area]
  (doto area
    (.setBackground panel)
    (.setForeground ink)
    (.setCaretColor gold)
    (.setFont mono)
    (.setLineWrap true)
    (.setWrapStyleWord true)
    (.setMargin (Insets. 8 8 8 8))))

(defn- append! [^JTextArea log text]
  (later!
    (fn []
      (.append log text)
      (.setCaretPosition log (.getLength (.getDocument log))))))

(defn- set-enabled! [widgets on?]
  (doseq [w widgets]
    (later! #(.setEnabled w (boolean on?)))))

(defn- set-status! [^JLabel header busy?]
  (later!
    #(.setText header (if busy?
                        "  Grok  —  running (Interrupt sends Esc)"
                        "  Grok  —  Enter to send, Shift-Enter for a new line"))))

(defn- stop-heartbeat! [!timer]
  (when-let [t @!timer]
    (.stop t)
    (reset! !timer nil)))

(defn- heartbeat-tick [{:keys [log !busy !started !last-out]}]
  (when @!busy
    (let [now (System/currentTimeMillis)
          silent (- now @!last-out)
          elapsed (quot (- now @!started) 1000)]
      (when (> silent 4000)
        (append! log (grok-status/format-working elapsed))))))

(defn- start-heartbeat! [ctx]
  (stop-heartbeat! (:!timer ctx))
  (let [t (Timer. 5000
            (reify ActionListener
              (actionPerformed [_ _]
                (heartbeat-tick ctx))))]
    (.start t)
    (reset! (:!timer ctx) t)))

(defn- on-chunk [{:keys [log !last-out]} chunk]
  (reset! !last-out (System/currentTimeMillis))
  (append! log chunk))

(defn- on-finished [{:keys [log input send-btn interrupt-btn header edn-path
                            !busy !continue !timer]} {:keys [exit]}]
  (stop-heartbeat! !timer)
  (grok/touch-edn! edn-path)
  (when (zero? exit)
    (reset! !continue true))
  (reset! !busy false)
  (set-status! header false)
  (append! log (str "\n(exit " exit " — diagram will reload)\n"))
  (set-enabled! [input send-btn] true)
  (set-enabled! [interrupt-btn] false)
  (later! #(.requestFocusInWindow input)))

(defn- send! [ctx]
  (let [{:keys [input log send-btn interrupt-btn header cwd
                !busy !continue !proc
                !started !last-out !timer]} ctx
        text (.trim (.getText input))]
    (when (and (seq text) (not @!busy))
      (reset! !busy true)
      (reset! !started (System/currentTimeMillis))
      (reset! !last-out (System/currentTimeMillis))
      (set-enabled! [interrupt-btn] true)
      (set-status! header true)
      (append! log (str "\n▸ " text "\n\n"))
      (.setText input "")
      (start-heartbeat! {:log log :!busy !busy :!started !started
                         :!last-out !last-out :!timer !timer})
      (grok/run-directive!
        {:prompt text
         :cwd cwd
         :continue? @!continue
         :!proc !proc
         :on-out #(on-chunk ctx %)
         :on-done #(on-finished ctx %)}))))

(defn- on-interrupt [{:keys [log !proc]}]
  (grok/interrupt! !proc)
  (append! log "\n(interrupt Esc Esc)\n"))

(defn- on-enter [ctx e]
  (cond
    (and (= (.getKeyCode e) KeyEvent/VK_ESCAPE)
         @(:!busy ctx))
    (do
      (.consume e)
      (on-interrupt ctx))

    (and (= (.getKeyCode e) KeyEvent/VK_ENTER)
         (not (.isShiftDown e)))
    (do
      (.consume e)
      (send! ctx))))

(defn- build-frame! [{:keys [edn-path cwd]}]
  (let [frame (JFrame. "Grok")
        log (doto (JTextArea.)
              (style-area)
              (.setEditable false)
              (.setText (str "Directives change the code. After each run the IR is\n"
                             "touched so the viewer reloads. Ask to recompute CRAP\n"
                             "or mutation when you want fresh metrics.\n")))
        input (doto (JTextArea. 4 40) (style-area))
        send-btn (doto (JButton. "Send")
                   (.setFont sans)
                   (.setBackground gold)
                   (.setForeground bg))
        interrupt-btn (doto (JButton. "Interrupt")
                        (.setFont sans)
                        (.setEnabled false))
        header (doto (JLabel. "  Grok  —  Enter to send, Shift-Enter for a new line")
                 (.setFont sans)
                 (.setForeground muted)
                 (.setBackground bg)
                 (.setOpaque true))
        buttons (doto (JPanel. (BorderLayout. 8 0))
                  (.setBackground bg)
                  (.add send-btn BorderLayout/NORTH)
                  (.add interrupt-btn BorderLayout/SOUTH))
        ctx {:input input
             :log log
             :send-btn send-btn
             :interrupt-btn interrupt-btn
             :header header
             :edn-path edn-path
             :cwd (or cwd (System/getProperty "user.dir"))
             :!busy (atom false)
             :!continue (atom false)
             :!proc (atom nil)
             :!started (atom 0)
             :!last-out (atom 0)
             :!timer (atom nil)}
        south (doto (JPanel. (BorderLayout. 8 8))
                (.setBackground bg)
                (.setBorder (BorderFactory/createEmptyBorder 8 8 8 8))
                (.add (JScrollPane. input) BorderLayout/CENTER)
                (.add buttons BorderLayout/EAST))]
    (.addActionListener send-btn
      (reify ActionListener
        (actionPerformed [_ _] (send! ctx))))
    (.addActionListener interrupt-btn
      (reify ActionListener
        (actionPerformed [_ _] (on-interrupt ctx))))
    (.addKeyListener input
      (proxy [KeyAdapter] []
        (keyPressed [e] (on-enter ctx e))))
    (let [content (.getContentPane frame)]
      (.setLayout content (BorderLayout.))
      (.setBackground content bg)
      (.add content header BorderLayout/NORTH)
      (.add content
            (doto (JScrollPane. log)
              (.setBorder (BorderFactory/createEmptyBorder)))
            BorderLayout/CENTER)
      (.add content south BorderLayout/SOUTH))
    (doto frame
      (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
      (.setPreferredSize (Dimension. 520 720))
      (.pack)
      (.setLocation 20 40)
      (.setVisible true))
    (.requestFocusInWindow input)
    frame))

(defn open!
  "Show the Grok directive window beside the viewer."
  [opts]
  (try
    (apply-laf!)
    (catch Exception _))
  (later!
    (fn []
      (build-frame! opts))))
