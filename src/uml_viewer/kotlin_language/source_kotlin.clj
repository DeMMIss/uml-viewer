(ns uml-viewer.kotlin-language.source-kotlin
  "Metadata-only Kotlin source navigation. This namespace has no compiler dependency."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [uml-viewer.source :as source]))

(defn- safe-file [{:keys [file source-root]}]
  (when (and file source-root)
    (let [root (.getCanonicalFile (io/file source-root))
          candidate (.getCanonicalFile (io/file file))]
      (when (and (.isFile candidate)
                 (.startsWith (.toPath candidate) (.toPath root)))
        (.getPath candidate)))))

(defn- lines [text]
  (str/split (or text "") #"\R" -1))

(defrecord KotlinSource []
  source/LanguageSource
  (locate [_ ident]
    (safe-file ident))
  (extract [_ text {:keys [line end-line]}]
    (let [xs (lines text)
          start (when line (dec line))
          end (min (count xs) (or end-line line 0))]
      (when (and start (<= 0 start) (< start (count xs)) (< start end))
        (str/join "\n" (subvec (vec xs) start end)))))
  (start-line [_ text {:keys [line]}]
    (when (and (integer? line) (pos? line) (<= line (count (lines text))))
      line))
  (title [_ {:keys [ns name]}]
    (str ns (when (seq (str name)) (str "/" name)))))

(def impl (->KotlinSource))

(source/register! :kotlin impl)
