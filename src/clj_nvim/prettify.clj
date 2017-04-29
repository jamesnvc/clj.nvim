(ns clj-nvim.prettify
  (:require
    [clojure.string :as string]
    [neovim-client.2.api :as api]
    [neovim-client.2.api.buffer :as buf]
    [rewrite-clj.node :as node]
    [rewrite-clj.zip :as z]))

(defn read-ns-form
  [conn]
  (let [buf (api/get-current-buf conn)
        lines (buf/get-lines conn buf 0 -1 false)
        data (z/of-string (string/join "\n" lines) {:track-position? true})]
    (-> data z/next (z/find-value 'ns) z/up)))

(defn ensure-vec
  [n]
  (if (vector? n) n (vector n)))

(defn canonicalize-require
  [[req-ns & {:keys [as refer]}]]
  (vec
    (concat
      [req-ns]
      (when as [:as as])
      (when refer [:refer (vec (sort refer))]))))

(defn sort-require-form
  [ns-form]
  (let [req-form (-> ns-form (z/find-value z/next :require) z/up)
        [line col] (z/position req-form)]
    (-> req-form
        (z/edit (fn [[_ & fs]]
                  (->> fs
                       (map (comp canonicalize-require ensure-vec))
                       (sort-by first)
                       node/line-separated
                       (mapcat (fn [n] [(node/spaces (+ 2 col)) n]))
                       (cons (node/newlines 1))
                       (cons :require)
                       node/list-node)))
        z/up)))

(defn update-current-ns
  [conn]
  (let [orig-ns (read-ns-form conn)
        start-line (-> orig-ns z/position first dec)
        end-line (-> orig-ns z/right z/position first dec dec)
        sorted (-> (sort-require-form orig-ns)
                   z/string (string/split #"\n"))]
    (buf/set-lines
      conn (api/get-current-buf conn)
      start-line end-line false sorted)))
