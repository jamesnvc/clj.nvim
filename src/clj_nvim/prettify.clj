(ns clj-nvim.prettify
  "Prettify Clojure source code"
  (:require
    [clojure.string :as string]
    [neovim-client.2.api :as api]
    [neovim-client.2.api.buffer :as buf]
    [rewrite-clj.node :as node]
    [rewrite-clj.zip :as z]
    [rewrite-clj.zip.base :as base]))

;; Helper functions

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
  "Find the token `v` under the zipper `zloc` without going past it"
  [zloc v]
  (let [after (z/right zloc)]
    (loop [z zloc]
      (cond
        (= z after) nil

        (and (= :token (base/tag z))
             (= v (base/sexpr z)))
        z

        :else (recur (z/next z))) )))

;; Prettifying require & import declarations in ns form

(defn canonicalize-require
  "Format a given require vector in our canonical form"
  [[req-ns & {:keys [as refer]}]]
  (vec
    (concat
      [req-ns]
      (when as [:as as])
      (when refer [:refer (vec (sort refer))]))))

(defn canonicalize-import
  "Format a given import list in our canonical form (i.e. a sorted prefix list)"
  [imp]
  (if (= 1 (count imp))
    ; convert to prefix list
    (-> (first imp) str (string/split #"\.")
        (->> ((juxt (comp symbol (partial string/join ".") butlast)
                    (comp symbol last)))
             seq))
    (cons (first imp) (sort (rest imp)))))

(defn fix-form
  "Helper function for fixing require or import forms in a namespace
  declaration, indenting the import/requires as we prefer"
  [ns-form startk fixfn]
  (if-let [req-form (-> ns-form (find-under startk) z/up)]
    (let [[_ col] (z/position req-form)]
      (-> req-form
          (z/edit (fn [[_ & fs]]
                    (->> fs
                         (map fixfn)
                         ; sort the require/imports
                         (sort-by first)
                         ; indent them & put them one per line
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

(defn fix-import-form
  [ns-form]
  (fix-form ns-form :import (comp canonicalize-import ensure-list)))

;; Neovim interaction

(defn read-ns-form
  "Read the namespace declaration from the current buffer of the given neovim
  client as a rewrite-clj zipper"
  [conn]
  (let [buf (api/get-current-buf conn)
        lines (buf/get-lines conn buf 0 -1 false)
        data (z/of-string (string/join "\n" lines) {:track-position? true})]
    (-> data z/next (z/find-value 'ns) z/up)))

(defn update-current-ns
  "Read the current namespace, format it according to our style preference,
  then write it back"
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
