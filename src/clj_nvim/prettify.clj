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

(defn sort-require-form
  [ns-form]
  (if-let [req-form (-> ns-form (find-under :require) z/up)]
    (let [[_ col] (z/position req-form)]
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
          z/up))
    ns-form))

(defn canonicalize-import
  [imp]
  (if (= 1 (count imp))
    ; convert to prefix list
    (-> (first imp) str (string/split #"\.")
        (->> ((juxt (comp symbol (partial string/join ".") butlast)
                    (comp symbol last)))
             seq))
    (cons (first imp) (sort (rest imp)))))

(defn sort-import-form
  [ns-form]
  (if-let [req-form (-> ns-form (find-under :import) z/up)]
    (let [[line col] (z/position req-form)]
      (-> req-form
          (z/edit (fn [[_ & fs]]
                    (->> fs
                         (map (comp canonicalize-import ensure-list))
                         (sort-by first)
                         node/line-separated
                         (mapcat (fn [n] [(node/spaces (+ 2 col)) n]))
                         (cons (node/newlines 1))
                         (cons :import)
                         node/list-node)))
          z/up))
    ns-form))

(defn update-current-ns
  [conn]
  (let [orig-ns (read-ns-form conn)
        start-line (-> orig-ns z/position first dec)
        end-line (-> orig-ns z/right z/position first dec dec)
        sorted (-> orig-ns
                   sort-require-form
                   sort-import-form
                   z/string
                   (string/split #"\n"))]
    (buf/set-lines
      conn (api/get-current-buf conn)
      start-line end-line false sorted)))
