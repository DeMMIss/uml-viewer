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
(def head-size 12)
(def lane-gap 10)

(defn text-w [s]
  (* char-w (count (or s ""))))

(defn format-crap [crap]
  (when (:mu crap)
    (format "μ %.1f   max %.1f   σ %.1f"
            (double (:mu crap))
            (double (or (:max crap) (:mu crap)))
            (double (or (:sigma crap) 0)))))

;; clj-mutate-manifest-begin
;; {:version 2, :hash-algorithm :sha256-source-v1, :verified? true, :tested-at "2026-09-13T12:11:55.239546-05:00", :module-hash "814fe8fc5a4ec3fdb04745ebf0e4181f9f4bf8cc3a9f7c58bb16c78ef7dc60df", :provenance {:mutation-rules-version "3", :test-command "clj -M:spec --tag ~no-mutate", :test-roots ["spec"], :test-profile-fingerprint "01f72bad2b4b9bac72353eccfb667d7ea8820b5b0699dd36256c4d99b23f89ae"}, :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "78063dc57556e2628e0a0b137235e53575071b074134663c6737ea5df88de3f6"} {:id "def/char-w", :kind "def", :line 3, :end-line 3, :hash "ae4fd14b9c84030abeaca95605c0c616e64e9c0595a4d573f5bd3845d47c5250"} {:id "def/line-h", :kind "def", :line 4, :end-line 4, :hash "0d094b8dce4d97ec7f44e957ff181c8b293b1238a467555c12a224ce887f3242"} {:id "def/pad", :kind "def", :line 5, :end-line 5, :hash "1aaee18ac647e10bbda70139bcfea77c2f3456f7f21b5d3cccfee3a7e04bac73"} {:id "def/banner-h", :kind "def", :line 6, :end-line 6, :hash "fd10eac11b5980b198cbfa69fcbe43bdd2beb5307056c6d35abc9b6b77b03f52"} {:id "def/class-gap", :kind "def", :line 7, :end-line 7, :hash "705b4f72682c328e4ab2668047432cd3ce18d9a4ccc7ae6e769a3dcf13a9a38e"} {:id "def/pack-gap", :kind "def", :line 8, :end-line 8, :hash "90b3a234ab348df05c8572f2f619728e7e19c91cba91d57ac4537bbb64af2618"} {:id "def/rank-gap", :kind "def", :line 9, :end-line 9, :hash "6ba0b619776f375567d01090688dd2f525134b1f9a613daab95bace0ad16059e"} {:id "def/class-rank-gap", :kind "def", :line 10, :end-line 10, :hash "39cd769eb83dbbb01da43b60c4a97843c5b64512e2ffb1c701ef1d08b053d9df"} {:id "def/margin", :kind "def", :line 11, :end-line 11, :hash "5689711b0a54961aeb8fad2e1115ab2edb2278967714061f59c0a1efa860ff84"} {:id "def/head-size", :kind "def", :line 12, :end-line 12, :hash "42a6c5ad99692fac308e7c4f238eb5536dac4b56f5416b45e86d190dc79b93b1"} {:id "def/lane-gap", :kind "def", :line 13, :end-line 13, :hash "0611a2a398a33034d1c1abb3083450c4defd685b8a38706414e0f99c50a00b67"} {:id "defn/text-w", :kind "defn", :line 15, :end-line 16, :hash "78cfa5642e372822ac069277a0671de7f19a31f6d2a0ed0cb65acabee99ef640"} {:id "defn/format-crap", :kind "defn", :line 18, :end-line 23, :hash "a78f5dc8740050bb4924608f20c98e14945b8d3992080d3e18d5234e083aab01"}]}
;; clj-mutate-manifest-end
