(ns uml-viewer.ir
  (:require [clojure.string :as str]
            [clojure.edn :as edn]))

(defn- as-id [x]
  (cond
    (keyword? x) x
    (string? x) (keyword (str/replace (str/lower-case x) #"\s+" "-"))
    (symbol? x) (keyword (name x))
    :else (throw (ex-info "id must be a keyword or string" {:value x}))))

(defn- as-crap [x]
  (cond
    (nil? x) nil
    (number? x) {:mu (double x) :max (double x) :sigma 0.0}
    (map? x) {:mu (some-> (:mu x) double)
              :max (double (or (:max x) (:mu x) 0))
              :sigma (double (or (:sigma x) (:sd x) 0))}
    :else (throw (ex-info "crap must be a number or {:mu :max :sigma}" {:value x}))))

(defn- as-member [x]
  (cond
    (string? x) {:text x}
    (map? x) {:text (or (:text x)
                        (str (:name x)
                             (when (seq (:args x))
                               (str "(" (str/join ", " (:args x)) ")"))
                             (when (:type x) (str " : " (:type x)))
                             (when (:returns x) (str " : " (:returns x)))))}
    :else (throw (ex-info "member must be a string or map" {:value x}))))

(defn- as-class [c]
  (let [name (or (:name c) (some-> (:id c) name))]
    (when-not name
      (throw (ex-info "class needs :name or :id" {:class c})))
    {:id (as-id (or (:id c) name))
     :name name
     :stereotype (:stereotype c)
     :crap (as-crap (:crap c))
     :fields (mapv as-member (:fields c))
     :ops (mapv as-member (:ops c))}))

(defn- as-package [p]
  (let [label (or (:label p) (some-> (:id p) name))]
    (when-not label
      (throw (ex-info "package needs :label or :id" {:package p})))
    {:id (as-id (or (:id p) label))
     :label label
     :crap (as-crap (:crap p))
     :classes (mapv as-class (:classes p))}))

(defn- as-edge [e]
  (when-not (and (:from e) (:to e))
    (throw (ex-info "edge needs :from and :to" {:edge e})))
  {:from (as-id (:from e))
   :to (as-id (:to e))
   :kind (keyword (or (:kind e) :association))
   :label (:label e)})

(defn normalize [raw]
  (let [diagram {:title (or (:title raw) "UML")
                 :direction (keyword (or (:direction raw) :tb))
                 :packages (mapv as-package (:packages raw))
                 :edges (mapv as-edge (:edges raw))}
        class-ids (mapcat (fn [p] (map :id (:classes p))) (:packages diagram))
        dup (ffirst (filter #(> (val %) 1) (frequencies class-ids)))]
    (when dup
      (throw (ex-info (str "duplicate class id: " dup) {:id dup})))
    (let [ids (set class-ids)]
      (doseq [e (:edges diagram)]
        (when-not (ids (:from e))
          (throw (ex-info (str "edge :from unknown class " (:from e)) {:edge e})))
        (when-not (ids (:to e))
          (throw (ex-info (str "edge :to unknown class " (:to e)) {:edge e})))))
    diagram))

(defn read-diagram [s]
  (normalize (edn/read-string s)))

(defn load-document [path]
  (let [raw (edn/read-string (slurp path))]
    (if (:diagrams raw)
      {:title (or (:title raw) "UML")
       :diagrams (mapv normalize (:diagrams raw))}
      {:title (or (:title raw) "UML")
       :diagrams [(normalize raw)]})))

(defn load-diagram [path]
  (first (:diagrams (load-document path))))

(defn class-index [diagram]
  (into {}
        (for [p (:packages diagram)
              c (:classes p)]
          [(:id c) (assoc c :package (:id p))])))

(defn package-of [diagram class-id]
  (:package (get (class-index diagram) class-id)))

;; clj-mutate-manifest-begin
;; {:version 2, :hash-algorithm :sha256-source-v1, :verified? true, :tested-at "2026-09-13T12:12:28.41724-05:00", :module-hash "1ccc91b041b74f160bde631f384e02fbde3c3de7f4c818fc843df575c2c8789c", :provenance {:mutation-rules-version "3", :test-command "clj -M:spec --tag ~no-mutate", :test-roots ["spec"], :test-profile-fingerprint "01f72bad2b4b9bac72353eccfb667d7ea8820b5b0699dd36256c4d99b23f89ae"}, :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "7a8685b92b4115fb82ff4e129c99a17874c73d4d0fd3f87d3e6dca0d702c704d"} {:id "defn-/as-id", :kind "defn-", :line 5, :end-line 10, :hash "d27a86bf05e9f87b8a113e7ccaca8ff1a2a37bd36bfe6f3817aa500e90c7a904"} {:id "defn-/as-crap", :kind "defn-", :line 12, :end-line 19, :hash "9e00e3dbc1033f6f86efc4a1057724bacd149b61c7247314bb8e366868c0bdde"} {:id "defn-/as-member", :kind "defn-", :line 21, :end-line 30, :hash "4dfc590ee66dde2b20341bd82adb6131b85a778d2e5a7cb4cc882dba58d8f644"} {:id "defn-/as-class", :kind "defn-", :line 32, :end-line 41, :hash "ba46de16e8ba273c777b38add516804bbfafabdb2abc2a61f439222621f2d78c"} {:id "defn-/as-package", :kind "defn-", :line 43, :end-line 50, :hash "3a76bf4ce5982c16d80ae42b6a50cf0e5bbb5336c9ebc3ef886c40ad0adbddcb"} {:id "defn-/as-edge", :kind "defn-", :line 52, :end-line 58, :hash "183c61e10eff49580d94018d4b5968f8e59d25cbd4e9c2ab25492c4183c2acdd"} {:id "defn/normalize", :kind "defn", :line 60, :end-line 75, :hash "3afa5362a18d3b662fb65c057df70b7c02ad9e37e51953bf17b008eeac9bd9bb"} {:id "defn/read-diagram", :kind "defn", :line 77, :end-line 78, :hash "15387a388503f3d185cfd773d81767aac5392d10483b49a1fe77706eacda0165"} {:id "defn/load-document", :kind "defn", :line 80, :end-line 86, :hash "7de3e207370543fb655e630f483c70671bdc9949d7c65fce69cb2560166497b8"} {:id "defn/load-diagram", :kind "defn", :line 88, :end-line 89, :hash "bc812f46f4355c1470da7977dee605cfb17bad64c6618fdae4ab5843df6df303"} {:id "defn/class-index", :kind "defn", :line 91, :end-line 95, :hash "1945325f9f9426fc72d61822e48047df707a94e96ab7fe3ee355e8a6aa86f4f0"} {:id "defn/package-of", :kind "defn", :line 97, :end-line 98, :hash "50512c6e6e62f62077a66ae02db08dc0c83822d4a644c10ccfdf27052c0b4aa7"}]}
;; clj-mutate-manifest-end
