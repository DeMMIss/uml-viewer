(ns uml-viewer.grok-status
  "Status sentences from Grok output, same idea as swarm-forge pack_web_status."
  (:require [clojure.string :as str]))

(defn- fold-apostrophe [s]
  (str/replace (or s "") "\u2019" "'"))

(defn strip-terminal-controls [text]
  (-> (or text "")
      (str/replace #"\u001b\][^\u0007\u001b]*(?:\u0007|\u001b\\)" "")
      (str/replace #"\u001b\[[0-?]*[ -/]*[@-~]" "")
      (str/replace #"[\u0000-\u0008\u000b\u000c\u000e-\u001a\u001c-\u001f\u007f]" "")))

(defn- thought-line? [line]
  (boolean (re-find #"^\s*┃" (or line ""))))

(defn- tool-start-line? [line]
  (boolean (re-find #"^\s*(?:◆|\$)\s" (or line ""))))

(defn- timer-line? [line]
  (let [n (str/lower-case (fold-apostrophe (str/trim (or line ""))))]
    (boolean
      (or (re-find #"^(?:[\u2800-\u28ff]\s*)?(?:thinking|waiting for response|working)\b" n)
          (re-find #"^(?:[\u2800-\u28ff]\s*)?[0-9]+(?:\.[0-9]+)?s(?:\s+.*)?$" n)
          (and (re-find #"\b[0-9]+(?:\.[0-9]+)?s\b" n)
               (or (str/includes? n "tokens")
                   (str/includes? n "esc to interrupt")))))))

(defn- chrome-line? [line]
  (let [n (str/lower-case (fold-apostrophe (str/trim (or line ""))))]
    (boolean
      (or (str/blank? n)
          (timer-line? line)
          (re-find #"^notify:" n)
          (re-find #"^worked for\b" n)
          (re-find #"^grok\s" n)
          (re-find #"^❯" n)
          (re-find #"waiting for response" n)
          (re-find #"^always-approve\b" n)
          (re-find #"^enter:send\b" n)
          (re-find #"·\s*/help\b" n)))))

(defn- prose-lines [text]
  (:text
    (reduce
      (fn [{:keys [in-tool? text]} line]
        (cond
          (thought-line? line)
          {:in-tool? false :text (conj text "")}

          (tool-start-line? line)
          {:in-tool? true :text (conj text "")}

          in-tool?
          {:in-tool? true :text text}

          (chrome-line? line)
          {:in-tool? false :text (conj text "")}

          :else
          {:in-tool? false :text (conj text (str/trim line))}))
      {:in-tool? false :text []}
      (str/split-lines (strip-terminal-controls text)))))

(defn- sentences [text]
  (->> (str/split-lines (or text ""))
       (map str/trim)
       (remove str/blank?)
       (str/join " ")
       (#(str/split % #"(?<=[.!?…])\s+"))
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- complete-status-sentence? [sentence]
  (boolean
    (and (not (str/blank? sentence))
         (not (chrome-line? sentence))
         (re-find #"[.!?…][\"'’”`)\]]*$" (str/trim sentence)))))

(defn status-sentences
  "Prose status sentences from a Grok transcript (newest last)."
  [text]
  (->> (str/join "\n" (prose-lines text))
       sentences
       (filterv complete-status-sentence?)))

(defn latest-status
  "Up to two newest status sentences."
  [text]
  (vec (take-last 2 (status-sentences text))))

(defn format-status-line
  [sentence]
  (str "◎ " (str/trim sentence) "\n"))

(defn format-working
  [elapsed-s]
  (str "◎ working " (long elapsed-s) "s\n"))
