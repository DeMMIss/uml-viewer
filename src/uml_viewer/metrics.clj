(ns uml-viewer.metrics)

(def char-w 8)
(def line-h 18)
(def pad 12)
(def banner-h 32)
(def class-gap 40)
(def pack-gap 40)
(def rank-gap 80)
(def class-rank-gap 140)
(def margin 40)
(def head-size 16)
(def lane-gap 10)
(def sidebar-w 280)
(def under-gap 5)

(defn text-w [s]
  (* char-w (count (or s ""))))

(defn format-crap [crap]
  (when (:mu crap)
    (format "μ %.1f   max %.1f   σ %.1f"
            (double (:mu crap))
            (double (or (:max crap) (:mu crap)))
            (double (or (:sigma crap) 0)))))

(defn format-coverage [p]
  (when p
    (format "%.0f%%" (* 100.0 p))))

(defn format-mutants [killed survived]
  (when (or killed survived)
    (format "%d killed / %d survived"
            (long (or killed 0))
            (long (or survived 0)))))
