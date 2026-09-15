(ns uml-viewer.sketch-spec
  (:require [quil.applet :as applet]
            [quil.core :as q]
            [speclj.core :refer :all]
            [uml-viewer.detail :as detail]
            [uml-viewer.draw :as draw]
            [uml-viewer.events :as events]
            [uml-viewer.geom :as geom]
            [uml-viewer.grok :as grok]
            [uml-viewer.ir :as ir]
            [uml-viewer.sketch :as sketch]
            [uml-viewer.source-window :as source-window])
  (:import [java.awt Frame]
           [java.util.concurrent CountDownLatch TimeUnit]
           [javax.swing JWindow]
           [processing.event Event MouseEvent]))

(defn- sk [sym]
  (ns-resolve 'uml-viewer.sketch sym))

(defn- call [sym & args]
  (apply (sk sym) args))

(defn- empty-bridge []
  {:applet nil :model nil :pick nil :closed? false :exiting false})

(defprotocol SurfaceOps
  (getNative [this])
  (setAlwaysOnTop [this on?]))

(defprotocol AppletOps
  (getSurface [this]))

(defrecord FakeSurface [native !top]
  SurfaceOps
  (getNative [_] native)
  (setAlwaysOnTop [_ on?]
    (when !top (reset! !top on?))
    on?))

(defrecord FakeApplet [finished surface]
  AppletOps
  (getSurface [_] surface))

(defrecord Finished [finished])

(defrecord FakeCurrent [mouseEvent])

(defprotocol ShiftOps
  (isShiftDown [this]))

(defrecord FakeShift [on?]
  ShiftOps
  (isShiftDown [_] on?))

(defn- scene []
  (events/compile-diagram
    (ir/normalize
      {:packages
       [{:id :p :label "P"
         :classes [{:id :a :name "A"
                    :ops [{:name "go" :args ["x"] :returns "void"}]}
                   {:id :b :name "B"}]}]
       :edges [{:from :a :to :b :kind :association}]})))

(defn- state []
  {:scene (scene)
   :selected nil
   :hover nil
   :cam-x 0
   :cam-y 0
   :path "examples/library.edn"
   :mtime 0})

(defn- class-xy [s id]
  (let [c (first (filter #(= id (:id %)) (:classes (:scene s))))]
    [(geom/cx (:rect c)) (geom/cy (:rect c))]))

(defn- a-model []
  (detail/model (:scene (state)) :a))

(defn- quiet-quil [f]
  (with-redefs [q/frame-rate (fn [_])
                q/color-mode (fn [_])
                q/smooth (fn [])
                q/create-font (fn [& _] :font)
                q/text-font (fn [& _])
                q/exit (fn [])
                q/width (fn [] 1500)
                q/height (fn [] 920)]
    (f)))

(describe "sketch"
  (before (reset! sketch/!bridge (empty-bridge)))

  (it "treats a missing or finished applet as dead"
    (should-not (call 'live? nil))
    (should-not (call 'live? (->Finished true)))
    (should (call 'live? (->Finished false)))
    (should-not (call 'live? "not-an-applet")))

  (it "reads the native window and swallows surface errors"
    (should= :native (call 'native-window (->FakeApplet false (->FakeSurface :native nil))))
    (should-be-nil (call 'native-window nil)))

  (it "brings a Frame or Window to the front and ignores other natives"
    (let [frame-calls (atom [])
          window-calls (atom [])
          frame (proxy [Frame] []
                  (setExtendedState [s] (swap! frame-calls conj [:extended s]))
                  (setVisible [v] (swap! frame-calls conj [:visible v]))
                  (toFront [] (swap! frame-calls conj :to-front))
                  (requestFocus [] (swap! frame-calls conj :focus) true)
                  (requestFocusInWindow [] (swap! frame-calls conj :focus-win) true))
          window (proxy [JWindow] []
                   (setVisible [v] (swap! window-calls conj [:visible v]))
                   (toFront [] (swap! window-calls conj :to-front))
                   (requestFocus [] (swap! window-calls conj :focus) true)
                   (requestFocusInWindow [] (swap! window-calls conj :focus-win) true))]
      (try
        (call 'front! frame)
        (call 'front! window)
        (should-be-nil (call 'front! :not-a-window))
        (should= [[:extended Frame/NORMAL] [:visible true] :to-front :focus :focus-win]
                 @frame-calls)
        (should= [[:visible true] :to-front :focus :focus-win]
                 @window-calls)
        (finally
          (.dispose frame)
          (.dispose window)))))

  (it "pins the detail card on top and leaves it alone without an applet"
    (let [top (atom nil)
          brought (atom nil)]
      (call 'pin-card! true)
      (reset! sketch/!bridge {:applet (->FakeApplet false (->FakeSurface :native top))})
      (with-redefs [uml-viewer.sketch/front! (fn [n] (reset! brought n))]
        (call 'pin-card! true)
        (should= true @top)
        (should= :native @brought)
        (reset! brought nil)
        (call 'pin-card! false)
        (should= false @top)
        (should-be-nil @brought))
      (reset! sketch/!bridge {:applet (->FakeApplet false nil)})
      (call 'pin-card! true)
      (reset! sketch/!bridge {:applet (reify AppletOps
                                        (getSurface [_] (throw (Exception. "gone"))))})
      (call 'pin-card! true)))

  (it "closes the detail applet and swallows exit errors"
    (let [exited (atom false)]
      (call 'close-detail-window!)
      (should-be-nil (:applet @sketch/!bridge))
      (reset! sketch/!bridge (assoc (empty-bridge) :applet (->Finished false)))
      (with-redefs [q/exit (fn [] (reset! exited true))]
        (call 'close-detail-window!)
        (should @exited)
        (should-be-nil (:applet @sketch/!bridge))
        (should (:exiting @sketch/!bridge)))
      (reset! sketch/!bridge (assoc (empty-bridge) :applet (->Finished false)))
      (with-redefs [q/exit (fn [] (throw (Exception. "exit")))]
        (call 'close-detail-window!)
        (should-be-nil (:applet @sketch/!bridge))
        (should (:exiting @sketch/!bridge)))))

  (it "runs a function on the swing thread"
    (let [done (CountDownLatch. 1)
          ran (atom false)]
      (call 'later! (fn []
                      (reset! ran true)
                      (.countDown done)))
      (should (.await done 2 TimeUnit/SECONDS))
      (should @ran)))

  (it "shuts down grok children then halts the VM"
    (let [order (atom [])]
      (with-redefs [grok/shutdown-children! (fn [] (swap! order conj :grok))
                    uml-viewer.sketch/halt-vm! (fn [] (swap! order conj :halt))]
        (call 'exit-app!)
        (should= [:grok :halt] @order))))

  (it "takes a one-shot flag from the bridge"
    (reset! sketch/!bridge (assoc (empty-bridge) :closed? true :pick :a))
    (should (call 'take-flag! :closed?))
    (should-not (:closed? @sketch/!bridge))
    (should= :a (call 'take-flag! :pick))
    (should-be-nil (:pick @sketch/!bridge))
    (should-be-nil (call 'take-flag! :pick)))

  (it "sets up the detail sketch state"
    (quiet-quil
      (fn []
        (should= {:scroll 0 :shown nil :hover nil}
                 (call 'detail-setup)))))

  (it "resets scroll when the shown class changes"
    (reset! sketch/!bridge {:model {:class {:id :b}}})
    (should= {:scroll 0 :shown :b :hover nil}
             (call 'detail-update {:scroll 40 :shown :a :hover :go}))
    (should= {:scroll 40 :shown :b :hover :go}
             (call 'detail-update {:scroll 40 :shown :b :hover :go})))

  (it "scrolls the detail card from a number, map, or junk amount"
    (reset! sketch/!bridge {:model (a-model)})
    (with-redefs [detail/height 10]
      (let [down (call 'detail-scroll {:scroll 0} 2)
            mapped (call 'detail-scroll {:scroll 0} {:count 1})
            empty-map (call 'detail-scroll {:scroll 0} {})
            junk (call 'detail-scroll {:scroll 12} :nope)]
        (should (pos? (:scroll down)))
        (should (pos? (:scroll mapped)))
        (should= 0 (:scroll empty-map))
        (should= 12 (:scroll junk))
        (should= 0 (:scroll (call 'detail-scroll {:scroll 0} -10))))))

  (it "draws the open model and skips drawing when none is open"
    (let [drawn (atom nil)]
      (with-redefs [draw/draw-detail (fn [& args] (reset! drawn args))]
        (call 'detail-draw {:scroll 3 :hover :go})
        (should-be-nil @drawn)
        (reset! sketch/!bridge {:model (a-model)})
        (call 'detail-draw {:scroll 3 :hover :go})
        (should= (a-model) (first @drawn))
        (should= 3 (second @drawn))
        (should= :go (nth @drawn 2)))))

  (it "tracks member hover and clears it when the pointer leaves"
    (let [model (a-model)
          go (first (filter :op-name (detail/rows model)))]
      (should= {:scroll 0 :hover nil} (call 'detail-mouse-moved {:scroll 0} {:y 0}))
      (reset! sketch/!bridge {:model model})
      (should= "go" (:hover (call 'detail-mouse-moved {:scroll 0}
                                 {:y (+ (:y go) 1)})))
      (should-be-nil (:hover (call 'detail-mouse-moved {:scroll 0} {:y 0})))
      (should= {:hover nil} (call 'detail-mouse-exited {:hover :go} :evt))))

  (it "opens source on click of a member and picks a related class"
    (let [model (a-model)
          rows (detail/rows model)
          go (first (filter :op-name rows))
          rel (first (filter #(= :rel (:kind %)) rows))
          opened (atom nil)
          go-y (+ (:y go) 1)]
      (should= {:scroll 0} (call 'detail-mouse-pressed {:scroll 0} {:y 0}))
      (reset! sketch/!bridge {:model model})
      (with-redefs [source-window/open-member-window! (fn [_src ns op]
                                                        (reset! opened [ns op]))]
        (call 'detail-mouse-pressed {:scroll 0} {:y go-y})
        (should= [(:ns model) "go"] @opened)
        (call 'detail-mouse-pressed {:scroll 0} {:y (+ (:y rel) 1)})
        (should= (:id rel) (:pick @sketch/!bridge))
        (reset! sketch/!bridge (assoc (empty-bridge) :model model :pick nil))
        (call 'detail-mouse-pressed {:scroll 0} {:y 0})
        (should-be-nil (:pick @sketch/!bridge))))))

(describe "sketch keys and lifecycle"
  (before (reset! sketch/!bridge (empty-bridge)))

  (it "closes the detail window on escape"
    (let [exited (atom false)]
      (with-redefs [q/exit (fn [] (reset! exited true))]
        (should= :state (call 'detail-key-pressed :state {:key :x}))
        (should-not @exited)
        (call 'detail-key-pressed :state {:key :esc})
        (should @exited)
        (should (:closed? @sketch/!bridge)))))

  (it "marks the card closed unless the main window is exiting it"
    (reset! sketch/!bridge (assoc (empty-bridge) :applet :ap :exiting true))
    (should= :s (call 'detail-on-close :s))
    (should-be-nil (:applet @sketch/!bridge))
    (should-not (:exiting @sketch/!bridge))
    (should-not (:closed? @sketch/!bridge))
    (reset! sketch/!bridge (assoc (empty-bridge) :applet :ap :exiting false))
    (call 'detail-on-close :s)
    (should (:closed? @sketch/!bridge)))

  (it "starts a detail sketch and stores the applet"
    (let [opts (atom nil)]
      (with-redefs [q/sketch (fn [& args]
                               (reset! opts (apply hash-map args))
                               :detail-applet)]
        (call 'start-detail-window!)
        (should= :detail-applet (:applet @sketch/!bridge))
        (should-not (:closed? @sketch/!bridge))
        (should-not (:exiting @sketch/!bridge))
        (should= "Class" (:title @opts))
        (should= [detail/width detail/height] (:size @opts))
        (quiet-quil
          (fn []
            (should= {:scroll 0 :shown nil :hover nil}
                     ((:setup @opts))))))))

  (it "starts the detail window once, and still clears the starting flag on error"
    (let [started (atom 0)
          pinned (atom [])]
      (with-redefs [uml-viewer.sketch/later! (fn [f] (f))
                    uml-viewer.sketch/start-detail-window! (fn [] (swap! started inc))
                    uml-viewer.sketch/pin-card! (fn [on?] (swap! pinned conj on?))]
        (call 'ensure-detail-window! {:class {:id :a}})
        (should= 1 @started)
        (should= [true] @pinned)
        (should= {:id :a} (get-in @sketch/!bridge [:model :class]))
        (should-not (:starting @sketch/!bridge))
        (reset! sketch/!bridge (assoc @sketch/!bridge :applet (->Finished false)))
        (call 'ensure-detail-window! {:class {:id :b}})
        (should= 1 @started)
        (reset! sketch/!bridge (assoc (empty-bridge) :starting true))
        (call 'ensure-detail-window! {:class {:id :c}})
        (should= 1 @started))
      (reset! sketch/!bridge (empty-bridge))
      (with-redefs [uml-viewer.sketch/later! (fn [f] (f))
                    uml-viewer.sketch/start-detail-window! (fn [] (throw (Exception. "boom")))
                    uml-viewer.sketch/pin-card! (fn [_])]
        (should-throw Exception (call 'ensure-detail-window! {:class {:id :a}}))
        (should-not (:starting @sketch/!bridge)))))

  (it "loads a path after configuring the main sketch"
    (quiet-quil
      (fn []
        (with-redefs [events/load-path (fn [p] {:path p})]
          (should= {:path "doc.edn"} (sketch/setup "doc.edn"))))))

  (it "applies closed and pick flags and keeps the detail model in sync"
    (let [s (assoc (state) :detail-id :a)
          closed (atom false)]
      (with-redefs [events/maybe-reload identity
                    uml-viewer.sketch/close-detail-window! (fn [] (reset! closed true))]
        (reset! sketch/!bridge (assoc (empty-bridge) :closed? true))
        (let [next (sketch/update-state s)]
          (should-not (:detail-id next))
          (should @closed))
        (reset! closed false)
        (reset! sketch/!bridge (assoc (empty-bridge) :pick :b))
        (let [next (sketch/update-state (assoc s :detail-id :a))]
          (should= :b (:detail-id next))
          (should= :b (get-in @sketch/!bridge [:model :class :id]))
          (should-not @closed))
        (reset! sketch/!bridge (empty-bridge))
        (sketch/update-state (dissoc s :detail-id))
        (should @closed)
        (reset! closed false)
        (reset! sketch/!bridge (empty-bridge))
        (sketch/update-state (assoc s :detail-id :missing))
        (should-not @closed)
        (should-be-nil (:model @sketch/!bridge))))))

(describe "sketch main window"
  (before (reset! sketch/!bridge (empty-bridge)))

  (it "opens the detail card on double-click of a class and unpins otherwise"
    (let [s (state)
          [x y] (class-xy s :a)
          ensured (atom nil)
          pinned (atom [])]
      (with-redefs [uml-viewer.sketch/ensure-detail-window! (fn [m] (reset! ensured m))
                    uml-viewer.sketch/pin-card! (fn [on?] (swap! pinned conj on?))]
        (let [single (call 'on-main-press s {:x x :y y :count 1})]
          (should= {:kind :class :id :a} (:selected single))
          (should-be-nil (:detail-id single))
          (should-be-nil @ensured)
          (should= [] @pinned))
        (let [next (call 'on-main-press s {:x x :y y :count 2})]
          (should= {:kind :class :id :a} (:selected next))
          (should= :a (:detail-id next))
          (should= :a (get-in @ensured [:class :id]))
          (should= [true] @pinned))
        (reset! ensured nil)
        (with-redefs [detail/model (fn [_ _] nil)
                      uml-viewer.sketch/ensure-detail-window! (fn [m] (reset! ensured m))
                      uml-viewer.sketch/pin-card! (fn [on?] (swap! pinned conj on?))]
          (call 'on-main-press s {:x x :y y :count 2})
          (should-be-nil @ensured)
          (should= [true true] @pinned))
        (call 'on-main-press s {:x 0 :y 0})
        (should= [true true false] @pinned))))

  (it "treats shift on the wheel event or the current applet as horizontal pan"
    (let [got (atom nil)
          wheel (MouseEvent. nil 0 MouseEvent/WHEEL Event/SHIFT 0 0 0 1)
          no-shift (MouseEvent. nil 0 MouseEvent/WHEEL 0 0 0 0 1)
          shift-ev (MouseEvent. nil 0 MouseEvent/WHEEL Event/SHIFT 0 0 0 1)]
      (with-redefs [q/width (fn [] 100)
                    q/height (fn [] 200)
                    events/on-scroll (fn [state event opts]
                                       (reset! got {:event event :opts opts})
                                       state)
                    applet/current-applet (fn [] (throw (Exception. "none")))]
        (call 'on-main-wheel :s wheel)
        (should= true (get-in @got [:opts :horizontal?]))
        (should= 100 (get-in @got [:opts :window-w]))
        (should= 200 (get-in @got [:opts :window-h]))
        (call 'on-main-wheel :s no-shift)
        (should-not (get-in @got [:opts :horizontal?]))
        (call 'on-main-wheel :s {:count 1})
        (should-not (get-in @got [:opts :horizontal?])))
      (with-redefs [q/width (fn [] 100)
                    q/height (fn [] 200)
                    events/on-scroll (fn [state event opts]
                                       (reset! got opts)
                                       state)
                    applet/current-applet
                    (fn [] (->FakeCurrent shift-ev))]
        (should (call 'applet-shift?))
        (call 'on-main-wheel :s {:count 1})
        (should (:horizontal? @got)))
      (with-redefs [applet/current-applet (fn [] (->FakeCurrent :not-a-mouse))]
        (should-not (call 'applet-shift?)))))

  (it "closes the detail window and exits the process"
    (let [closed (atom false)
          exited (atom false)]
      (with-redefs [uml-viewer.sketch/close-detail-window! (fn [] (reset! closed true))
                    uml-viewer.sketch/exit-app! (fn [] (reset! exited true))]
        (should= :s (call 'on-main-close :s))
        (should @closed)
        (should @exited))))

  (it "starts the main sketch and wires the handlers"
    (let [opts (atom nil)
          moved (atom nil)
          keyed (atom nil)]
      (with-redefs [q/sketch (fn [& args]
                               (reset! opts (apply hash-map args))
                               :main-applet)]
        (should= :main-applet (sketch/start! "doc.edn" :source-impl))
        (should= "UML viewer" (:title @opts))
        (should= [sketch/window-width sketch/window-height] (:size @opts))
        (quiet-quil
          (fn []
            (with-redefs [events/load-path (fn [p] {:path p})
                          events/on-move (fn [state x y]
                                           (reset! moved [state x y])
                                           state)
                          events/on-key (fn [state k dims]
                                          (reset! keyed [state k dims])
                                          state)
                          uml-viewer.sketch/on-main-close (fn [state] state)
                          uml-viewer.sketch/exit-app! (fn [])]
              (should= {:path "doc.edn"} ((:setup @opts)))
              ((:mouse-moved @opts) :s {:x 4 :y 5})
              (should= [:s 4 5] @moved)
              ((:key-pressed @opts) :s {:key :r})
              (should= [:s :r {:window-w 1500 :window-h 920}] @keyed)
              (should= :s ((:on-close @opts) :s))))))))
)
