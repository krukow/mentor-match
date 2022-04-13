(ns krukow.mentor-match.github-projects
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [cheshire.core :as json]
            [krukow.mentor-match.config :as cfg]
            [krukow.mentor-match.map-utils :as u])
  (:import java.util.TimeZone))

(def ^:private mentor-organization "github")
(def ^:private mentor-project-number 5127)

(defn- project-node-id-graphql-query
  [organization project-number]
  {:query
   (str "{organization(login: \""

        organization

        "\") {projectNext(number: " project-number "){id}}}")})

(defn- project-fields-graphql-query
  ([project-id]
   (project-fields-graphql-query project-id 20))
  ([project-id field-limit]
   {:query
    (str
     "query{node(id: \"" project-id "\") {"
      "...on ProjectNext {fields(first: " field-limit ") "
       "{nodes {id name settings}}"
      "}"
     "}}"
     )}))

(defn- project-items-graphql-query
  ([project-id]
   (project-items-graphql-query project-id 100 20))
  ([project-id item-limit field-limit]
   {:query
    (str
     "query{node(id: \"" project-id "\") {"
       "...on ProjectNext {items(first: " item-limit ") {"
         "nodes{title id fieldValues(first:" field-limit ") "
           "{nodes{value projectField{name}}}"
           "content{"
             "...on Issue {assignees(first: " field-limit ") {nodes{login}}}"
             "...on PullRequest {assignees(first: " field-limit ") {nodes{login}}}"
             "}"
         "}"
       "}}"
     "}}"
     )}))




(defn- project-node-id
  ([url org num]
   (-> url
       (client/post
        (u/deep-merge (cfg/http-options)
                      {:body (json/generate-string
                              (project-node-id-graphql-query org num))}))
       (get-in [:body :data :organization :projectNext :id]))))

(defn- parse-fields
  [fields]
  (into
   {}
   (map
    (fn [field]
      [(:name field)
       (update field :settings json/parse-string true)])
    fields)))

(defn- project-fields
  [url project-id]
  (-> url
      (client/post
       (u/deep-merge (cfg/http-options)
                     {:body (json/generate-string
                             (project-fields-graphql-query project-id))}))
      (get-in [:body :data :node :fields :nodes])
      parse-fields))

(defn- get-field-value
  [item field-name]
  (if-let [fields (get-in item [:fieldValues :nodes])]
    (->> fields
         (filter #(= field-name (get-in % [:projectField :name])))
         first
         :value)))

(defn- project-items
  ([url project-id]
   (-> url
       (client/post
        (u/deep-merge (cfg/http-options)
                      {:body (json/generate-string
                              (project-items-graphql-query project-id))}))
       (get-in [:body :data :node :items :nodes]))))

(defn- select-fields
  [item field-names fields]
  (into
   {}
   (map
    (fn [field-name]
      (let [field (get fields field-name)
            field-value (get-field-value item field-name)
            field-options (get-in field [:settings :options])]
        (if field-options
          [field-name (->> field-options
                           (filter #(= field-value (:id %)))
                           first
                           :name)]
          [field-name field-value])))
    field-names)))

(def ^:private available-timezone-ids (TimeZone/getAvailableIDs))

(def ^:private timezone-map
  {"eastern time - us" "US/Eastern"
   "eastern time -us" "US/Eastern"
   "pacific time - us" "US/Pacific"
   "pacific time -us" "US/Pacific"
   "pacific" "US/Pacific"
   "eastern standard - est" "US/Eastern"
   "eastern standard -est" "US/Eastern"
   "central time - us" "US/Central"
   "central time -us" "US/Central"


   })

(defn- match-timezone-id
  [id timezoneid]
  (= (string/lower-case id)
     (string/lower-case timezoneid)))

(defn- match-timezone
  [id]
  (if (nil? id)
    {:timezone-string nil
     :timezone nil}
    (let [direct-match
          (first
           (filter #(match-timezone-id id %) available-timezone-ids))]
      (if direct-match
        {:timezone-string id
         :timezone (TimeZone/getTimeZone direct-match)}
        (if-let [mapped-match (get timezone-map (string/lower-case id))]
          {:timezone-string id
           :timezone (TimeZone/getTimeZone mapped-match)}
          {:timezone-string id
           :timezone nil})))))


(defn- find-available-mentors-in-board
  [{:keys [organization project-number]}]
  (let [project-id (project-node-id
                    cfg/github-graphql-url
                    organization
                    project-number)
        fields (project-fields cfg/github-graphql-url project-id)
        mentor-field (get fields "Mentor")
        is-mentor-value (->> (get-in mentor-field [:settings :options])
                             (filter #(= "True" (get % :name)))
                             first
                             :id)
        availability-field (get fields "Availability")
        is-available-value (->> (get-in availability-field [:settings :options])
                                (filter #(= "Available" (get % :name)))
                                first
                                :id)

        parse-mentor-node (fn [{:keys [title] :as item}]
                            (let [[_ name handle]
                                  (re-find #"(.*)\((@\w+)\)" title)]
                              (if handle
                                (let [parsed (merge
                                              (select-fields item
                                                             ["CET" "Org" "Team"
                                                              "staff"
                                                              "Role"
                                                              "Headline"]
                                                             fields)
                                              {:name (string/trim name)
                                               :handle (string/trim handle)})]
                                  (merge parsed
                                         (match-timezone (get parsed "CET")))))))
        ]
    (->> (project-items cfg/github-graphql-url project-id)
         (filter #(and
                   (= is-mentor-value (get-field-value % "Mentor"))
                   (= is-available-value (get-field-value % "Availability"))))
         (map parse-mentor-node)
         (filter (complement nil?)))))


(defn available-mentors
  []
  (->> (find-available-mentors-in-board
        {:organization mentor-organization
         :project-number mentor-project-number})))

(defn mentor-board-url
  []
  (str "https://github.com/orgs/" mentor-organization
       "/projects/"
       mentor-project-number)

  )
