(ns uml-viewer.kotlin-language.check
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [uml-viewer.graph :as graph]
            [uml-viewer.kotlin-language.graph-kotlin]
            [uml-viewer.kotlin-language.source-kotlin]
            [uml-viewer.source :as source]))

(defn- require! [truth message data]
  (when-not truth
    (throw (ex-info message data))))

(defn- edge? [edges from to kind label]
  (some #(and (= from (:from %)) (= to (:to %)) (= kind (:kind %))
              (or (nil? label) (= label (:label %)))) edges))

(defn- collision [root]
  (try
    (graph/scan (graph/lookup :kotlin) root
                {:prefix "com.acme" :modules [{:id :app :src "app/src/main/kotlin"}]})
    nil
    (catch clojure.lang.ExceptionInfo error error)))

(defn -main [& _]
  (let [root (.getCanonicalPath (io/file "integration/fixtures/kotlin-project"))
        result (graph/scan (graph/lookup :kotlin) root
                           {:prefix "com.acme"
                            :modules [{:id :app :src "app/src/main/kotlin"}
                                      {:id :domain :src "domain/src/main/kotlin"}
                                      {:id :data :src "data/src/main/kotlin"}]})
        classes (into {} (map (juxt :id identity) (:classes result)))
        edges (:edges result)
        outer (classes :domain.Outer)
        overloads (filter #(str/starts-with? (:name %) "run(") (:ops outer))
        op (first overloads)
        opened (source/member-source (merge outer op))
        collision-error (collision (.getCanonicalPath
                                     (io/file "integration/fixtures/kotlin-collision")))]
    (doseq [id [:app.Consumer :app.LiteralConsumer :app.QualifiedConsumer
                :domain.ExternalUser :domain.Plugin :domain.PluginCatalog :domain.Outer
                :domain.Outer.Nested :domain.ContractsKt :domain.SamePackage
                :domain.SameUser :domain.Result :domain.BuiltinNameUser
                :domain.Item :domain.T :domain.TypeParameterUser
                :domain.LocalOwner :domain.LocalOwner.Plugin :domain.LocalOwner.User
                :domain.GenericOuter :domain.GenericOuter.Inner
                :data.impl.PluginImpl :data.di.PluginModule]]
      (require! (classes id) "missing Kotlin class" {:id id :classes (keys classes)}))
    (require! (= :interface (:stereotype (classes :domain.Plugin)))
              "interface stereotype was lost" {})
    (require! (= :object (:stereotype (classes :domain.PluginCatalog)))
              "object stereotype was lost" {})
    (require! (not-any? #(#{"TestOnly" "BuiltOnly"} (:name %)) (vals classes))
              "scanner escaped explicit main source roots" {})
    (require! (some #(and (= :data.impl.PluginImpl (:from %))
                          (= :domain.Plugin (:to %))
                          (= :implements (:kind %))
                          (nil? (:label %))) edges)
              "structural implements was replaced by Hilt binding" {:edges edges})
    (require! (some #(and (= :app.Consumer (:from %))
                          (= :data.impl.PluginImpl (:to %))
                          (= :association (:kind %))
                          (= "Hilt set" (:label %))
                          (true? (:derived %))) edges)
              "missing Hilt set contribution edge" {:edges edges})
    (require! (not (edge? edges :app.QualifiedConsumer :data.impl.PluginImpl :association "Hilt set"))
              "qualifier mismatch was guessed" {:edges edges})
    (require! (not (edge? edges :app.LiteralConsumer :data.impl.PluginImpl :association "Hilt set"))
              "qualifier string literal whitespace was normalized" {:edges edges})
    (require! (= 2 (count (filter #(= :hilt-qualifier-mismatch (:kind %))
                                  (:diagnostics result))))
              "qualifier mismatch was not diagnosed" {:diagnostics (:diagnostics result)})
    (require! (edge? edges :app.Consumer :domain.Plugin :dependency nil)
              "star-imported generic type was not resolved" {:edges edges})
    (require! (edge? edges :app.Consumer :domain.Outer.Nested :dependency nil)
              "aliased nested type was not resolved" {:edges edges})
    (require! (edge? edges :domain.SameUser :domain.SamePackage :dependency nil)
              "same-package type was not resolved" {:edges edges})
    (require! (edge? edges :domain.ExternalUser :external.Plugin :dependency nil)
              "explicit foreign import did not win" {:edges edges})
    (require! (not (edge? edges :domain.ExternalUser :domain.Plugin :dependency nil))
              "explicit foreign import fell back to same-package type" {:edges edges})
    (require! (edge? edges :domain.LocalOwner.User :domain.LocalOwner.Plugin :dependency nil)
              "local nested type did not shadow explicit import" {:edges edges})
    (require! (not (edge? edges :domain.LocalOwner.User :external.Plugin :dependency nil))
              "explicit import shadowed local nested type" {:edges edges})
    (require! (edge? edges :domain.BuiltinNameUser :domain.Result :dependency nil)
              "project type with builtin short name was ignored" {:edges edges})
    (require! (not (edge? edges :domain.TypeParameterUser :domain.Item :dependency nil))
              "class type parameter resolved to project class" {:edges edges})
    (require! (not (edge? edges :domain.TypeParameterUser :domain.T :dependency nil))
              "function type parameter resolved to project class" {:edges edges})
    (require! (not (edge? edges :domain.GenericOuter.Inner :domain.Item :dependency nil))
              "outer class type parameter resolved to project class" {:edges edges})
    (require! (and collision-error
                   (= :app.Duplicate (get-in (ex-data collision-error) [:collisions 0 :id]))
                   (= 2 (count (get-in (ex-data collision-error) [:collisions 0 :classes]))))
              "class id collision did not fail fast" {:error collision-error})
    (require! (= 2 (count overloads)) "overloads were not retained" {:ops (:ops outer)})
    (require! (= 2 (count (set (map :name overloads))))
              "overload signatures are not unique" {:ops overloads})
    (require! (and opened (= (:line op) (:line opened))
                   (= (.getCanonicalPath (io/file (:file outer))) (:file opened)))
              "metadata navigation failed" {:op op :opened opened})
    (println "Kotlin PSI integration check passed:" (count classes) "classes," (count edges) "edges")))
