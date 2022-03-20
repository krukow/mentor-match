(ns krukow.mentor-match
  (:require [clojure.math.combinatorics :as combo]
            [clojure.pprint :as p]
            [krukow.mentor-match.sheets-parser :as parser]
            [krukow.mentor-match.map-utils :as u]
            [krukow.mentor-match.int-constraints :as ic]
            [krukow.mentor-match.domain :as dom])
  (:import (org.chocosolver.solver Model)
           (org.chocosolver.solver.variables IntVar
                                             SetVar)
           (org.chocosolver.solver.constraints.extension Tuples)))

(declare solutions-for)

(defn match
  [{:keys [config sheet-url] :as conf}]
  (let [all-mentee-preferences (parser/parse-sheet sheet-url config)

        {:keys [mentee-preferences available-mentors]}
        (dom/preferences-to-solve-for all-mentee-preferences)

        nmentors (count available-mentors)

        {mentees :source->int
         mentors :target->int
         :as int-domain} (ic/map->int-domains mentee-preferences)]
    (println "From a total of" (count all-mentee-preferences) "mentees.")
    (println "With" nmentors "available mentors.")
    (println "\nRunning constraint solver...\n")
    (loop [n (count mentee-preferences)]
      (println "Trying to match:" n "mentees")
      (let [solutions (filter seq (solutions-for n int-domain))
            bs (ic/best-solution solutions)]
        (if (seq bs)
          (p/pprint bs)
          (if (> n 2)
            (do
              (println "No solution exists that matches all preferences...")
              (recur (dec n)))
            (println "Unable to find any solution.")))))))

(defn solutions-for [n {int->mentee :int->source
                        int->mentor :int->target
                        constraints :constraints}]
  (let [mcount (count int->mentor) ;; mentor count
        ;; seq of (pick n mentees)
        mentee-combos (combo/combinations (keys int->mentee) n)
        cnt (count mentee-combos)]
    (println "Trying " cnt "combinations...")
    (->> mentee-combos ;; for each choice of n mentees
         (map-indexed
          (fn [i ms]
            (when (zero? (mod i 1000))
              (printf "%d/%d" i cnt)
              (println))
            (let [ms-constraints (into (sorted-map)
                                       (select-keys constraints ms))
                  int-solution->domain #(update
                                         %
                                         :solution
                                         (fn [solution]
                                           (map (fn [i-mentee i-mentor]
                                                  [(get int->mentee i-mentee)
                                                   (get int->mentor i-mentor)])
                                                (keys ms-constraints)
                                                solution)))]
              (->> (ic/solve ms-constraints dom/scoring-fn)
                   (map int-solution->domain))))))))

(comment
  (def solutions
    (filter seq (solutions-for 35)))
  (defn validate-solution
    [s n]
    (let [unique-mentors (into #{} (map second (:solution s)))]
      (= (count unique-mentors) n)))

  )
