(ns uml-viewer.theme-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.theme :as theme]))

(describe "CRAP colors"
  (it "uses a default fill when mu is missing"
    (should= [36 52 48] (theme/fill-for nil))
    (should= [36 52 48] (theme/fill-for {})))

  (it "is green at μ+σ = 0, gold at 12, rust at 24 and above"
    (should= [30 74 56] (theme/fill-for {:mu 0 :sigma 0}))
    (should= [61 58 24] (theme/fill-for {:mu 12}))
    (should= [61 58 24] (theme/fill-for {:mu 6 :sigma 6}))
    (should= [74 40 24] (theme/fill-for {:mu 24}))
    (should= [74 40 24] (theme/fill-for {:mu 100})))

  (it "strokes the same μ+σ ramp"
    (should= [90 110 100] (theme/stroke-for nil))
    (should= [95 181 138] (theme/stroke-for {:mu 0}))
    (should= [212 192 90] (theme/stroke-for {:mu 12 :sigma 0}))
    (should= [224 122 74] (theme/stroke-for {:mu 24}))))

(describe "coverage colors"
  (it "bands ink by coverage, high to low"
    (should= theme/muted (theme/coverage-ink nil))
    (should= [95 181 138] (theme/coverage-ink 0.8))
    (should= theme/gold (theme/coverage-ink 0.5))
    (should= [224 122 74] (theme/coverage-ink 0.49))))
