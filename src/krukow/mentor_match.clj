(ns krukow.mentor-match
  (:require [clojure.pprint :as p]
            [krukow.mentor-match.sheets-parser :as parser]
            [krukow.mentor-match.int-constraints :as ic]
            [krukow.mentor-match.domain :as dom]))

(defn match
  [{:keys [config sheet-url] :as conf}]
  (let [all-mentee-preferences (parser/parse-sheet sheet-url config)

        {:keys [mentee-preferences available-mentors]}
        (dom/preferences-to-solve-for all-mentee-preferences)

        nmentors (count available-mentors)

        ;; map mentees and mentors to ints to use int solver
        {mentees :source->int
         mentors :target->int
         :as int-domain} (ic/map->int-domains mentee-preferences)]
    (println "From a total of" (count all-mentee-preferences) "mentees.")
    (println "With" nmentors "available mentors.")
    (println "\nRunning constraint solver...\n")
    (loop [n (count mentee-preferences)]
      (println "Trying to match:" n "mentees")
      (let [solutions (filter seq (ic/solutions-for n int-domain dom/scoring-fn))
            bs (ic/best-solution solutions)]
        (if bs
          (p/pprint bs)
          (if (> n 2)
            (do
              (println "No solution exists that matches all preferences...")
              (recur (dec n)))
            (println "Unable to find any solution.")))))))
