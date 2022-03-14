(ns krukow.mentor-match-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [krukow.mentor-match :refer :all]))

(deftest simple-integration
  (let [all-mentee-preferences (list {:timestamp "2/3/2022 4:19:57",
                                      :mentee "bob@email.com",
                                      :admin "",
                                      :status "",
                                      :mentor-preferences '("@lucy" "@fred" "@ethel"),
                                      :mentor-handle nil}
                                     {:timestamp "2/3/2022 14:00:41",
                                      :mentee "mary@email.com",
                                      :admin "",
                                      :status "",
                                      :mentor-preferences '("@lucy" "@ethel"),
                                      :mentor-handle nil}
                                     {:timestamp "2/3/2022 17:05:40",
                                      :mentee "ricky@email.com",
                                      :admin "",
                                      :status "",
                                      :mentor-preferences '("@ethel" "@fred"),
                                      :mentor-handle nil})
        all-mentees (->> all-mentee-preferences
                         (map :mentee)
                         (into #{})
                         indexed-map)
        mentors-set (->> all-mentee-preferences
                         (mapcat :mentor-preferences)
                         (into #{}) ;;unique
                         )
        taken-mentors-set (into #{}
                                (->> all-mentee-preferences
                                     (map :mentor-handle)
                                     (filter (complement nil?))))

        mentors (indexed-map (set/difference mentors-set
                                             taken-mentors-set))
        mcount (count mentors)
        ;; can't have more mentees than mentors :/
        mentee-preferences (-> (remove-taken-preferences
                                taken-mentors-set
                                all-mentee-preferences)
                               (select-mentees mcount))

        mentees (->> mentee-preferences
                     (map :mentee)
                     (into #{}) ;; unique
                     indexed-map)

        expected-solution {:score 165,
                           :solution
                           (list ["ricky@email.com" "@ethel"]
                                 ["mary@email.com" "@lucy"]
                                 ["bob@email.com" "@fred"])}
        solutions (solutions-for 3 mentees mentors mentee-preferences)
        ]
    (testing "Simple match"
      (is (= 1 (count solutions)))
      (is (= 1 (count (first solutions))))
      (is (= expected-solution (ffirst solutions) )))))
