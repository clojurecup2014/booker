(ns booker.routes
  (:require
    [taoensso.timbre :as logger]
    [compojure.core :refer [defroutes GET POST ANY context]]
    [compojure.route :as route]
    [cemerick.friend :as friend]
    [booker.templates :as templates]))


(defroutes main
  (GET "/" req (templates/index-tpl req))
  (GET "/search" req (templates/search-tpl req))
  (GET "/profile/:id" req (templates/profile-tpl req))

  (GET "/edit-profile" req
       (friend/authorize #{:booker.auth/user}
                         (templates/edit-profile req)))
  (POST "/update-profile" req
        (friend/authorize #{:booker.auth/user}
                          (templates/update-profile req)))

  (POST "/create-trip" req
        (friend/authorize #{:booker.auth/user}
                          (templates/add-trip req)))

  (POST "/delete-trip/:id" req
        (friend/authorize #{:booker.auth/user}
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
       (friend/authorize #{:booker.auth/user}
          {:body "Authorized."
           :status 200
           :headers {"Content-Type" "text/html"}} ))

  (route/resources "/")
  (route/not-found "404 Not Found"))
