(ns uml-viewer.theme)

(def bg [22 28 32])
(def panel [26 36 40])
(def ink [236 236 228])
(def muted [157 184 168])
(def gold [232 196 72])
(def line [42 61 54])

(defn- risk [crap]
  (when-let [mu (:mu crap)]
    (+ (double mu) (double (or (:sigma crap) 0)))))

(defn- mix [a b t]
  (int (+ a (* t (- b a)) 0.5)))

(defn- mix-rgb [c1 c2 t]
  [(mix (nth c1 0) (nth c2 0) t)
   (mix (nth c1 1) (nth c2 1) t)
   (mix (nth c1 2) (nth c2 2) t)])

(defn- ramp [crap none green mid red]
  (let [r (risk crap)]
    (cond
      (nil? r) none
      (<= r 12.0) (mix-rgb green mid (/ r 12.0))
      :else (mix-rgb mid red (min 1.0 (/ (- r 12.0) 12.0))))))

(defn fill-for [crap]
  (ramp crap [36 52 48] [30 74 56] [61 58 24] [74 40 24]))

(defn stroke-for [crap]
  (ramp crap [90 110 100] [95 181 138] [212 192 90] [224 122 74]))

(defn coverage-ink [p]
  (cond
    (nil? p) muted
    (>= p 0.8) [95 181 138]
    (>= p 0.5) gold
    :else [224 122 74]))
