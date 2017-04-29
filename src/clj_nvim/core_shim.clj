(ns clj-nvim.core-shim
  "Shim to dynamically require stuff to avoid AOT compilation issue
  See CLJ-1544: https://dev.clojure.org/jira/browse/CLJ-1544"
  (:gen-class))

(defn -main
  [& args]
  (require 'clj-nvim.core)
  ((resolve 'clj-nvim.core/-main) args))
