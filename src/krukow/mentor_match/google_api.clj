(ns krukow.mentor-match.google-api
  (:require [krukow.mentor-match.config :as cfg])
  (:import
   (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
   com.google.api.client.http.FileContent
   (com.google.api.client.json.gson GsonFactory)
   com.google.api.client.util.store.FileDataStoreFactory
   (com.google.api.services.drive Drive Drive$Builder)
   (com.google.api.services.drive.model File FileList)
   (com.google.api.services.sheets.v4 Sheets
                                      Sheets$Builder)
   (com.google.api.services.sheets.v4.model ValueRange)))

(defn generate-token
  [google-config]
    (let [transport (. GoogleNetHttpTransport newTrustedTransport)
          creds (cfg/google-credentials transport
                                        (str (:token-directory google-config)))
          factory (. GsonFactory getDefaultInstance)]
      (println "Token stored in: " (:token-directory google-config))))


(defn eval-range [google-config sheet range]
  (let [transport (. GoogleNetHttpTransport newTrustedTransport)
        creds (cfg/google-credentials transport
                                      (str (:token-directory google-config)))
        factory (. GsonFactory getDefaultInstance)
        service  (.. (new Sheets$Builder transport factory creds)
                     (setApplicationName "krukow/mentor-match")
                     build)
        response (.. service (spreadsheets)
                     (values)
                     (get sheet range)
                     execute)]
    (seq (.getValues response))))
