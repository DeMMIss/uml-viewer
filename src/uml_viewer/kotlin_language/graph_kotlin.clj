(ns uml-viewer.kotlin-language.graph-kotlin
  "Syntax-only Kotlin graph backed by Kotlin compiler PSI."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [uml-viewer.graph :as graph])
  (:import [org.jetbrains.kotlin.cli.jvm.compiler EnvironmentConfigFiles KotlinCoreEnvironment]
           [org.jetbrains.kotlin.com.intellij.openapi.util Disposer]
           [org.jetbrains.kotlin.com.intellij.psi PsiErrorElement]
           [org.jetbrains.kotlin.com.intellij.psi.util PsiTreeUtil]
           [org.jetbrains.kotlin.config CompilerConfiguration]
           [org.jetbrains.kotlin.lexer KtTokens]
           [org.jetbrains.kotlin.psi KtAnnotated KtAnnotationEntry KtClass KtClassOrObject KtConstructor
            KtFile KtNamedFunction KtObjectDeclaration KtParameter KtProperty
            KtPsiFactory KtSecondaryConstructor KtTypeAlias KtTypeReference KtUserType]))

(def ^:private builtin-types
  #{"Any" "Boolean" "Byte" "Char" "Double" "Float" "Int" "Long" "Nothing"
    "Number" "Short" "String" "Unit" "Array" "Collection" "Iterable" "Iterator"
    "List" "Map" "MutableCollection" "MutableIterable" "MutableIterator" "MutableList"
    "MutableMap" "MutableSet" "Pair" "Result" "Sequence" "Set" "Triple"})

(def ^:private non-qualifier-annotations
  #{"Assisted" "AssistedInject" "Binds" "HiltViewModel" "Inject" "InstallIn" "IntoMap"
    "IntoSet" "JvmSuppressWildcards" "Module" "Multibinds" "Provides" "Singleton"})

(defn- canonical-file [path]
  (.getCanonicalFile (io/file path)))

(defn- line-at [text offset]
  (inc (count (filter #(= \newline %) (take (max 0 (min offset (count text))) text)))))

(defn- span [text element]
  (let [range (.getTextRange element)]
    {:line (line-at text (.getStartOffset range))
     :end-line (line-at text (max (.getStartOffset range) (dec (.getEndOffset range))))}))

(defn- compact [text]
  (str/replace (str/trim (or text "")) #"\s+" " "))

(defn- normalize-lines [text]
  (str/replace (or text "") #"\r\n?" "\n"))

(defn- source-files [source-root]
  (->> (file-seq source-root)
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".kt"))
       (sort-by #(.getPath %))))

(defn- import-context [^KtFile file]
  (reduce (fn [acc directive]
            (if-let [fq-name (.getImportedFqName directive)]
              (let [fq (str fq-name)]
                (if (.isAllUnder directive)
                  (update acc :stars conj fq)
                  (let [local (or (.getAliasName directive)
                                  (last (str/split fq #"\.")))]
                    (update-in acc [:explicit local] (fnil conj []) fq))))
              acc))
          {:explicit {} :stars []}
          (.getImportDirectives file)))

(defn- annotation-entries [x]
  (if (instance? KtAnnotated x) (.getAnnotationEntries ^KtAnnotated x) []))

(defn- annotation-names [x]
  (into #{} (keep #(some-> % .getShortName str)) (annotation-entries x)))

(defn- imported-name [{:keys [explicit package]} local]
  (let [xs (distinct (get explicit local))]
    (if (= 1 (count xs)) (first xs) (str package "." local))))

(defn- qualifier-key [x context]
  (->> (annotation-entries x)
       (keep (fn [entry]
               (let [short (some-> entry .getShortName str)]
                 (when (and short (not (non-qualifier-annotations short)))
                   (str (imported-name context short)
                        (some-> entry .getValueArgumentList .getText compact))))))
       sort
       vec))

(defn- user-type-name [^KtUserType user-type]
  (let [name (.getReferencedName user-type)]
    (when name
      (if-let [qualifier (.getQualifier user-type)]
        (str (user-type-name qualifier) "." name)
        name))))

(defn- root-user-types [^KtTypeReference type-ref]
  (->> (PsiTreeUtil/findChildrenOfType type-ref KtUserType)
       (remove #(instance? KtUserType (.getParent %)))
       (remove #(PsiTreeUtil/getParentOfType % KtAnnotationEntry true))))

(defn- type-names [^KtTypeReference type-ref]
  (if-not type-ref
    []
    (->> (root-user-types type-ref)
         (keep user-type-name)
         distinct
         vec)))

(defn- primary-type-name [type-ref]
  (first (type-names type-ref)))

(defn- set-element-name [^KtTypeReference type-ref]
  (when type-ref
    (let [types (root-user-types type-ref)
          outer (first types)]
      (when (and outer (= "Set" (.getReferencedName ^KtUserType outer)))
        (some-> outer .getTypeArgumentsAsTypes first primary-type-name)))))

(defn- type-text [type-ref]
  (if type-ref (compact (.getText type-ref)) "?"))

(defn- parameter-type [^KtParameter parameter]
  (type-text (.getTypeReference parameter)))

(defn- operation [text element name params return-type prefix]
  (let [args (str/join ", " (map parameter-type params))
        signature (str name "(" args ")" (when return-type (str ": " return-type)))]
    (merge {:name signature :text (str prefix signature)} (span text element))))

(defn- function-op [text ^KtNamedFunction function]
  (operation text function (.getName function) (.getValueParameters function)
             (when (.getTypeReference function) (type-text (.getTypeReference function))) "fun "))

(defn- constructor-op [text class-name ^KtConstructor constructor]
  (operation text constructor "<init>" (.getValueParameters constructor) nil
             (str class-name " ")))

(defn- property-field [text ^KtProperty property]
  (merge {:name (.getName property)
          :type (type-text (.getTypeReference property))
          :text (str (if (.isVar property) "var " "val ")
                     (.getName property) ": " (type-text (.getTypeReference property)))}
         (span text property)))

(defn- parameter-field [text ^KtParameter parameter]
  (merge {:name (.getName parameter)
          :type (parameter-type parameter)
          :text (str (if (.isMutable parameter) "var " "val ")
                     (.getName parameter) ": " (parameter-type parameter))}
         (span text parameter)))

(defn- type-sites [relation refs]
  (for [ref refs, name (type-names ref), :when (not (builtin-types name))]
    {:name name :relation relation :line (some-> ref .getTextOffset)}))

(defn- callable-type-refs [callable]
  (concat (keep #(.getTypeReference ^KtParameter %) (.getValueParameters callable))
          (when-let [receiver (.getReceiverTypeReference callable)] [receiver])
          (when-let [returns (.getTypeReference callable)] [returns])))

(defn- class-type-sites [^KtClassOrObject declaration]
  (let [members (.getDeclarations declaration)
        functions (filter #(instance? KtNamedFunction %) members)
        properties (filter #(instance? KtProperty %) members)
        constructors (concat (when-let [primary (.getPrimaryConstructor declaration)] [primary])
                             (.getSecondaryConstructors declaration))]
    (vec (concat
           (type-sites :super (keep #(.getTypeReference %) (.getSuperTypeListEntries declaration)))
           (type-sites :dependency
                       (concat (mapcat callable-type-refs functions)
                               (keep #(.getTypeReference ^KtProperty %) properties)
                               (mapcat #(keep (fn [p] (.getTypeReference ^KtParameter p))
                                              (.getValueParameters ^KtConstructor %)) constructors)))))))

(defn- binding-facts [^KtClassOrObject declaration context]
  (for [member (.getDeclarations declaration)
        :when (instance? KtNamedFunction member)
        :let [function ^KtNamedFunction member
              annotations (annotation-names function)]
        :when (annotations "Binds")]
    {:implementation (some-> function .getValueParameters first .getTypeReference primary-type-name)
     :interface (some-> function .getTypeReference primary-type-name)
     :into-set (boolean (annotations "IntoSet"))
     :qualifier (qualifier-key function context)
     :line (.getTextOffset function)}))

(defn- consumer-facts [^KtClassOrObject declaration context]
  (let [constructors (concat (when-let [primary (.getPrimaryConstructor declaration)] [primary])
                             (.getSecondaryConstructors declaration))]
    (for [constructor constructors
          :when ((annotation-names constructor) "Inject")
          parameter (.getValueParameters ^KtConstructor constructor)
          :let [element (set-element-name (.getTypeReference ^KtParameter parameter))]
          :when element]
      {:interface element
       :qualifier (qualifier-key parameter context)
       :line (.getTextOffset parameter)})))

(defn- stereotype [declaration]
  (cond
    (instance? KtObjectDeclaration declaration) :object
    (and (instance? KtClass declaration) (.isInterface ^KtClass declaration)) :interface
    (and (instance? KtClass declaration) (.isEnum ^KtClass declaration)) :enumeration
    (.hasModifier declaration KtTokens/ABSTRACT_KEYWORD) :abstract
    :else :class))

(defn- class-facts [declaration outer {:keys [text package] :as context}]
  (when-let [simple (.getName ^KtClassOrObject declaration)]
    (let [fqn (str (when (seq package) (str package "."))
                   (when (seq outer) (str outer ".")) simple)
          members (.getDeclarations ^KtClassOrObject declaration)
          constructors (concat (when-let [primary (.getPrimaryConstructor ^KtClassOrObject declaration)] [primary])
                               (.getSecondaryConstructors ^KtClassOrObject declaration))
          functions (filter #(instance? KtNamedFunction %) members)
          properties (filter #(instance? KtProperty %) members)
          fields (concat (map #(property-field text %) properties)
                         (when-let [primary (.getPrimaryConstructor ^KtClassOrObject declaration)]
                           (map #(parameter-field text %)
                                (filter #(.hasValOrVar ^KtParameter %)
                                        (.getValueParameters primary)))))
          fact (merge {:fqn fqn
                       :name simple
                       :stereotype (stereotype declaration)
                       :ops (vec (concat (map #(constructor-op text simple %) constructors)
                                         (map #(function-op text %) functions)))
                       :fields (vec fields)
                       :type-sites (class-type-sites declaration)
                       :bindings (vec (binding-facts declaration context))
                       :consumers (vec (consumer-facts declaration context))
                       :context context}
                      (span text declaration))
          nested (filter #(instance? KtClassOrObject %) members)]
      (cons fact (mapcat #(class-facts % (str (when (seq outer) (str outer ".")) simple) context)
                         nested)))))

(defn- facade-fact [^KtFile file context file-base]
  (let [text (:text context)
        declarations (.getDeclarations file)
        functions (filter #(instance? KtNamedFunction %) declarations)
        properties (filter #(instance? KtProperty %) declarations)
        aliases (filter #(instance? KtTypeAlias %) declarations)]
    (when (or (seq functions) (seq properties) (seq aliases))
      (let [name (str file-base "Kt")
            fqn (str (when (seq (:package context)) (str (:package context) ".")) name)]
        {:fqn fqn
         :name name
         :stereotype :object
         :synthetic true
         :ops (mapv #(function-op text %) functions)
         :fields (vec (concat (map #(property-field text %) properties)
                              (map (fn [^KtTypeAlias alias]
                                     (merge {:name (.getName alias)
                                             :type (type-text (.getTypeReference alias))
                                             :text (str "typealias " (.getName alias) " = "
                                                        (type-text (.getTypeReference alias)))}
                                            (span text alias)))
                                   aliases)))
         :type-sites (vec (type-sites :dependency
                                     (concat (mapcat callable-type-refs functions)
                                             (keep #(.getTypeReference ^KtProperty %) properties)
                                             (map #(.getTypeReference ^KtTypeAlias %) aliases))))
         :bindings []
         :consumers []
         :context context
         :line 1
         :end-line (count (str/split text #"\R" -1))}))))

(defn- parse-file [^KtPsiFactory factory module source-root file]
  (let [path (.getPath (canonical-file file))
        ;; PSI factories receive IDE-normalized text; raw CRLF produces error elements.
        text (normalize-lines (slurp file))
        kt-file (.createFile factory (.getName file) text)
        package (str (.getPackageFqName kt-file))
        context (merge (import-context kt-file)
                       {:package package :text text :file path :source-root (.getPath source-root)
                        :module module})
        top-classes (filter #(instance? KtClassOrObject %) (.getDeclarations kt-file))
        file-base (str/replace (.getName file) #"\.kt$" "")
        facts (concat (mapcat #(class-facts % nil context) top-classes)
                      (when-let [facade (facade-fact kt-file context file-base)] [facade]))
        errors (PsiTreeUtil/findChildrenOfType kt-file PsiErrorElement)]
    {:facts (vec facts)
     :diagnostics (mapv (fn [error]
                          {:kind :parse-error :file path
                           :line (line-at text (.getTextOffset error))
                           :message (.getErrorDescription ^PsiErrorElement error)})
                        errors)}))

(defn- strip-prefix [fqn prefix]
  (let [prefix (str/replace (or prefix "") #"\.$" "")]
    (if (and (seq prefix) (str/starts-with? fqn (str prefix ".")))
      (subs fqn (inc (count prefix)))
      fqn)))

(defn- class-id [module fqn prefix]
  (let [module-name (name module)
        tail (strip-prefix fqn prefix)
        tail (if (str/starts-with? tail (str module-name "."))
               (subs tail (inc (count module-name))) tail)]
    (keyword (str module-name "." tail))))

(defn- decorate-fact [fact prefix]
  (let [{:keys [module file source-root]} (:context fact)]
    (-> fact
        (assoc :id (class-id module (:fqn fact) prefix)
               :ns (:fqn fact) :lang :kotlin :file file :source-root source-root)
        (dissoc :fqn))))

(defn- candidates [fact type-name]
  (let [{:keys [package explicit stars]} (:context fact)
        parts (str/split type-name #"\.")
        head (first parts)
        suffix (when (> (count parts) 1) (str "." (str/join "." (rest parts))))
        imported (map #(str % suffix) (get explicit head))
        scopes (loop [fqn (:ns fact), acc []]
                 (let [i (str/last-index-of fqn ".")]
                   (if (and i (>= i (count package)))
                     (recur (subs fqn 0 i) (conj acc (str fqn "." type-name)))
                     acc)))]
    (distinct
      (concat (when (str/includes? type-name ".") [type-name])
              imported scopes
              [(str (when (seq package) (str package ".")) type-name)]
              (map #(str % "." type-name) stars)))))

(defn- resolve-type [index fact type-name]
  (let [found (->> (candidates fact type-name) (mapcat #(get index %)) distinct vec)]
    (cond
      (= 1 (count found)) {:class (first found)}
      (> (count found) 1) {:ambiguous found}
      :else (let [{:keys [explicit]} (:context fact)
                  parts (str/split type-name #"\.")
                  imported (get explicit (first parts))
                  suffix (when (> (count parts) 1) (str "." (str/join "." (rest parts))))]
              (cond
                (= 1 (count imported)) {:foreign (str (first imported) suffix)}
                (and (str/includes? type-name ".")
                     (Character/isLowerCase ^char (first type-name))) {:foreign type-name}
                :else {})))))

(defn- reference-edges [facts index]
  (reduce
    (fn [{:keys [edges foreign diagnostics] :as acc} fact]
      (reduce
        (fn [state {:keys [name relation line]}]
          (let [{target :class external :foreign ambiguous :ambiguous}
                (resolve-type index fact name)]
            (cond
              (and target (not= (:id fact) (:id target)))
              (update state :edges conj
                      {:from (:id fact) :to (:id target)
                       :kind (if (= relation :super)
                               (if (= :interface (:stereotype target)) :implements :inheritance)
                               :dependency)})

              external
              (-> state
                  (update :foreign conj external)
                  (update :edges conj {:from (:id fact) :to (keyword external) :kind :dependency}))

              (seq ambiguous)
              (update state :diagnostics conj
                      {:kind :ambiguous-reference :file (:file fact)
                       :line (line-at (get-in fact [:context :text]) line)
                       :reference name :candidates (mapv :ns ambiguous)})

              :else state)))
        acc (:type-sites fact)))
    {:edges [] :foreign #{} :diagnostics []} facts))

(defn- resolve-one [index fact type-name]
  (:class (resolve-type index fact type-name)))

(defn- hilt-edges [facts index]
  (let [binding-result
        (reduce
          (fn [acc owner]
            (reduce
              (fn [state {:keys [implementation interface into-set qualifier line]}]
                (let [impl (resolve-one index owner implementation)
                      contract (resolve-one index owner interface)]
                  (if (and impl contract)
                    (-> state
                        (update :bindings conj {:implementation impl :interface contract
                                                :into-set into-set :qualifier qualifier})
                        (update :edges conj {:from (:id impl) :to (:id contract)
                                             :kind :implements :label "Hilt @Binds"}))
                    (update state :diagnostics conj
                            {:kind :unresolved-hilt-binding :file (:file owner)
                             :line (line-at (get-in owner [:context :text]) line)
                             :implementation implementation :interface interface}))))
              acc (:bindings owner)))
          {:bindings [] :edges [] :diagnostics []} facts)
        bindings (:bindings binding-result)
        contributions (group-by (juxt (comp :id :interface) :qualifier)
                                (filter :into-set bindings))]
    (reduce
      (fn [acc consumer]
        (reduce
          (fn [state {:keys [interface qualifier line]}]
            (if-let [contract (resolve-one index consumer interface)]
              (let [exact (get contributions [(:id contract) qualifier])
                    other (seq (mapcat val (filter #(= (:id contract) (first (key %))) contributions)))]
                (cond
                  (seq exact)
                  (update state :edges into
                          (map (fn [binding]
                                 {:from (:id consumer) :to (get-in binding [:implementation :id])
                                  :kind :association :label "Hilt set"}) exact))

                  other
                  (update state :diagnostics conj
                          {:kind :hilt-qualifier-mismatch :file (:file consumer)
                           :line (line-at (get-in consumer [:context :text]) line)
                           :interface (:ns contract) :qualifier qualifier})

                  :else state))
              (update state :diagnostics conj
                      {:kind :unresolved-hilt-consumer :file (:file consumer)
                       :line (line-at (get-in consumer [:context :text]) line)
                       :interface interface})))
          acc (:consumers consumer)))
      {:edges (:edges binding-result) :diagnostics (:diagnostics binding-result)} facts)))

(defn- public-class [fact]
  (select-keys fact [:id :name :ns :stereotype :synthetic :lang :file :source-root
                     :line :end-line :ops :fields]))

(defn- foreign-class [fqn]
  {:id (keyword fqn) :name fqn :ns fqn :foreign true :lang :kotlin})

(defn- scan-modules [factory root modules]
  (reduce
    (fn [acc {:keys [id src] :as module}]
      (when-not (and id (seq (str src)))
        (throw (ex-info "Kotlin module requires :id and :src" {:module module})))
      (let [source-root (canonical-file (io/file root src))]
        (when-not (and (.isDirectory source-root)
                       (.startsWith (.toPath source-root) (.toPath root)))
          (throw (ex-info "Kotlin module source root is missing or outside root"
                          {:module id :src (.getPath source-root) :root (.getPath root)})))
        (reduce (fn [state file]
                  (try
                    (let [parsed (parse-file factory id source-root file)]
                      (-> state
                          (update :facts into (:facts parsed))
                          (update :diagnostics into (:diagnostics parsed))))
                    (catch Throwable error
                      (update state :diagnostics conj
                              {:kind :parser-failure :file (.getPath file)
                               :message (.getMessage error)}))))
                acc (source-files source-root))))
    {:facts [] :diagnostics []} modules))

(defrecord KotlinGraph []
  graph/LanguageGraph
  (scan [_ root opts]
    (let [root (canonical-file root)
          modules (:modules opts)]
      (when-not (.isDirectory root)
        (throw (ex-info "Kotlin scan root is not a directory" {:root (.getPath root)})))
      (when-not (seq modules)
        (throw (ex-info "Kotlin scan requires explicit :modules" {:opts opts})))
      (let [disposable (Disposer/newDisposable)]
        (try
          (let [environment (KotlinCoreEnvironment/createForProduction
                              disposable (CompilerConfiguration.)
                              EnvironmentConfigFiles/JVM_CONFIG_FILES)
                factory (KtPsiFactory. (.getProject environment) false)
                parsed (scan-modules factory root modules)
                facts (mapv #(decorate-fact % (:prefix opts)) (:facts parsed))
                index (group-by :ns facts)
                references (reference-edges facts index)
                hilt (hilt-edges facts index)
                edges (->> (concat (:edges references) (:edges hilt))
                           (group-by (juxt :from :to :kind))
                           vals
                           (mapv #(or (first (filter :label %)) (first %))))]
            {:classes (into (mapv public-class facts)
                            (map foreign-class (sort (:foreign references))))
             :edges edges
             :diagnostics (vec (concat (:diagnostics parsed)
                                       (:diagnostics references)
                                       (:diagnostics hilt)))})
          (finally
            (Disposer/dispose disposable)))))))

(def impl (->KotlinGraph))

(graph/register! :kotlin impl)
