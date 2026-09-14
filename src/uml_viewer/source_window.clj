(ns uml-viewer.source-window
  (:require [uml-viewer.source :as source]
            [uml-viewer.source-html :as source-html])
  (:import [java.awt Dimension]
           [javax.swing JEditorPane JFrame JScrollPane SwingUtilities]))

(defn- build-frame!
  [title body line]
  (let [frame (JFrame. title)
        editor (JEditorPane. "text/html" (source-html/source->html title body line))
        scroll (JScrollPane. editor)]
    (.setEditable editor false)
    (.setCaretPosition editor 0)
    (.setPreferredSize scroll (Dimension. 900 640))
    (.add (.getContentPane frame) scroll)
    (.pack frame)
    (.setLocationByPlatform frame true)
    (.setVisible frame true)
    (when line
      (SwingUtilities/invokeLater
        (fn []
          (.scrollToReference editor "here"))))
    frame))

(defn open-member-window!
  "Open an independent source window for a member.
  `source-impl` satisfies `LanguageSource`. `ident` is a source identity
  map, or `ns-name` plus `member-name`."
  ([source-impl ident]
   (when-let [{:keys [title body line]} (source/member-source source-impl ident)]
     (SwingUtilities/invokeLater
       (fn []
         (build-frame! title body line)))
     true))
  ([source-impl ns-name member-name]
   (open-member-window! source-impl {:ns ns-name :name member-name})))
