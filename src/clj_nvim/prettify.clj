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

(defn indent-items
  "Given a sequence of sexps, indent them to the given level by interleaving
  them with rewrite-clj whitespace & newline nodes"
  [level items]
  (->> items
       (mapcat (fn [s] [(node/spaces level) s (node/newlines 1)]))
       ; strip off trailing newline
       butlast))

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
                         (sort-by first)
                         (indent-items (inc col))
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

(defn check-refers
  [[req-ns & {refs :refer as :as} :as req] zbody]
  (letfn [(used? [v] (some? (z/find-value zbody z/next v)))]
    (if-not refs
      req
      (if-let [used (seq (filter used? refs))]
        [req-ns :as as :refer (vec used)]
        [req-ns :as as]))))

(defn filter-unused-refers
  [zns zbody]
  (let [requires (-> zns (find-under :require))]
    (loop [z requires]
      (cond
        (z/end? (z/right z)) (-> z z/up z/up)

        (z/vector? z) (recur (-> z
                                 (z/edit (fn [req] (check-refers req zbody)))
                                 z/right))

        :else (recur (z/right z))))))

;; Neovim interaction

(defn read-ns-form
  "Read the namespace declaration from the current buffer of the given neovim
  client as a rewrite-clj zipper"
  [buf conn]
  (let [lines (buf/get-lines conn buf 0 -1 false)
        data (z/of-string (string/join "\n" lines) {:track-position? true})]
    (-> data z/next (z/find-value 'ns) z/up)))

(defn update-current-ns
  "Read the current namespace, format it according to our style preference,
  then write it back"
  [conn]
  (let [buf (api/get-current-buf conn)
        orig-ns (read-ns-form buf conn)
        start-line (-> orig-ns z/position first dec)
        end-line (-> orig-ns z/right z/position first dec dec)
        sorted (-> orig-ns
                   fix-require-form
                   fix-import-form
                   z/string
                   (string/split #"\n"))]
    (when-not (= (buf/get-lines conn buf start-line end-line false)
                 sorted)
      (buf/set-lines
        conn buf
        start-line end-line false sorted))))

(defn remove-unused-refers
  "Go through the namespace and remove any referred vars that are unused"
  [conn]
  (let [buf (api/get-current-buf conn)
        ns-form (read-ns-form buf conn)
        start-line (-> ns-form z/position first dec)
        end-line (-> ns-form z/right z/position first dec dec)
        body (z/right ns-form)
        updated (-> (filter-unused-refers ns-form body)
                    z/string
                    (string/split #"\n"))]
    (buf/set-lines
      conn buf
      start-line end-line false updated)))
