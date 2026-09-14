(ns uml-viewer.source)

(defprotocol LanguageSource
  (locate [this ident]
    "Path to the file that should contain `ident`, or nil.")
  (extract [this source ident]
    "Source span for `ident` from `source` text, or nil.")
  (start-line [this source ident]
    "1-based line of `ident` in `source`, or nil.")
  (title [this ident]
    "Window title for this member."))

(defonce ^:private languages (atom {}))

(defn register!
  "Install `impl` as the extractor for `lang` (e.g. `:clojure`)."
  [lang impl]
  (swap! languages assoc lang impl)
  lang)

(defn lookup
  [lang]
  (get @languages lang))

(defn member-source
  "Locate a member. `ident` is a map with at least `:name`.
  `:lang` selects the extractor (default `:clojure`). Returns
  `{:title :file :body :line :lang}` — `body` is the whole file,
  `line` is where the member starts — or nil."
  ([ident]
   (member-source (or (:lang ident) :clojure) ident))
  ([lang ident]
   (when-let [impl (lookup lang)]
     (when-let [path (locate impl ident)]
       (let [src (slurp path)]
         (when (extract impl src ident)
           {:title (str path ":" (or (start-line impl src ident) 1))
            :file path
            :body src
            :line (start-line impl src ident)
            :lang lang}))))))
