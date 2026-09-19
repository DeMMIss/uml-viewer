(ns uml-viewer.adapters.core
  (:require [clojure.string :as str]
            [uml-viewer.adapters.sketch :as sketch]))

(def help-text
  (str "Usage: clj -M:run [options] [edn-file]\n"
       "\n"
       "  edn-file          Diagram to watch (default: examples/library.edn).\n"
       "                    Loads immediately; no agent or terminal is launched.\n"
       "  --standalone      Explicitly select the default local viewer.\n"
       "  --grok            Opt in to the upstream tmux/Grok companion.\n"
       "\n"
       "  --restart         Associated agent only (via :uml-viewer-restart).\n"
       "                    New JVM, keep the existing Grok tmux session.\n"
       "                    Loads the EDN immediately (does not wait for\n"
       "                    :display). Do not use this if no companion is attached.\n"
       "\n"
       "  -h, --help        Print this help and exit.\n"))

(defn parse-args
  "Standalone by default. Grok and its restart flow require explicit flags."
  [args]
  (let [args (keep identity args)
        help? (boolean (some #{"--help" "-h"} args))
        restart? (boolean (some #{"--restart"} args))
        grok? (boolean (some #{"--grok"} args))
        flags #{"--help" "-h" "--restart" "--standalone" "--grok"}
        paths (remove flags args)]
    (when (or (> (count paths) 1) (some #(str/starts-with? % "-") paths))
      (throw (ex-info "Expected one EDN path and supported flags; see --help." {:args args})))
    (when (and (some #{"--standalone"} args) (or restart? grok?))
      (throw (ex-info "--standalone cannot be combined with --grok or --restart." {})))
    {:help? help?
     :restart? restart?
     :standalone? (not (or restart? grok?))
     :path (or (first paths) "examples/library.edn")}))

(defn launch!
  "Launch with a source adapter and an optional local regeneration callback."
  [source-impl regenerate args]
  (let [{:keys [path restart? standalone? help?]} (parse-args args)]
    (if help?
      (do (print help-text) :help)
      (do
        (sketch/start! path source-impl restart?
                       {:standalone? standalone? :regenerate regenerate})
        (println "Watching" path)
        (println "Double-click a class for its card. Scroll to pan (Shift-scroll for horizontal). Ctrl+/− zoom; Ctrl+0 resets. R reloads. Click the real diagram above Proposals, or a proposal to show it.")))))

(defn start! [source-impl & args]
  (launch! source-impl nil args))
