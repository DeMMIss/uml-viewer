(ns uml-viewer.adapters.core-spec
  (:require [speclj.core :refer :all]
            [uml-viewer.adapters.core :as core]))

(describe "cli args"
  (it "defaults the path and does not restart"
    (should= {:path "examples/library.edn" :restart? false}
             (core/parse-args nil))
    (should= {:path "examples/library.edn" :restart? false}
             (core/parse-args [])))

  (it "takes a path and the --restart flag in either order"
    (should= {:path "doc.edn" :restart? false}
             (core/parse-args ["doc.edn"]))
    (should= {:path "examples/library.edn" :restart? true}
             (core/parse-args ["--restart"]))
    (should= {:path "doc.edn" :restart? true}
             (core/parse-args ["--restart" "doc.edn"]))
    (should= {:path "doc.edn" :restart? true}
             (core/parse-args ["doc.edn" "--restart"]))))
