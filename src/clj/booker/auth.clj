(ns booker.auth
  (:require
    [taoensso.timbre :as logger]
    [cemerick.friend :as friend]
    [friend-oauth2.workflow :as oauth2]
    [friend-oauth2.util :refer [format-config-uri get-access-token-from-params]]

    [booker.data :as data]
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [booker.helpers :refer [attempt-all, try*, ->Failure, has-failed?]]

    )
  (:gen-class)
  )

;; Facebook oauth

(defn- -populate-user-from-facebook [tok]
  (let [resp (client/get
               "https://graph.facebook.com/v2.1/me"
               {:query-params {"access_token" tok
                               "fields" "id,name,about,email,picture"
                               }})
        body (json/read-str (:body resp))
        ]
    (assoc (data/make-user tok)
           :name (get body "name")
           :email (get body "email")
           :description (get body "about")
           :facebook-user-id (get body "id")
           :pic (get-in body ["picture" "data" "url"]))))

(defn populate-user-from-facebook [tok]
  (try
    (-populate-user-from-facebook tok)
    (catch Exception e
      (logger/log e "Error populating user with facebook info")
      e)))

(defn associate-with-existing-user [store user]
  (if-let [existing-user (data/get-user-by-facebook-id data/store (:facebook-user-id user))]
    (assoc user :id (:id existing-user))
    user))

(defn validate-friend-user [user]
  (if (:id user)
    (assoc user :identity (:id user) :roles #{::user})
    (->Failure "User has no identity")))

(defn user-from-token [token]
  (attempt-all
    [tok (or token (->Failure "No token"))
     user ((try* populate-user-from-facebook) tok)
     user (associate-with-existing-user data/store user)
     user (validate-friend-user user)
      _ (data/put-user! data/store user)
     ]
    user
    (do
      (logger/warn "Failed to generate user from token!")
      nil)))

(defn credential-fn [{token :access-token}]
  (let [result (user-from-token token)]
    result))

(def client-config
  {:client-id "705827986136884"
   :client-secret (System/getenv "FACEBOOK_OAUTH_SECRET")
   :callback {:domain "http://booker.clojurecup.com" :path "/oauth2callback"}})

(def uri-config
  {:authentication-uri {:url "https://www.facebook.com/dialog/oauth"
                       :query {:client_id (:client-id client-config)
                               :redirect_uri (format-config-uri client-config)}}

   :access-token-uri {:url "https://graph.facebook.com/oauth/access_token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :redirect_uri (format-config-uri client-config)}}})


(def auth-config
  {:allow-anon? true
         :workflows [(oauth2/workflow
                       {:client-config client-config
                        :uri-config uri-config
                        :access-token-parsefn get-access-token-from-params
                        :credential-fn credential-fn
                        :auth-error-fn (fn [error]
                                         (logger/error error "Auth error"))
                        })]}
  )
