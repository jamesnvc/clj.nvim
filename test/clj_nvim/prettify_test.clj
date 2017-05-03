(ns clj-nvim.prettify-test
  (:require
    [clj-nvim.prettify :as pretty]
    [clojure.string :as string]
    [clojure.test :refer [deftest testing is]]
    [rewrite-clj.zip :as z]))


(deftest formatting-ns-form
  (testing "can format namespace form"
    (let [processed (->
                      (string/join "\n"
                                   ["(ns neovim-client.parser"
                                    "  \"Turn Neovim's api-info schema into Clojure functions.\""
                                    "  (:require"
                                    "    [clojure.java.io :as io]"
                                    "    [clojure.string :as string]"
                                    "    [clojure.walk :refer [walk prewalk] :as walk]"
                                    "    [msgpack.clojure-extensions]"
                                    "    clojure.set"
                                    "    [msgpack.core :as msgpack])"
                                    "  (:import (java.foo.Bar)"
                                    "           (net.quux Z B A)"
                                    "           ))"])
                      (z/of-string {:track-position? true}) z/next (z/find-value 'ns) z/up
                      pretty/fix-require-form
                      pretty/fix-import-form
                      z/string
                      (string/split #"\n"))]
      (is (= processed
             ["(ns neovim-client.parser"
              "  \"Turn Neovim's api-info schema into Clojure functions.\""
              "  (:require"
              "    [clojure.java.io :as io]"
              "    [clojure.set]"
              "    [clojure.string :as string]"
              "    [clojure.walk :as walk :refer [prewalk walk]]"
              "    [msgpack.clojure-extensions]"
              "    [msgpack.core :as msgpack])"
              "  (:import"
              "    (java.foo Bar)"
              "    (net.quux A B Z)))"])))))
