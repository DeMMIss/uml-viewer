(ns uml-viewer.detail-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.detail :as detail]
            [uml-viewer.events :as events]
            [uml-viewer.ir :as ir]
            [uml-viewer.metrics :as m]))

(defn- call [sym & args]
  (apply (ns-resolve 'uml-viewer.detail sym) args))

(defn scene []
  (events/compile-diagram
    (ir/normalize
      {:title "Tiny"
       :packages
       [{:id :p :label "Domain"
         :classes [{:id :a :name "A"
                    :coverage 0.9
                    :crap 1.2
                    :ops [{:name "go" :args ["x"] :returns "void"
                           :coverage 0.75
                           :cc 2
                           :crap 1.8
                           :killed 3
                           :survived 1}
                          {:name "hide" :private true :crap 4.0}]}
                   {:id :b :name "B"}]}]
       :edges [{:from :a :to :b :kind :dependency}]})))

(describe "rel-phrase"
  (it "names each edge kind in both directions"
    (doseq [[kind out in]
            [[:inheritance "extends" "extended by"]
             [:implements "implements" "implemented by"]
             [:association "associates with" "associated from"]
             [:dependency "depends on" "used by"]
             [:aggregation "aggregates" "aggregated by"]
             [:composition "composes" "composed in"]]]
      (should= out (call 'rel-phrase kind true))
      (should= in (call 'rel-phrase kind false))))

  (it "falls back to to/from for an unknown kind"
    (should= "to" (call 'rel-phrase :other true))
    (should= "from" (call 'rel-phrase :other false))))

(describe "detail"
  (it "formats coverage and mutant counts"
    (should= "90%" (m/format-coverage 0.9))
    (should-be-nil (m/format-coverage nil))
    (should= "3 killed / 1 survived" (m/format-mutants 3 1))
    (should-be-nil (m/format-mutants nil nil)))

  (it "builds a class card with coverage, ops, and relationships"
    (let [s (scene)
          model (detail/model s :a)
          rows (detail/rows model)
          kinds (map :kind rows)
          go (first (filter #(= "+ go(x) : void" (:text %)) rows))
          hide (first (filter #(= "- hide" (:text %)) rows))
          cls (first (filter #(and (= :stats (:kind %)) (= "A" (:text %))) rows))
          rel (first (filter #(= :rel (:kind %)) rows))]
      (should= "A" (get-in model [:class :name]))
      (should= 0.9 (get-in model [:class :coverage]))
      (should (some #{:name :stats :col-header :rel} kinds))
      (should= "90%" (:cov-s cls))
      (should= "1.2μ" (:crap-s cls))
      (should-be-nil (:cc-s cls))
      (should= "75%" (:cov-s go))
      (should= "2" (:cc-s go))
      (should= "1.8" (:crap-s go))
      (should hide)
      (should (:private hide))
      (should= "4.0" (:crap-s hide))
      (should= "3" (:killed-s go))
      (should= "1" (:survived-s go))
      (should= "3" (:killed-s cls))
      (should= "1" (:survived-s cls))
      (should= :b (:id rel))
      (should= :b (detail/rel-at rows (+ (:y rel) 1)))
      (should= "go" (:op-name go))
      (should= "go" (detail/member-at rows (+ (:y go) 1)))
      (should-be-nil (detail/member-at rows (:y cls)))
      (should= "hide" (detail/member-at rows (+ (:y hide) 1)))))

  (it "shows class CRAP as μ, omits CC, and keeps μ/max/σ on the header line"
    (let [s (events/compile-diagram
              (ir/normalize
                {:packages
                 [{:id :p :label "P"
                   :classes [{:id :d :name "Detail"
                              :crap {:mu 14.2 :max 134.6 :sigma 34.9}
                              :cc 65}]}]
                 :edges []}))
          rows (detail/rows (detail/model s :d))
          cls (first (filter #(and (= :stats (:kind %)) (= "Detail" (:text %))) rows))
          header (first (filter #(= :crap (:kind %)) rows))]
      (should= "14.2μ" (:crap-s cls))
      (should-be-nil (:cc-s cls))
      (should (re-find #"max 134\.6" (:text header)))))

  (it "returns nil for an unknown class"
    (should-be-nil (detail/model (scene) :nope)))

  (it "lays out Crap, CC, Cov, killed, and survived columns"
    (let [cols (detail/column-layout)]
      (should= ["Crap" "CC" "Cov" "killed" "survived"] (map :label cols))
      (should (apply < (map :left cols)))))

  (it "does not treat a field row as a relationship hit"
    (let [rows (detail/rows (detail/model (scene) :a))
          name-row (first (filter #(= :name (:kind %)) rows))]
      (should-be-nil (detail/rel-at rows (:y name-row))))))
