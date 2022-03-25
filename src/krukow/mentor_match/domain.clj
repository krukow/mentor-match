(ns krukow.mentor-match.domain
  (:require [clojure.set :as set]
            [clojure.string :as st]))

(defn scoring-fn
  "Scoring seq of preferences: p1, ..., pn so that
  score for pn: w, p_(n-1): 2w, ..., p1: n*w.
  and sum of scores is 100: w + 2*w + ... + n*w == 100
  (<=> w = 100/(1 + 2 + ... + n))"
  [solutions full-target-domain]
  (let [points 100
        n (count solutions)
        scores (if (> n 0)
                 (let [weights (reverse (range 1 (inc n))) ;; (n,  n-1, ..., 1)
                       base-score (/ points (reduce + weights))]
                   (map (fn [s w] [s (int (* w base-score))])
                        solutions
                        weights))
                 (let [n-full-target-domain (count full-target-domain)
                       avg-score (int (/ 100 n-full-target-domain))]
                   (map (fn [solution] [solution avg-score])
                        full-target-domain)))]
    (into (sorted-map) scores)))


(defn taken-mentors-set
  [all-mentee-preferences]
  (into #{}
        (->> all-mentee-preferences
             (map :mentor-handle)
             (filter (complement nil?))
             (map st/lower-case))))

(defn select-mentees
  [all n]
  (->> all
       (filter #(seq (:mentor-preferences %))) ;; has preferences
       (filter #(nil? (:mentor-handle %)))     ;; is unassigned
       (take n))) ;; at most n

(defn select-preferences
  [ms mentee-preferences]
  (let [menteesT (zipmap (vals ms) (keys ms))]
    (filter (fn [x] (get menteesT (:mentee x)))
            mentee-preferences)))

(defn filter-mentee-preferences
  [available-set mentee-preferences]
  (->> mentee-preferences
       (map
        (fn [pref]
          (update pref :mentor-preferences
                  (comp
                   #(filter available-set %)
                   #(map st/lower-case %)))))))


(defn remove-taken-preferences
  [taken mentee-preferences]
  (->> mentee-preferences
       (map
        (fn [pref]
          (update pref :mentor-preferences
                  (comp
                   #(remove taken %)
                   #(map st/lower-case %)))))))

(defn preferences-to-solve-for-sheet-only
  [all-mentee-preferences]
  (let [all-mentors-set (into #{}
                              (mapcat :mentor-preferences all-mentee-preferences))
        taken-mentors-set (taken-mentors-set all-mentee-preferences)
        available-mentors (set/difference all-mentors-set taken-mentors-set)
        prefs (remove-taken-preferences taken-mentors-set all-mentee-preferences)
        selected-mentee-prefs (select-mentees prefs (count available-mentors))]
    {:mentee-preferences (zipmap (map :mentee selected-mentee-prefs)
                                 (map :mentor-preferences selected-mentee-prefs))
     :available-mentors available-mentors}))

(defn preferences-to-solve-for
  [all-mentee-preferences available-mentors]
  (let [all-mentors-set (into #{}
                              (map (comp st/lower-case :handle) available-mentors))
        taken-mentors-set (taken-mentors-set all-mentee-preferences)
        available-mentors (set/difference all-mentors-set taken-mentors-set)
        prefs (filter-mentee-preferences available-mentors all-mentee-preferences)
        selected-mentee-prefs (select-mentees prefs (count available-mentors))]
    {:mentee-preferences (zipmap (map :mentee selected-mentee-prefs)
                                 (map :mentor-preferences selected-mentee-prefs))
     :available-mentors available-mentors}))
