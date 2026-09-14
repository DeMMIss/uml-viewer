(ns uml-viewer.overlay
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- read-edn [f]
  (when (and f (.exists (io/file f)))
    (edn/read-string (slurp f))))

(defn load-crap
  ([root] (load-crap root ".metrics/crap.edn"))
  ([root rel]
   (let [data (read-edn (io/file root rel))]
     (group-by :namespace (or (:entries data) [])))))

(defn- mutate-files [root]
  (let [dir (io/file root ".metrics" "mutate")]
    (when (.isDirectory dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (filter #(str/ends-with? (.getName %) ".edn"))))))

(defn- ns-from-source [src]
  (when src
    (-> src
        (str/replace #"\\" "/")
        (str/replace #"^src/" "")
        (str/replace #"\.[^.]+$" "")
        (str/replace #"/" ".")
        (str/replace #"_" "-"))))

(defn load-mutate
  [root]
  (reduce (fn [acc f]
            (if-let [data (read-edn f)]
              (let [ns-name (or (:namespace data) (ns-from-source (:source data)))]
                (cond-> acc ns-name (assoc ns-name data)))
              acc))
          {}
          (or (mutate-files root) [])))

(defn load-metrics
  ([] (load-metrics (System/getProperty "user.dir")))
  ([root]
   {:crap (or (load-crap root) {})
    :mutate (or (load-mutate root) {})}))

(defn- form-name [id]
  (when id
    (cond
      (str/starts-with? id "defn-/") {:name (subs id 6) :private true}
      (str/starts-with? id "defn/") {:name (subs id 5) :private false}
      :else nil)))

(defn- mutate-by-fn [snapshot]
  (into {}
        (keep (fn [form]
                (when-let [n (form-name (:id form))]
                  [(:name n) (assoc n
                               :killed (:killed form)
                               :survived (:survived form))]))
              (:forms snapshot))))

(defn- ns-for-class [c]
  (or (:ns c)
      (let [id (name (:id c))
            id (if-let [m (re-find #"^d\d+-(.+)$" id)] (second m) id)]
        (str "uml-viewer." id))))

(defn- pct->ratio [cov]
  (when (number? cov)
    (/ (double cov) 100.0)))

(defn- class-crap [scores]
  (when (seq scores)
    (let [xs (mapv double scores)
          n (count xs)
          mu (/ (reduce + xs) n)
          mx (apply max xs)
          var (/ (reduce + (map #(let [d (- % mu)] (* d d)) xs)) n)
          sd (Math/sqrt var)]
      {:mu (* 0.1 (Math/round (* 10.0 mu)))
       :max (* 0.1 (Math/round (* 10.0 mx)))
       :sigma (* 0.1 (Math/round (* 10.0 sd)))})))

(defn- overlay-op [op crap-fn mut-fn]
  (cond-> (assoc op :text (or (:text op) (:name op)))
    crap-fn (assoc :cc (:complexity crap-fn)
                   :crap (:crap crap-fn)
                   :coverage (pct->ratio (:coverage crap-fn)))
    mut-fn (assoc :killed (or (:killed mut-fn) 0)
                  :survived (or (:survived mut-fn) 0))
    (or (:private op) (:private mut-fn)) (assoc :private true)))

(defn- ops-for-class [c crap-fns mut-fns]
  (let [by-name (into {} (map (juxt :name identity) crap-fns))
        names (distinct (concat (map :name (:ops c))
                                (map :name crap-fns)
                                (keys mut-fns)))]
    (mapv (fn [nm]
            (let [existing (first (filter #(= nm (:name %)) (:ops c)))
                  base (or existing {:name nm :text nm})]
              (overlay-op base (get by-name nm) (get mut-fns nm))))
          names)))

(defn overlay-class [c crap-by-ns mutate-by-ns]
  (let [ns-name (ns-for-class c)
        crap-fns (or (get crap-by-ns ns-name) [])
        mut-fns (mutate-by-fn (get mutate-by-ns ns-name))
        scores (keep :crap crap-fns)
        coverages (keep :coverage crap-fns)
        ops (ops-for-class c crap-fns mut-fns)
        killed (apply + 0 (keep :killed (vals mut-fns)))
        survived (apply + 0 (keep :survived (vals mut-fns)))]
    (cond-> c
      (seq scores) (assoc :crap (class-crap scores)
                          :cc (apply + (map :complexity crap-fns)))
      (seq coverages) (assoc :coverage (pct->ratio
                                         (/ (reduce + coverages) (count coverages))))
      (seq mut-fns) (assoc :killed killed :survived survived)
      (seq ops) (assoc :ops ops))))

(defn- paint-packages [packages metrics]
  (mapv (fn [p]
          (update p :classes
                  (fn [cs]
                    (mapv #(overlay-class % (:crap metrics) (:mutate metrics))
                          cs))))
        packages))

(defn apply-metrics
  [doc metrics]
  (if (and (empty? (:crap metrics)) (empty? (:mutate metrics)))
    doc
    (cond
      (:diagrams doc)
      (update doc :diagrams
              (fn [ds]
                (mapv #(update % :packages paint-packages metrics) ds)))
      (:packages doc)
      (update doc :packages paint-packages metrics)
      :else doc)))
