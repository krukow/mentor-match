(ns krukow.mentor-match.ui
  (:require [clojure.pprint :as p]
            [clojure.string :as s]))

(defn find-mentee
  [mentee mentees]
  (->> mentees
       (filter #(= mentee (:mentee %)))
       first))

(defn find-mentor
  [mentor mentors]
  (->> mentors
       (filter #(= mentor (s/lower-case (:handle %))))
       first))

(defn display-solution
  [{:keys [score solution]} available-mentors all-mentee-preferences]
  (doseq [[mentee mentor] solution]
    (println "-------------------------------------------")
    (println "Match: mentee:" mentee "-> mentor:" mentor)
    (println "-------------------------------------------")
    (println "Match details:\n")
    (let [mentor-data (find-mentor (s/lower-case mentor) available-mentors)
          mentee-data (find-mentee mentee all-mentee-preferences)]
      (println "Mentee info:" mentee)
      (p/pprint (select-keys mentee-data [:looking-for
                                          :mentor-preferences
                                          :areas-of-growth
                                          :not-looking-for
                                          :location])
                     )
      (println "\nMentor info:" mentor)
      (p/pprint mentor-data))
    )
  (println "\n\nOverall solution stats:\n #matches =" (count solution)
           "\n preference score = " score)


  )

(defn display-solution-short
  [{:keys [solution]}]
  (p/pprint solution))
