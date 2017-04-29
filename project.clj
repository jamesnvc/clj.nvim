(defproject clj-nvim "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 [org.clojure/tools.reader "1.0.0-beta4"]
                 [jamesnvc/neovim-client "0.1.1"]
                 [rewrite-clj "0.6.0" :exclusions [org.clojure/tools.reader]]]
  :main clj-nvim.core-shim

  :profiles {:uberjar {:aot [clj-nvim.core-shim]}})
