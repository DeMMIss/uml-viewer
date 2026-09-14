(ns uml-viewer.compose
  (:require [uml-viewer.geom :as geom]
            [uml-viewer.layout :as layout]
            [uml-viewer.metrics :as m]
            [uml-viewer.route :as route]))

(defn- qid [idx id]
  (keyword (str "d" idx "-" (name id))))

(defn- move-rect [r dx dy]
  (geom/rect (+ (:x r) dx) (+ (:y r) dy) (:w r) (:h r)))

(defn- qualify [idx scene]
  (let [q #(qid idx %)]
    (-> scene
        (update :packages
                (fn [ps]
                  (mapv #(assoc % :id (q (:id %))) ps)))
        (update :classes
                (fn [cs]
                  (mapv #(assoc %
                           :id (q (:id %))
                           :package (q (:package %)))
                        cs)))
        (update :edges
                (fn [es]
                  (mapv #(assoc % :from (q (:from %)) :to (q (:to %))) es))))))

(defn- translate [scene dx dy]
  (-> scene
      (update :packages (fn [ps] (mapv #(update % :rect move-rect dx dy) ps)))
      (update :classes (fn [cs] (mapv #(update % :rect move-rect dx dy) cs)))
      (update :edges
              (fn [es]
                (mapv (fn [e]
                        (update e :points
                                (fn [pts]
                                  (mapv (fn [[x y]] [(+ x dx) (+ y dy)]) pts))))
                      es)))))

(defn compile-document
  "Layout and route each diagram, then stack them top to bottom."
  [doc]
  (let [gap 64
        title-h 40
        raw (map-indexed
              (fn [i d]
                (-> (route/route (layout/layout d))
                    (assoc :title (:title d) :crap (:crap d))
                    (#(qualify i %))))
              (:diagrams doc))
        [total-h sections]
        (reduce
          (fn [[y acc] s]
            (let [s' (translate s m/margin (+ y title-h))]
              [(+ y title-h (get-in s [:size :h]) gap)
               (conj acc (assoc s' :title-y y))]))
          [24 []]
          raw)]
    {:title (:title doc)
     :sections sections
     :classes (vec (mapcat :classes sections))
     :packages (vec (mapcat :packages sections))
     :edges (vec (mapcat :edges sections))
     :diagram {:title (:title doc)}
     :size {:w (+ m/margin (apply max 400 (map #(get-in % [:size :w]) sections)))
            :h total-h}}))

;; clj-mutate-manifest-begin
;; {:version 2, :hash-algorithm :sha256-source-v1, :verified? true, :tested-at "2026-09-14T09:12:22.403699-05:00", :module-hash "9b14aa6d9afe021c1def2c4844b09b17b0dd850f8566c424fa0841299dcb5eb1", :provenance {:mutation-rules-version "3", :test-command "clj -M:spec --tag ~no-mutate", :test-roots ["spec"], :test-profile-fingerprint "feb50ad925ce7a201699752fff6e8f85bd5e985fe2b359782db5f1e6456edf17"}, :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "e4c4b83a2a04892d01cd0f49f48cd6c1b7c7e4ade739d99d1011accfe7c0f81d"} {:id "defn-/qid", :kind "defn-", :line 7, :end-line 8, :hash "3549e59b6a871e8289e62f989c997b849a1c7d410151be11dc576736c1d226a4"} {:id "defn-/move-rect", :kind "defn-", :line 10, :end-line 11, :hash "8adecd628cb5853219d880e91f353dcbbcdd4125310692c8bf34dc8d6ec5a8fe"} {:id "defn-/qualify", :kind "defn-", :line 13, :end-line 27, :hash "d78109f78fb86bc440aefdf04083c44590c09fd934b48d6526919916d0431a99"} {:id "defn-/translate", :kind "defn-", :line 29, :end-line 39, :hash "d591b965b66d952bb38b769b9dab59114904ac2886fc1a4cb2e7b11424871a14"} {:id "defn/compile-document", :kind "defn", :line 41, :end-line 67, :hash "c11eda4e5d198e0428eabd907edea31531465c77082faa08f547e92ba4a19a28"}]}
;; clj-mutate-manifest-end
