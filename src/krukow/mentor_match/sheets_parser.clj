(ns krukow.mentor-match.sheets-parser
  (:require [krukow.mentor-match.google-api :as api]
            [clojure.string :as s]))


(defn- spreadsheet-url->id
  [spreadsheetUrl]
  (let [url-path (.getPath (java.net.URL. spreadsheetUrl))
        id-action-segment (last (clojure.string/split url-path  #"/spreadsheets/d/"))]
    ;; "1QkBeMNGfsHHga85c-UsLAwnpmz7QyhvFK_n31CzDe7c/edit"
    (first (s/split id-action-segment #"/"))))


(defn- parse-preferences
  [^String prefs-raw]
  (let [lines (s/split-lines prefs-raw)
        prefs (map #(second (re-matches #"(?:\s*\d+\.\s*)?(@\w+).*" %))
                   lines)]
    (filter (complement nil?) prefs)))

(defn- parse-mentor-handle
  [^String mentor-handle]
  ;; karl@foo.com
  (when (and mentor-handle (re-seq #"@" mentor-handle))
    (let [h (s/split mentor-handle #"@")]
      (str "@" (first h)))))

(def sheet-columns
  {"Timestamp" :timestamp
   "Email Address" :mentee
   "Admin Facilitating" :admin
   "Status" :status
   "3 Mentor Choices" :mentor-preferences
   "Fit Check Meeting Date" :fit-check-date
   "Mentor" :mentor
   "Start Date" :start-date
   "Notes" :notes
   "Are you comfortable committing to spending 45-90 minutes a month on this program?" :comfort
   "Where are you located?" :location
   "How comfortable are you receiving direct, honest feedback?" :feedback   "What areas of growth are you looking to focus on during the next 6 months?" :areas-of-growth
   "What are you looking for in a mentor?" :looking-for
   "Is there anything you're explicitly not looking for in a mentor?" :not-looking-for
   "Is there anyone you have in mind for a mentor?" :mentor-in-mind
   "Is there anyone you wouldn't feel comfortable having as a mentor?" :non-mentors
   "Tell us a little bit about yourself outside of programming." :yourself
   "Anything else you would like us to know?" :anything-else
   "Mentor handle" :mentor-handle

   })

(def row-key->column (zipmap (vals sheet-columns) (keys sheet-columns)))

(defn parse-sheet
  [url google-config]
  (let [data-raw (api/eval-range google-config
                                 (spreadsheet-url->id url)
                                 "A:T")

        row-key->index (map
                        #(get sheet-columns (s/trim %) (s/trim %))
                        (first data-raw))]
    (->> (rest data-raw)
         (map
          (fn [row]
            (let [raw-map (zipmap row-key->index row)
                  prefs (get raw-map :mentor-preferences)
                  mentor-handle (get raw-map :mentor-handle)]
              (assoc raw-map
                     :mentor-preferences (parse-preferences prefs)
                     :mentor-handle (parse-mentor-handle mentor-handle))))))))

(comment
  (def spreadsheet-url "https://docs.google.com/spreadsheets/d/13eowmhtoWvTsrT1v9mXC6W3TH-fLme0S0jCOVaPgpKc/edit#gid=1314170866")
  (parse-sheet spreadsheet-url {:token-directory "./tokens"})
)
