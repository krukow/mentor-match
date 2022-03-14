(ns krukow.mentor-match.config
  (:require [clojure.string :as string]
            [clojure.java.io :as io])
  (:import (com.google.api.client.auth.oauth2 Credential)
           (com.google.api.client.extensions.java6.auth.oauth2
              AuthorizationCodeInstalledApp)
           (com.google.api.client.extensions.jetty.auth.oauth2
            LocalServerReceiver
            LocalServerReceiver$Builder)
           (com.google.api.services.sheets.v4 SheetsScopes)
           (com.google.api.services.drive Drive DriveScopes)
           (com.google.api.client.googleapis.auth.oauth2
            GoogleAuthorizationCodeFlow
            GoogleAuthorizationCodeFlow$Builder)
           (com.google.api.client.googleapis.auth.oauth2 GoogleClientSecrets)
           (com.google.api.client.http.javanet NetHttpTransport)
           (com.google.api.client.json.gson GsonFactory)
           (com.google.api.client.util.store FileDataStoreFactory)))

(defn environment-config
  ([name]
   (environment-config name nil))
  ([name default-value]
   (let [env (System/getenv name)]
     (if (string/blank? env)
       default-value
       env))))

(def credentials-json-path-env "GOOGLE_CREDENTIALS_JSON")
(def ^:dynamic *credentials-json-path-override* nil)


(defn google-credentials
  [transport token-dir-path]
  (with-open [rdr (io/reader
                   (if *credentials-json-path-override*
                     *credentials-json-path-override*
                     (environment-config credentials-json-path-env "credentials.json")))]
    (let [factory (. GsonFactory getDefaultInstance)
          secrets
          (GoogleClientSecrets/load factory rdr)
          flow (.. (new GoogleAuthorizationCodeFlow$Builder
                        transport factory secrets [SheetsScopes/SPREADSHEETS_READONLY
                                                   DriveScopes/DRIVE_FILE])
                   (setDataStoreFactory
                    (new FileDataStoreFactory (io/file token-dir-path)))
                   (setAccessType "offline")
                   build)
          receiver (.. (new LocalServerReceiver$Builder)
                       (setPort 8888)
                       (build))
          ]
      (.
       (new AuthorizationCodeInstalledApp flow, receiver)
       (authorize "user")))))
