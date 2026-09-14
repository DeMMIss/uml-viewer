(ns uml-viewer.overlay-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [uml-viewer.ir :as ir]
            [uml-viewer.overlay :as overlay]))

(describe "overlay"
  (it "paints class and op metrics from crap and mutate snapshots"
    (let [root (.getCanonicalPath (io/file "target" "overlay-demo"))
          crap-dir (io/file root ".metrics")
          mut-dir (io/file root ".metrics" "mutate" "uml_viewer")]
      (.mkdirs mut-dir)
      (spit (io/file crap-dir "crap.edn")
            (pr-str {:entries [{:name "go" :namespace "uml-viewer.demo"
                                :complexity 3 :coverage 50.0 :crap 6.4}
                               {:name "hide" :namespace "uml-viewer.demo"
                                :complexity 2 :coverage 100.0 :crap 2.0}]}))
      (spit (io/file mut-dir "demo.edn")
            (pr-str {:source "src/uml_viewer/demo.clj"
                     :forms [{:id "defn/go" :hash "a" :killed 4 :survived 1}
                             {:id "defn-/hide" :hash "b" :killed 2 :survived 0}]}))
      (try
        (let [metrics (overlay/load-metrics root)
              d (ir/normalize {:packages
                               [{:id :p :label "P"
                                 :classes [{:id :demo :name "Demo"
                                            :ops [{:name "go"}]}]}]
                               :edges []})
              painted (overlay/apply-metrics d metrics)
              c (get-in painted [:packages 0 :classes 0])
              go (first (filter #(= "go" (:name %)) (:ops c)))
              hide (first (filter #(= "hide" (:name %)) (:ops c)))]
          (should= 5 (:cc c))
          (should= 6 (:killed c))
          (should= 1 (:survived c))
          (should= 3 (:cc go))
          (should= 4 (:killed go))
          (should= 1 (:survived go))
          (should (:private hide))
          (should= 2 (:killed hide)))
        (finally
          (doseq [f (reverse (file-seq (io/file root)))]
            (io/delete-file f true))))))

  (it "leaves a document alone when there is no snapshot"
    (let [d (ir/normalize {:packages [{:id :p :label "P"
                                       :classes [{:id :a :name "A"}]}]
                           :edges []})
          painted (overlay/apply-metrics d {:crap {} :mutate {}})]
      (should= d painted))))
