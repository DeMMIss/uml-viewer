(ns uml-viewer.graph)

(defprotocol LanguageGraph
  (scan [this root opts]
    "Project graph of classes and edges from source under `root`.
     `opts` is a map; `:prefix` is the project namespace prefix.
     Returns `{:classes [{:id :name :ns :stereotype}]
               :edges [{:from :to :kind}]}`."))

(defonce ^:private languages (atom {}))

(defn register!
  "Install `impl` as the graph scanner for `lang` (e.g. `:clojure`)."
  [lang impl]
  (swap! languages assoc lang impl)
  lang)

(defn lookup
  [lang]
  (get @languages lang))

(defn scan-project
  "Scan `root` with the registered scanner for `lang` (default `:clojure`)."
  ([root] (scan-project :clojure root {}))
  ([lang-or-root root-or-opts]
   (if (map? root-or-opts)
     (scan-project :clojure lang-or-root root-or-opts)
     (scan-project lang-or-root root-or-opts {})))
  ([lang root opts]
   (if-let [impl (lookup lang)]
     (scan impl root opts)
     (throw (ex-info (str "no LanguageGraph for " lang) {:lang lang})))))
