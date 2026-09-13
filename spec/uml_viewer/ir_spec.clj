(ns uml-viewer.ir-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.ir :as ir]))

(def tiny
  {:title "Tiny"
   :packages
   [{:id :dom
     :label "Domain"
     :crap 1.2
     :classes
     [{:id :a :name "A" :crap {:mu 1.0 :max 2 :sigma 0.5}
       :ops [{:name "go" :returns "void"}]}
      {:name "B"}]}]
   :edges
   [{:from :a :to :b :kind :association}]})

(describe "IR"
  (it "keywordizes ids and fills omitted class ids from the name"
    (let [d (ir/normalize tiny)]
      (should= :a (get-in d [:packages 0 :classes 0 :id]))
      (should= :b (get-in d [:packages 0 :classes 1 :id]))
      (should= 1.2 (get-in d [:packages 0 :crap :mu]))))

  (it "rejects edges to unknown classes"
    (should-throw
      (ir/normalize (assoc tiny :edges [{:from :a :to :nope}]))))

  (it "rejects duplicate class ids"
    (should-throw
      (ir/normalize (assoc-in tiny [:packages 0 :classes 1 :id] :a))))

  (it "loads the sample library diagram"
    (let [d (ir/load-diagram "examples/library.edn")]
      (should= "Lending library" (:title d))
      (should= 3 (count (:packages d)))
      (should (seq (:edges d)))))

  (it "loads the Othello diagram"
    (let [d (ir/load-diagram "examples/othello.edn")]
      (should= "Othello" (:title d))
      (should= 4 (count (:packages d))))))
