(ns uml-viewer.theme-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.theme :as theme]))

(describe "CRAP colors"
  (it "uses a default fill when mu is missing"
    (should= [36 52 48] (theme/fill-for nil))
    (should= [36 52 48] (theme/fill-for {})))

  (it "bands fill by mu, including the 1.5 and 2.5 edges"
    (should= [30 74 56] (theme/fill-for {:mu 1.5}))
    (should= [61 58 24] (theme/fill-for {:mu 1.6}))
    (should= [61 58 24] (theme/fill-for {:mu 2.5}))
    (should= [74 40 24] (theme/fill-for {:mu 2.6})))

  (it "bands stroke the same way"
    (should= [90 110 100] (theme/stroke-for nil))
    (should= [95 181 138] (theme/stroke-for {:mu 0.5}))
    (should= [212 192 90] (theme/stroke-for {:mu 2.0}))
    (should= [224 122 74] (theme/stroke-for {:mu 9.0}))))
