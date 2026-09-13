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

  (it "builds member text from name, args, type, and returns"
    (let [d (ir/normalize
              {:packages
               [{:id :p :label "P"
                 :classes [{:id :a :name "A"
                            :fields [{:name "n" :type "int"}]
                            :ops [{:name "go" :args ["x"] :returns "void"}]}]}]
               :edges []})
          c (get-in d [:packages 0 :classes 0])]
      (should= "n : int" (get-in c [:fields 0 :text]))
      (should= "go(x) : void" (get-in c [:ops 0 :text]))))

  (it "accepts symbol ids and :sd as sigma"
    (let [d (ir/normalize
              {:packages
               [{:id 'dom :label "D"
                 :crap {:mu 1 :sd 0.2}
                 :classes [{:id 'a :name "A"}]}]
               :edges []})]
      (should= :dom (get-in d [:packages 0 :id]))
      (should= 0.2 (get-in d [:packages 0 :crap :sigma]))))

  (it "defaults title and direction"
    (let [d (ir/normalize
              {:packages [{:id :p :label "P" :classes [{:id :a :name "A"}]}]
               :edges []})]
      (should= "UML" (:title d))
      (should= :tb (:direction d))))

  (it "rejects malformed ids, crap, members, classes, packages, and edges"
    (should-throw (ir/normalize {:packages [{:id 1 :label "P" :classes [{:id :a :name "A"}]}]}))
    (should-throw (ir/normalize {:packages [{:id :p :label "P" :crap "bad"
                                            :classes [{:id :a :name "A"}]}]}))
    (should-throw (ir/normalize {:packages [{:id :p :label "P"
                                            :classes [{:id :a :name "A" :fields [1]}]}]}))
    (should-throw (ir/normalize {:packages [{:id :p :label "P" :classes [{}]}]}))
    (should-throw (ir/normalize {:packages [{}]}))
    (should-throw (ir/normalize {:packages [{:id :p :label "P" :classes [{:id :a :name "A"}]}]
                                :edges [{}]})))

  (it "reads a diagram from an EDN string"
    (should= "Tiny" (:title (ir/read-diagram "{:title \"Tiny\" :packages [{:id :p :label \"P\" :classes [{:id :a :name \"A\"}]}] :edges []}"))))

  (it "loads the viewer document as layer diagrams"
    (let [doc (ir/load-document "examples/uml-viewer.edn")]
      (should= "UML viewer" (:title doc))
      (should= ["Layers" "Domain" "Engine" "Application" "Adapters"]
               (map :title (:diagrams doc))))))
