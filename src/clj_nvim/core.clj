(ns clj-nvim.core
  (:require
    [clj-nvim.prettify :as pretty]
    [neovim-client.nvim :as nvim])
  (:gen-class))

(defn -main
  [& args]
  (let [conn (nvim/new 2)]
    (nvim/register-method!
      conn
      "tidy-ns"
      (fn [] (pretty/update-current-ns conn)))
    (println "Registered, now waiting")
    (while true
      (Thread/sleep 100))))
