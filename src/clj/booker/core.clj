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
    [booker.data :refer [wrap-atom-store store]]
    )
  (:gen-class)
  )

;; Auth configuration

(def config-auth {:roles #{::user}})

(defn credential-fn [token]
  (if token
    {:identity token :roles #{::user}}
    nil
    ))

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

;; Routes

(defroutes main
  (GET "/" req (templates/index-tpl req))
  (GET "/search" req (templates/search-tpl req))
  (GET "/profile/:id" req (templates/profile-tpl req))
  (GET "/edit-profile-tpl" req (templates/edit-profile-tpl req))

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


(def app
  (-> main
      (friend/authenticate
        {:allow-anon? true
         :workflows [(oauth2/workflow
                       {:client-config client-config
                        :uri-config uri-config
                        :access-token-parsefn get-access-token-from-params
                        :config-auth config-auth
                        :credential-fn credential-fn
                        })]})
      (wrap-defaults site-defaults)
      (wrap-atom-store store)
      (wrap-stacktrace)
      (wrap-reload)
      ))


(defn -main []
  (run-server app {:port 8080}))
