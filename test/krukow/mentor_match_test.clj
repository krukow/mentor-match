(ns krukow.mentor-match-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [krukow.mentor-match.map-utils :as u]
            [krukow.mentor-match.int-constraints :as ic]
            [krukow.mentor-match.domain :as dom]
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
        {:keys [mentee-preferences]} (dom/preferences-to-solve-for
                                      all-mentee-preferences)

        int-domain (ic/map->int-domains mentee-preferences)

        expected-solution {:score 165,
                           :solution
                           (into {}
                                 (list ["ricky@email.com" "@ethel"]
                                       ["mary@email.com" "@lucy"]
                                       ["bob@email.com" "@fred"]))}
        solutions (ic/solutions-for 3 int-domain dom/scoring-fn)]
    (testing "Simple match"
      (is (= 1 (count solutions)))
      (is (= 1 (count (first solutions))))
      (let [sol (ffirst solutions)]
        (is (= (:score expected-solution) (:score sol)))
        (is (= (:solution expected-solution) (into {} (:solution sol))))))))
