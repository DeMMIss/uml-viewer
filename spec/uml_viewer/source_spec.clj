(ns uml-viewer.source-spec
  (:require [clojure.string :as str]
            [speclj.core :refer :all]
            [uml-viewer.source :as source]
            [uml-viewer.source.clojure :as clj-src]))

(describe "source protocol"
  (it "returns nil for an unknown language"
    (should-be-nil (source/member-source {:lang :cobol :name "FOO"})))

  (it "dispatches clojure by :lang"
    (let [found (source/member-source {:lang :clojure
                                       :ns "uml-viewer.geom"
                                       :name "rect"})]
      (should= :clojure (:lang found))
      (should= "src/uml_viewer/geom.clj" (:file found))
      (should (str/starts-with? (:body found) "(ns uml-viewer.geom"))
      (should (str/includes? (:body found) "(defn rect"))
      (should (pos? (:line found))))))

(describe "clojure extractor"
  (it "maps a namespace to a source file under src/"
    (should= "src/uml_viewer/geom.clj"
             (clj-src/ns->source-path "uml-viewer.geom")))

  (it "extracts a public defn by name"
    (let [body (clj-src/extract-member (slurp "src/uml_viewer/geom.clj") "rect")]
      (should (str/starts-with? body "(defn rect"))
      (should (str/includes? body "[x y w h]"))))

  (it "extracts a private defn-"
    (let [found (source/member-source {:ns "uml-viewer.detail" :name "rel-phrase"})]
      (should (re-find #"src/uml_viewer/detail.clj:" (:title found)))
      (should (str/includes? (:body found) "(defn- rel-phrase"))
      (should= (clj-src/member-line (slurp "src/uml_viewer/detail.clj") "rel-phrase")
               (:line found))))

  (it "extracts names that end with ! or ?"
    (let [src "(ns demo)\n(defn- live? [applet]\n  true)\n(defn pin-card! [on?]\n  on?)\n"
          q (clj-src/extract-member src "live?")
          bang (clj-src/extract-member src "pin-card!")]
      (should (str/starts-with? q "(defn- live?"))
      (should (str/starts-with? bang "(defn pin-card!"))))

  (it "returns nil for an unknown member"
    (should-be-nil (source/member-source {:ns "uml-viewer.geom" :name "no-such-fn"}))))
