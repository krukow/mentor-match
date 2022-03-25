(ns krukow.mentor-match.map-utils)

;; Utils

(defn into-indexed-sorted-map
  "Eagerly maps any seq to an indexed sorted map {0 e1 1 e2, ...}
   Example: (indexed-sorted-map inc {:a 1 :b 2 :c 3})
  => {:a 2, :b 3, :c 4}"
  [coll]
  (into (sorted-map) (map-indexed vector) coll))

(defn map-values
  "Like map takes and returns a map.
  Maps across the values of a map-data structure, preserving keys.
  Example: (map-values inc {:a 1 :b 2 :c 3})
  => {:a 2, :b 3, :c 4}"
  [f m]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} m))

(defn deep-merge
  "Merges maps of similar shapes (e.g. for default+override in config files).
  The default must have all the nested keys present."
  [default overrides]
  (letfn [(deep-merge-rec [a b]
            (if (map? a)
              (merge-with deep-merge-rec a b)
              b))]
    (reduce deep-merge-rec nil (list default overrides))))
