(ns clj-nvim.prettify
  (:require
    [clojure.string :as string]
    [neovim-client.2.api :as api]
    [neovim-client.2.api.buffer :as buf]
    [rewrite-clj.node :as node]
    [rewrite-clj.zip :as z]
    [rewrite-clj.zip.base :as base]))

(defn read-ns-form
  [conn]
  (let [buf (api/get-current-buf conn)
        lines (buf/get-lines conn buf 0 -1 false)
        data (z/of-string (string/join "\n" lines) {:track-position? true})]
    (-> data z/next (z/find-value 'ns) z/up)))

(defn ensure-vec
  [n]
  (cond
    (not (coll? n)) (vector n)
    (not (vector? n)) (vec n)
    :else n))

(defn ensure-list
  [n]
  (cond
    (not (coll? n)) (list n)
    (not (list? n)) (seq n)
    :else n))

(defn find-under
  [zloc v]
  (let [after (z/right zloc)]
    (loop [z zloc]
      (cond
        (= z after) nil

        (and (= :token (base/tag z))
             (= v (base/sexpr z)))
        z

        :else (recur (z/next z))) )))

(defn canonicalize-require
  [[req-ns & {:keys [as refer]}]]
  (vec
    (concat
      [req-ns]
      (when as [:as as])
      (when refer [:refer (vec (sort refer))]))))

(defn fix-form
  [ns-form startk fixfn]
  (if-let [req-form (-> ns-form (find-under startk) z/up)]
    (let [[_ col] (z/position req-form)]
      (-> req-form
          (z/edit (fn [[_ & fs]]
                    (->> fs
                         (map fixfn)
                         (sort-by first)
                         (mapcat (fn [n] [(node/spaces (inc col))
                                          n
                                          (node/newlines 1)]))
                         butlast
                         (cons (node/newlines 1))
                         (cons startk)
                         node/list-node)))
          z/up))
    ns-form))

(defn fix-require-form
  [ns-form]
  (fix-form ns-form :require (comp canonicalize-require ensure-vec)))

(defn canonicalize-import
  [imp]
  (if (= 1 (count imp))
    ; convert to prefix list
    (-> (first imp) str (string/split #"\.")
        (->> ((juxt (comp symbol (partial string/join ".") butlast)
                    (comp symbol last)))
             seq))
    (cons (first imp) (sort (rest imp)))))

(defn fix-import-form
  [ns-form]
  (fix-form ns-form :import (comp canonicalize-import ensure-list)))

(defn update-current-ns
  [conn]
  (let [orig-ns (read-ns-form conn)
        start-line (-> orig-ns z/position first dec)
        end-line (-> orig-ns z/right z/position first dec dec)
        sorted (-> orig-ns
                   fix-require-form
                   fix-import-form
                   z/string
                   (string/split #"\n"))]
    (buf/set-lines
      conn (api/get-current-buf conn)
      start-line end-line false sorted)))
