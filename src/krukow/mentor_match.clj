(ns krukow.mentor-match
  (:require [clojure.pprint :as p]
            [krukow.mentor-match.sheets-parser :as parser]
            [krukow.mentor-match.ui :as ui]
            [krukow.mentor-match.github-projects :as projects]
            [krukow.mentor-match.int-constraints :as ic]
            [krukow.mentor-match.domain :as dom]))

(defn match
  [{:keys [config sheet-url] :as conf}]
  (let [all-mentee-preferences (parser/parse-sheet sheet-url config)
        available-mentors (projects/available-mentors)

        {:keys [mentee-preferences available-mentors]}
        (dom/preferences-to-solve-for all-mentee-preferences available-mentors)

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

(defn match-interactive
  [{:keys [config sheet-url] :as conf}]
  (let [all-mentee-preferences (parser/parse-sheet sheet-url config)
        all-available-mentors (projects/available-mentors)

        {:keys [mentee-preferences available-mentors]}
        (dom/preferences-to-solve-for all-mentee-preferences all-available-mentors)

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
            sorted-solutions (ic/sort-by-best-solution solutions)]
        (when (seq sorted-solutions)
          (println "Found " (count sorted-solutions) " solutions.")
          (doseq [solution sorted-solutions]
            (ui/display-solution solution all-available-mentors all-mentee-preferences)
            (println "Accept above matches? (y/n)")
            (loop [answer (read-line)]
              (if (#{"y" "n"} answer)
                (if (= "y" answer)
                  (do
                    (println "To enact the match, please open" (projects/mentor-board-url)
                             "and move mentors: " (map second (:solution solution))
                             "to unavailable.")
                    (println "Then please update: " sheet-url)
                    (println "with these matches:")
                    (ui/display-solution-short solution)
                    (System/exit 0))
                  (println "\n================== Next Solution ==================\n"))
                (recur (read-line))))))
        (if (> n 2)
          (do
            (println "No solution exists that matches all preferences...")
            (recur (dec n)))
          (println "Unable to find any solution."))))))
