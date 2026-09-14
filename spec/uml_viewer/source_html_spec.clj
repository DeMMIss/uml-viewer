(ns uml-viewer.source-html-spec
  (:require [clojure.string :as str]
            [speclj.core :refer :all]
            [uml-viewer.source-html :as html]))

(describe "source html"
  (it "escapes html-sensitive characters"
    (should= "&lt;a&amp;b&gt;" (html/html-escape "<a&b>")))

  (it "colorizes strings, keywords, and comments"
    (let [out (html/colorize-clojure-html "(println \"x\" :k) ; c")]
      (should (str/includes? out "class='str'"))
      (should (str/includes? out "class='kw'"))
      (should (str/includes? out "class='cmt'"))))

  (it "renders a titled document with line numbers"
    (let [doc (html/source->html "demo.clj" "(ns demo)\n")]
      (should (str/includes? doc "<div class='hdr'>demo.clj</div>"))
      (should (str/includes? doc "class='ln'>1</td>"))))

  (it "anchors and highlights the member line"
    (let [doc (html/source->html "f.clj" "(ns f)\n(defn go [])\n" 2)]
      (should (str/includes? doc "name='here'"))
      (should (str/includes? doc "class='hl'")))))
