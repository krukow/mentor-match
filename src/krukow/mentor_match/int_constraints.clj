(ns krukow.mentor-match.int-constraints
  (:require [clojure.math.combinatorics :as combo]
            [krukow.mentor-match.map-utils :as u])
  (:import (org.chocosolver.solver Model)
           (org.chocosolver.solver.variables IntVar
                                             SetVar)
           (org.chocosolver.solver.constraints.extension Tuples)))

(defn map->int-domains
  "Transform a map of source => list of target constraints
  into an int -> list of int constraints.
  returns translation map of
  {:int->target int -> target (domain) mapping 1:1
   :target->int target -> int mapping 1:1
   :int->source int -> source (domain) mapping 1:1
   :source->int source -> int mapping 1:1
   :constraints int -> list of int"
  [constraints]
  (let [source-domain (u/into-indexed-sorted-map (keys constraints))
        source-domainT (zipmap (vals source-domain) (keys source-domain))
        target-set (into [] (into #{} (mapcat second constraints)))
        target-domain (u/into-indexed-sorted-map target-set)
        target-domainT (zipmap (vals target-domain) (keys target-domain))
        constraints-mapped
        (map
         (fn [[source targets]]
           [(source-domainT source) (map target-domainT targets)])
         constraints)]
    {:int->target target-domain
     :target->int target-domainT
     :int->source source-domain
     :source->int source-domainT
     :constraints  (into (sorted-map) constraints-mapped)}))

(defn map->tuples
  "Transform a map of [k v] pairs into a Tuple object
  which has the same k v tuples."
  [m]
  (let [tuples (new Tuples true)]
    (doseq [[target score] m]
      (.add tuples (int-array [target score])))
    tuples))

(defn create-model-vars!
  "Given a model, a sorted map of constraints (int -> list of int),
  and a scoring function (score each list of constraints).
  Create the following vars for each key in the int-constraints.
  {:solution solution int var
   :constraint constraint set vars
   :score score-var scoring int var
   :score-tuples a tuples object that maps solutions to scores}
  returns map of {:objective var(sum of all scores),
                  :vars sorted map of int to vars above}"
  [model int-constraints scoring-fn]
  (let [source-domain (keys int-constraints)
        target-domain (into (sorted-set) (mapcat second int-constraints))
        first-target (first target-domain)
        last-target (last target-domain)

        ;; a var for each domain object with range over target-domain
        solution-vars (.intVarArray model
                                    "source-solution"
                                    (count source-domain)
                                    first-target
                                    last-target)
        constraints-vars (map
                          (fn [[source targets]]
                            (if-let [targets (seq targets)]
                              (.setVar model
                                       (str "constraint-" source)
                                       (int-array targets))
                              (.setVar model
                                       (str "constraint-" source)
                                       (int-array target-domain))))
                          int-constraints)

        score-vars (.intVarArray model
                                 "solution-scores"
                                 (count source-domain)
                                 0
                                 1000)

        score-tuples (map
                      (fn [[_ targets]]
                        (map->tuples (scoring-fn targets target-domain)))
                      int-constraints)

        sum-var (-> (first score-vars)
                    (.add (into-array IntVar (rest score-vars)))
                    .intVar)]

    {:vars (into
            (sorted-map)
            (map
             (fn [source sol-var cons-var score-var score-tuples]
               [source {:solution sol-var
                        :constraint cons-var
                        :score score-var
                        :score-tuples score-tuples}])
             source-domain
             solution-vars
             constraints-vars
             score-vars
             score-tuples))
     :objective sum-var}))

(defn constrain-model-vars!
  "Given a model and the output of create-model-vars! function,
  sets the following constraints on the model:
  - all solutions must take on distinct values
  - map solution vars to corresponding scoring-vars
  - for each solution var, it must take on a value in the
  corresponding constraint set var"
  [model model-vars]

  (let [solution-vars (doall (map :solution (vals (:vars model-vars))))]
    (.. model
        (allDifferent (into-array solution-vars))
        (post)))

  (doseq [[_ {:keys [solution score score-tuples]}] (:vars model-vars)]
    (-> (.table model solution score score-tuples)
        .post))

  (doseq [[_ {:keys [solution constraint]}] (:vars model-vars)]
    (-> model
        (.member solution constraint)
        .post)))

(defn solution-seq
  "Lazy seq of solutions obtained by repeatedly
   calling .solve on solver, and evaluating the solutions and score."
  [solver {:keys [objective vars] :as model-vars}]
  (lazy-seq
   (when (.solve solver)
     (cons {:score (.getValue objective)
            :solution (doall
                       (map #(.getValue (:solution %)) (vals vars)))}
           (solution-seq solver model-vars)))))

(defn solve
  "Given a sorted map of (int) constraints and a scoring function,
  returns an seq of solutions to the constraints."
  [constraints scoring-fn]
  (let [model (Model.)
        vars (create-model-vars! model constraints scoring-fn)]
    (constrain-model-vars! model vars)
    ;;(.setObjective model Model/MAXIMIZE (:objective vars))
    (solution-seq (.getSolver model) vars)))


(defn solutions-for
  "Given an int n, a finite domain mapping of constraints
  (obtained by map->int-domains), and a scoring function,
  for each way of choosing n sources, provides a lazy seq of
  solutions to satisfying those n sources constraints."
  [n
   {int->mentee :int->source
    int->mentor :int->target
    constraints :constraints}
   scoring-fn]
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
            (let [ms-constraints (into (sorted-map) (select-keys constraints ms))
                  int-solutions (solve ms-constraints scoring-fn)
                  int-solution->domain #(update
                                         %
                                         :solution
                                         (fn [solution]
                                           (map (fn [i-mentee i-mentor]
                                                  [(get int->mentee i-mentee)
                                                   (get int->mentor i-mentor)])
                                                (keys ms-constraints)
                                                solution)))]
              (->> int-solutions
                   (map int-solution->domain))))))))


(defn sort-by-best-solution
  [solutions]
  (->> solutions
       flatten
       (into #{})
       (sort-by :score (comparator >))
       ))

(defn best-solution
  "Given a seq of (seq of solutions), pick the best of all of them
  (highest :score solution)."
  [solutions]
  (let [select-solution (fn [sols]
                          (last (sort-by :score sols)))
        best-candidates (map select-solution solutions)]
    (->> best-candidates
         (sort-by :score)
         last)))



(comment
  (def solutions
    (filter seq (solutions-for 35)))
  (defn validate-solution
    [s n]
    (let [unique-mentors (into #{} (map second (:solution s)))]
      (= (count unique-mentors) n)))

  )
