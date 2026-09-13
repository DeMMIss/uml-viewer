(ns uml-viewer.theme)

(def bg [22 28 32])
(def panel [26 36 40])
(def ink [236 236 228])
(def muted [157 184 168])
(def gold [232 196 72])
(def line [42 61 54])

(defn fill-for [crap]
  (let [mu (:mu crap)]
    (cond
      (nil? mu) [36 52 48]
      (<= mu 1.5) [30 74 56]
      (<= mu 2.5) [61 58 24]
      :else [74 40 24])))

(defn stroke-for [crap]
  (let [mu (:mu crap)]
    (cond
      (nil? mu) [90 110 100]
      (<= mu 1.5) [95 181 138]
      (<= mu 2.5) [212 192 90]
      :else [224 122 74])))
