(ns booker.core
  (:require
    [taoensso.timbre :as logger]

    ; Routes
    [booker.routes :refer [main]]

    ; Auth
    [cemerick.friend :as friend]
    [booker.auth :refer [auth-config]]

    ; Middlewares
    [ring.middleware.reload :refer [wrap-reload]]
    [ring.middleware.stacktrace :refer [wrap-stacktrace]]
    [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
    [taoensso.carmine.ring :refer [carmine-store]]
    [booker.data :as data :refer [wrap-atom-store store]]

    ;[org.httpkit.server :refer [run-server]]
    [ring.adapter.jetty :refer [run-jetty]]
    )
  (:gen-class)
  )

(logger/set-config! [:appenders :spit :enabled?] true)
(logger/set-config! [:shared-appender-config :spit-filename] "booker.log")

;; Auth configuration


(defn debug [handler]
  (fn [req]
    (logger/debug "Incoming request: " req)
    (try
      (let [resp (handler req)]
        (logger/debug "Outgoing response: " resp)
        resp)
      (catch Exception e
        (prn "CAUGHT EX")
        (prn (.getMessage e))
        (prn e)
        (throw e)))))



(defn wrap-content-type
  "Defaults default to application/octet-stream. Who needs that?"
  [handler]
  (fn [req]
    (update-in (handler req) [:headers "Content-Type"] (fnil identity "text/html"))))

(defn wrap-current-user [handler]
  (fn [req]
    (handler
      (assoc-in req
                [:current-user] (-> req :session :friend/identity :current)))))

(defn debug-session [handler]
  (fn [req]
    (logger/debug "")
    (logger/debug "Incoming Session: " (:session req))
    (handler req)))


(def base-handler
  (-> main
      (wrap-current-user)
      (friend/authenticate auth-config)
      (wrap-content-type)
      (wrap-defaults (assoc-in site-defaults
                               [:session :store] (carmine-store nil)))
      (wrap-atom-store store)))

(def dev-handler
  (-> base-handler
      (debug)
      (wrap-stacktrace)
      (wrap-reload)))


(defn -main []
  (logger/info "Starting server on port 8080...")
  (run-jetty base-handler {:port 8080}))

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
