(ns booker.core
  (:require
    [compojure.core :refer [defroutes GET POST ANY context]]
    [compojure.route :as route]
    [clojure.java.io :as io]
    [ring.middleware.reload :refer [wrap-reload]]
    [ring.middleware.stacktrace :refer [wrap-stacktrace]]

    ; Auth
    [cemerick.friend :as friend]
    [friend-oauth2.workflow :as oauth2]
    [friend-oauth2.util :refer [format-config-uri get-access-token-from-params]]

    [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
    [org.httpkit.server :refer [run-server]]

    ;internal
    [booker.templates :as templates]
    [booker.data :as data :refer [wrap-atom-store store]]

    [clj-http.client :as client]
    [clojure.data.json :as json]
    [booker.helpers :refer [attempt-all, try*, ->Failure, has-failed?]]
    [taoensso.carmine.ring :refer [carmine-store]]
    )
  (:gen-class)
  )

;; Auth configuration

(def config-auth {:roles #{::user}})

(defn populate-user-from-facebook [tok]
  (let [resp (client/get
               "https://graph.facebook.com/v2.1/me"
               {:query-params {"access_token" tok
                               "fields" "id,name,first_name,locale,about,email,cover,picture"
                               }})
        body (json/read-str (:body resp))
        ]
    (assoc (data/make-user tok)
           :name (get body "name")
           :facebook-user-id (get body "id")
           :pic (-> body
                    (get "picture")
                    (get "data")
                    (get "url")))))

(defn associate-with-existing-user [store user]
  (if-let [existing-user (data/get-user-by-facebook-id store (:facebook-user-id user))]
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
     user (associate-with-existing-user store user)
     user (validate-friend-user user)
      _ (data/put-user! store user)
     ]
    user))

(defn credential-fn [{token :access-token}]
  (let [result (user-from-token token)]
    result))

(def client-config
  {:client-id "705827986136884"
   :client-secret (System/getenv "FACEBOOK_OAUTH_SECRET")
   :callback {:domain "http://booker.clojurecup.com" :path "/oauth2callbackx"}})

(def uri-config
  {:authentication-uri {:url "https://www.facebook.com/dialog/oauth"
                       :query {:client_id (:client-id client-config)
                               :redirect_uri (format-config-uri client-config)}}

   :access-token-uri {:url "https://graph.facebook.com/oauth/access_token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :redirect_uri (format-config-uri client-config)}}})

;; Routes

(defroutes main
  (GET "/" req (templates/index-tpl req))
  (GET "/search" req (templates/search-tpl req))
  (GET "/profile/:id" req (templates/profile-tpl req))

  (GET "/edit-profile" req
       (friend/authorize #{::user}
                         (templates/edit-profile req)))
  (POST "/update-profile" req
        (friend/authorize #{::user}
                          (templates/update-profile req)))

  (POST "/create-trip" req
        (friend/authorize #{::user}
                          (templates/add-trip req)))

  (POST "/delete-trip/:id" req
        (friend/authorize #{::user}
                          (templates/delete-trip req)))

  ; debug routes
  (GET "/status" request
       (let [count (:count (:session request) 0)
             session (assoc (:session request) :count (inc count))]
         {:body  (str "<p>We've hit ye olde session page " (:count session)
                   " times.</p><p>The current session: " session "</p>"
                   )
              :status 200
              :headers {"Content-Type" "text/html"}
              :session session
              }))

  (GET "/auth" req
       (friend/authorize #{::user}
          {:body "Authorized."
           :status 200
           :headers {"Content-Type" "text/html"}} ))

  (route/resources "/")
  (route/not-found "404 Not Found")
  )


(defn debug [handler]
  (fn [req]
  (try
    (handler req)
    (catch Exception e
      (prn "CAUGHT EX")
      (prn (.getMessage e))
      (prn e)
      (throw e)
      )
    ))
  )

(defn wrap-current-user [handler]
  (fn [req]
    (handler
      (assoc-in req
                [:current-user] (-> req :session :friend/identity :current)))))

(def app
  (-> main
      (wrap-current-user)
      (friend/authenticate
        {:allow-anon? true
         :workflows [(oauth2/workflow
                       {:client-config client-config
                        :uri-config uri-config
                        :access-token-parsefn get-access-token-from-params
                        :credential-fn credential-fn
                        :auth-error-fn (fn [error]
                                         (prn "AUTH ERROR: " error)
                                         (ring.util.response/response error))
                        })]})
      (debug)
      (wrap-defaults (assoc-in site-defaults
                               [:session :store] (carmine-store nil)))
      (wrap-atom-store store)
      (wrap-stacktrace)
      (wrap-reload)
      ))


(defn -main []
  (prn "Starting server on port 8080...")
  (run-server app {:port 8080})
  )

(comment
  (require '[clj-http.client :as client])
  (let [resp (app {:server-name "localhost"
        :server-port 8080
        :remote-addr "127.0.0.1"
        :uri "/auth"
        :scheme "http"
        :request-method :get
        :headers {}
        })
        session-id (-> resp :headers
                       (get "Set-Cookie") (first)
                       (clojure.string/split #";") (first)
                       )
        redirect-url (-> resp :headers (get "Location"))

        resp2 (app {:request-method :get :uri "/login" :headers {"Cookie" session-id}})
        facebook-url (-> resp :headers (get "Location"))

        resp3 (client/get facebook-url)
        ]
    resp3
    )
  
  (app {:server-name "localhost"
        :server-port 8080
        :uri "/oauth2callback"
        :query-params {"code" "AQDfxD6XtwWvc8K3al5T661neTqxI80JnFGNQAhnI4SP9Rs54o9gKwWzI3793HkrrdvtMIScy8nSI8tFHIQtvSb_goNJZqpRlWxIQ0PzVxImJioBZY07_YYF3js9RoaJdzywkaX6WVdXo1y-w_R7UzYdpnWNr9x-AvotPEpOvPfvo_bK34fiJziXfe-RCcbRBiZfqhg3gvMQb_oOE4wmFsg3ZTt1x5FieyHmiUBNm3-UwccBqrvsqC6IFg3IF5r-FKlGB7Wg09sQPLWfKNoZ01DiVyQfQhm8XMyKpjCt9ZMUqow8dLVfdel4KC6r7rZdg0I"
                       "state" "%2BXDm8aokc0CKb7OzmDldSrDotXHQXTe49joIn5DllAApUF8bTy1SRBaOWQ9G8iz57a5YFFmvO0lucur"
                       }})
  )
