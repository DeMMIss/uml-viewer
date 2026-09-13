(ns uml-viewer.hit
  (:require [uml-viewer.geom :as geom]))

(defn at
  "Topmost class or package under world point [x y]."
  [scene x y]
  (or (some (fn [c]
              (when (geom/inside? (:rect c) x y)
                {:kind :class :id (:id c)}))
            (reverse (remove :dummy? (:classes scene))))
      (some (fn [p]
              (when (geom/inside? (:rect p) x y)
                {:kind :package :id (:id p)}))
            (reverse (:packages scene)))))

(defn class-by-id [scene id]
  (first (filter #(= id (:id %)) (:classes scene))))

(defn package-by-id [scene id]
  (first (filter #(= id (:id %)) (:packages scene))))

(defn connected-edges [scene class-id]
  (filter (fn [e]
            (or (= class-id (:from e))
                (= class-id (:to e))))
          (:edges scene)))

;; clj-mutate-manifest-begin
;; {:version 2, :hash-algorithm :sha256-source-v1, :verified? true, :tested-at "2026-09-13T12:14:22.232257-05:00", :module-hash "396130cdc8ee825cce82702bc67f07e53820d9fb5b5f19c5aefdbe1d47338bea", :provenance {:mutation-rules-version "3", :test-command "clj -M:spec --tag ~no-mutate", :test-roots ["spec"], :test-profile-fingerprint "01f72bad2b4b9bac72353eccfb667d7ea8820b5b0699dd36256c4d99b23f89ae"}, :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "eba1bc74a1a243fa02f0adb671b4b0867071a9c7bda54ef2a191347e7d0e8ea3"} {:id "defn/at", :kind "defn", :line 4, :end-line 14, :hash "3792b63a83d7b81c5fa0144aa9deaeb8cc5b4dbcfc172b4bcf1d63067d926d3a"} {:id "defn/class-by-id", :kind "defn", :line 16, :end-line 17, :hash "b91c36e303ddd8e85328111f87a21b444eda883f6d96a6ef21ea34bfed7bb77d"} {:id "defn/package-by-id", :kind "defn", :line 19, :end-line 20, :hash "5b46d27610b5b0c10b51866a6ee99a571596236e9752d8e941073be9c9de5db5"} {:id "defn/connected-edges", :kind "defn", :line 22, :end-line 26, :hash "7cee051d77c8759d0f6c80d7cab93bad70d96ddcdb81c510def1137910685a5e"}]}
;; clj-mutate-manifest-end
