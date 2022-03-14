(ns krukow.mentor-match
  (:require [clojure.math.combinatorics :as combo]
            [krukow.mentor-match.sheets-parser :as parser]
            [clojure.set :as set]
            [clojure.pprint :as p])
  (:import (org.chocosolver.solver Model)
           (org.chocosolver.solver.variables IntVar
                                             SetVar)
           (org.chocosolver.solver.constraints.extension Tuples)))

;; Utils

(defn indexed-map
  [s]
  (->> s
       (map-indexed vector)
       (into (sorted-map))))

(defn map-values
  "Like map takes and returns a map.
  Maps across the values of a map-data structure, preserving keys.
  Example: (map-values inc {:a 1 :b 2 :c 3})
  => {:a 2, :b 3, :c 4}"
  [f m]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} m))

;;; Logic

(defn select-mentees
  [all n]
  (->> all
       (filter #(seq (:mentor-preferences %))) ;; has preferences
       (filter #(nil? (:mentor-handle %))) ;; is unassigned
       (take n))) ;; at most n

(defn select-preferences
  [ms mentee-preferences]
  (let [menteesT (zipmap (vals ms) (keys ms))]
    (filter (fn [x] (get menteesT (:mentee x)))
            mentee-preferences)))

(defn remove-taken-preferences
  [taken mentee-preferences]
  (map
   (fn [x]
     (let [prefs (:mentor-preferences x)]
       (assoc x :mentor-preferences (remove taken prefs))))
   mentee-preferences))


(defn configure-tuples!
  [model scores pref-var]
  (let [tuples (new Tuples true)
        score-var (.intVar model (str "score-" (.getName pref-var)) 0 1000)]
    (doseq [[x score] scores]
      (.add tuples (int-array [x score])))
    (.post (.table model pref-var score-var tuples))
    score-var))

(defn create-scores [ps any-mentor]
  (let [points 100
        default-score (int (/ 100 (count any-mentor)))]
    (if (seq ps)
      (let [weights (range 1 (inc (count ps))) ;; 1 + 2 + .. + (count ps)
            sum (reduce + weights)
            factor (/ 100 sum)]
        (map
         (fn [w x] [x (int (* w factor))])
         weights
         (reverse ps)))
      (map (fn [x] [x default-score]) any-mentor))))

(defn create-preferences-vars!
  [^Model model mentees prefs var-map mentorsT]
  (let [menteesT (zipmap (vals mentees) (keys mentees))]
    (->> prefs
         (map (fn [{:keys [mentee mentor-preferences]}]
                (let [mentee-index (get menteesT mentee)
                      var-name (str "prefs-" mentee-index)
                      ps (map mentorsT mentor-preferences)
                      any-mentor (int-array (sort (vals mentorsT)))
                      aprefs (if (seq ps)
                               (int-array ps)
                               any-mentor)
                      scores (create-scores ps any-mentor)
                      pref-var (.setVar model var-name aprefs)
                      score-var (configure-tuples! model scores (get var-map mentee-index))]
                  [mentee-index [pref-var score-var]])))
         (into {}))))

(defn index->names
  [solution mentees mentors]
  (map
   (juxt (comp mentees first)
         (comp mentors second))
   solution))

(defn solution-seq
  [solver var-map score-sum-var mentees mentors]
  (lazy-seq
   (when (.solve solver)
     (cons {:score (.getValue score-sum-var)
            :solution (doall
                       (index->names
                        (map (fn [[i m]] [i (.getValue m)]) var-map)
                        mentees
                        mentors))}
           (solution-seq solver var-map score-sum-var mentees mentors)))))

(defn solutions-for [n mentees mentors mentee-preferences]
  (let [mentorsT (zipmap (vals mentors) (keys mentors)) ;; name -> id
        mcount (count mentors)
        last-mentor (dec mcount) ;; index of last mentor
        mentee-combos (combo/combinations mentees n) ;; seq of (pick n mentees)
        cnt (count mentee-combos)]
    (println "Trying " cnt "combinations...")
    (->> mentee-combos ;; for each choice of n mentees
         (map-indexed
          (fn [i ms]
            (when (zero? (mod i 1000))
              (printf "%d/%d" i cnt)
              (println))
            (let [model (Model. (str "mentor-match" n "-" i))
                  mentees-map (into (sorted-map) ms)
                  first-mentee (first (keys mentees-map))
                  last-mentee  (last (keys mentees-map))
                  match-vars (.intVarArray model
                                           "matches"
                                           (count mentees-map) ;; one for each mentee
                                           0 ;; range: all the mentors
                                           last-mentor)
                  var-map (->> mentees-map ;; mentee-index -> match-var
                               (map-indexed
                                (fn [i [mi _]] [mi (aget match-vars i)]))
                               (into {}))
                  mentees-submap (select-keys mentees (keys mentees-map))
                  prefs (select-preferences mentees-submap mentee-preferences)
                  prefs-scores-vars (create-preferences-vars! model
                                                              mentees-map
                                                              prefs
                                                              var-map
                                                              mentorsT)
                  prefs-vars (into {} (map #(vector (first %) (first (second %)))
                                          prefs-scores-vars))
                  score-vars (into {} (map #(vector (first %) (second (second %)))
                                          prefs-scores-vars))
                  scores (map second score-vars)
                  sum-var (.intVar (.add (first scores)
                                         (into-array IntVar (rest scores))))
                  constrain-model!
                  (fn []
                    ;; each mentor must only have one mentee
                    (.. model
                        (allDifferent match-vars)
                        (post))
                    (doseq [[i m] var-map]
                      (let [mprefs (get prefs-vars i)]
                        (.. model
                            (member m mprefs)
                            post))))]

              (constrain-model!)

              (.setObjective model Model/MAXIMIZE sum-var)

              (let [solver (.getSolver model)]
                (solution-seq solver var-map sum-var mentees mentors))))))))

(defn validate-solution
  [s n]
  (let [unique-mentors (into #{} (map second (:solution s)))]
    (= (count unique-mentors) n)))

(defn select-solution [sols]
  (last (sort-by :score sols)))

(defn best-solution
  [solutions]
  (let [best-candidates
        (map #(select-solution %) solutions)]
    (->> (doall best-candidates)
         (sort-by :score)
         last)))


(defn match
  [{:keys [config sheet-url] :as conf}]
  (let [all-mentee-preferences (parser/parse-sheet sheet-url config)
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
        mentee-preferences (-> (remove-taken-preferences taken-mentors-set
                                                         all-mentee-preferences)
                               (select-mentees  mcount))

        mentees (->> mentee-preferences
                     (map :mentee)
                     (into #{}) ;; unique
                     indexed-map)]
    (println "From a total of" (count all-mentees) "mentees.")
    (println "With a total of" (count mentors-set) "mentors.")
    (println "Pre-matched mentors:" (count taken-mentors-set))
    (println "Available mentors:" (count mentors))
    (println "Picking" (count mentees) "mentees with preferences...")
    (println "\nRunning constraint solver...\n")
    (loop [n (count mentees)]
      (println "Trying to match:" n "mentees")
      (let [solutions (filter seq (solutions-for n
                                                 mentees
                                                 mentors
                                                 mentee-preferences))
            bs (best-solution solutions)]
        (if (seq bs)
          (p/pprint bs)
          (if (> n 2)
            (do
              (println "No solution exists that matches all preferences...")
              (recur (dec n)))
            (println "Unable to find any solution.")))))))



(comment
  (def solutions
    (filter seq (solutions-for 35)))
  )
