(ns uml-viewer.metrics)

(def char-w 8)
(def line-h 18)
(def pad 10)
(def banner-h 32)
(def class-gap 16)
(def pack-gap 28)
(def rank-gap 72)
(def margin 40)

(defn text-w [s]
  (* char-w (count (or s ""))))

(defn format-crap [crap]
  (when (:mu crap)
    (format "μ %.1f   max %.1f   σ %.1f"
            (double (:mu crap))
            (double (or (:max crap) (:mu crap)))
            (double (or (:sigma crap) 0)))))
