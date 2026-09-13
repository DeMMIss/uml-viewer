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

;; clj-mutate-manifest-begin
;; {:version 2, :hash-algorithm :sha256-source-v1, :verified? true, :tested-at "2026-09-13T12:11:23.062498-05:00", :module-hash "962259f00b7d9d4590545b7bd3c03547a748288c74bc666704f36cbd2d1367f1", :provenance {:mutation-rules-version "3", :test-command "clj -M:spec --tag ~no-mutate", :test-roots ["spec"], :test-profile-fingerprint "2ba661442008c8ee7c88d59101a010345db8599b122338e9eafa9bd7b48c5d00"}, :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "d0a365b5837ba1b19a238be9de2341a65b6a1d0bf55e17e6f46c67a6c61c42e5"} {:id "def/bg", :kind "def", :line 3, :end-line 3, :hash "3abda511bcd3ccfe002d4922ebc4cbc35a5c0c955b6b85bae93366bc7d1fbd29"} {:id "def/panel", :kind "def", :line 4, :end-line 4, :hash "f450662d3535e6e269797cd98d99789fa82d531dbd4a98c522e3eb8e00ab97c8"} {:id "def/ink", :kind "def", :line 5, :end-line 5, :hash "cc239e8c27f0546c448067cf4337786b3e43fae3d945fc014b768d0ce1d99ec0"} {:id "def/muted", :kind "def", :line 6, :end-line 6, :hash "e1d3ada03f416c07ccd32c54d2d47da39a7fd4e16f257748fd0f886907a6518b"} {:id "def/gold", :kind "def", :line 7, :end-line 7, :hash "c6bff45a954847f2a6e634f37d3d82d73c6f92e86b61161c29223a4e85ac4137"} {:id "def/line", :kind "def", :line 8, :end-line 8, :hash "2224a850204c03224bd647fd58d0a6eba6b86c84962fdf8ee3aded84ad779f0a"} {:id "defn/fill-for", :kind "defn", :line 10, :end-line 16, :hash "ea8387b31bf2325d59dc9ebccce4e6ff8ba5a0fb9e2415afb31da04f59871653"} {:id "defn/stroke-for", :kind "defn", :line 18, :end-line 24, :hash "e3e81d370490ff5af29ab8b37790c5492c488f65c72baf71d70e2a5aebb012d1"}]}
;; clj-mutate-manifest-end
