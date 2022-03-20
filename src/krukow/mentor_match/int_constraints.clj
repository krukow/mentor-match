(ns krukow.mentor-match.int-constraints
  (:require [krukow.mentor-match.map-utils :as u])
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
  "Given a model, a map of constraints (int -> list of int),
  and a scoring function (score each list of constraints).
  Create the following vars in the model:
  {:solution-vars list of vars of solutions to constraints
   :constraints-vars list of set vars that can only take on values from constraints
   :score-vars score-vars list of scoring variables to score each solution
   :optimized-var sum-var a var that is the sum of the scoring vars}"
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
        constraints-vars (doall
                          (map
                           (fn [[source targets]]
                             (if-let [targets (seq targets)]
                               (.setVar model
                                        (str "constraint-" source)
                                        (int-array targets))
                               (.setVar model
                                        (str "constraint-" source)
                                        (int-array target-domain))))
                           int-constraints))
        score-vars (doall
                    (map
                     (fn [[source targets] source-var]
                       (let [target-scores (scoring-fn targets target-domain)
                             tuples (map->tuples target-scores)
                             score-var (.intVar model (str "score-" source) 0 1000)]
                         (-> (.table model source-var score-var tuples)
                             .post)
                         score-var))
                     int-constraints
                     solution-vars))

        sum-var (-> (first score-vars)
                    (.add (into-array IntVar (rest score-vars)))
                    .intVar)]
    {:solution-vars solution-vars
     :constraints-vars constraints-vars ;; avoid laziness here
     :score-vars score-vars
     :objective-var sum-var}))

(defn constrain-model-vars!
  "Given a model and the output of create-model-vars! function,
  sets the following constraints on the model:
  - all solutions must take on distinct values
  - for each solution var, it must take on a value in the
  corresponding constraint set var"
  [model {:keys [solution-vars constraints-vars]}]
  (.. model
      (allDifferent solution-vars)
      (post))
  (loop [[sv & svs] solution-vars
         [cv & cvs] constraints-vars]
    (.. model
        (member sv cv)
        post)
    (when (seq svs)
      (recur svs cvs))))

(defn solution-seq
  [solver vars]
  (lazy-seq
   (when (.solve solver)
     (cons {:score (.getValue (:objective-var vars))
            :solution (doall
                       (map #(.getValue %) (:solution-vars vars)))}
           (solution-seq solver vars)))))

(defn solve
  [constraints scoring-fn]
  (let [model (Model.)
        vars (create-model-vars! model constraints scoring-fn)]
    (constrain-model-vars! model vars)
    (.setObjective model Model/MAXIMIZE (:objective-var vars))
    (solution-seq (.getSolver model) vars)))

(defn best-solution
  [solutions]
  (let [select-solution (fn [sols]
                          (last (sort-by :score sols)))
        best-candidates (map select-solution solutions)]
    (->> best-candidates
         (sort-by :score)
         last)))
