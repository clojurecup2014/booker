(ns booker.templates
  (:require [taoensso.timbre :as logger]
            [net.cgrand.enlive-html :as enlive]
            net.cgrand.reload
            [booker.data :as data]
            [booker.helpers :refer [as-date format-date attempt-all try* ->Failure]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :as response]
            )
  )

(net.cgrand.reload/auto-reload *ns*)

; Utils

(defn current-user [req]
  (let [user-id (-> req :session :cemerick.friend/identity :current)]
    (-> req :session :cemerick.friend/identity :authentications (get user-id))))

(defn is-authenticated? [req]
  (not (nil? (current-user req))))

(defn is-current-user? [req user-id]
  (and (is-authenticated? req) (= (:id (current-user req)) user-id)))

(defn photo-url [user width height]
  (if (:facebook-user-id user)
    (str "https://graph.facebook.com/v2.1/" (:facebook-user-id user) "/picture?width=" width "&height=" height)
    "/img/pic.jpg"
  ))

(defn insert-csrf-field []
  (enlive/append (enlive/html-resource (java.io.StringReader. (anti-forgery-field)))))


;; Home

(enlive/defsnippet popular-dest-snippet "public/index.html" [:ul.popular-destinations [:li (enlive/nth-of-type 1)]]
  [[destination cnt]]
  [:.num-visitors] (enlive/content (str cnt))
  [:a.dest-link] (enlive/do->
                   (enlive/set-attr :href (str "/search"
                                               "?destination=" destination
                                               "&date=" (format-date (System/currentTimeMillis))))
                   (enlive/content destination)))

(enlive/deftemplate -index-tpl "public/index.html"
  [popular-destinations]
  [:.splash] (enlive/add-class (rand-nth ["splash1" "splash2" "splash3"]))
  [:form] (enlive/set-attr :action "/search"
                           :method "get")
  [:ul.popular-destinations] (enlive/content (map popular-dest-snippet popular-destinations))
  )

(defn index-tpl [req]
  (let [dests (data/popular-destinations (:store req))]
    (-index-tpl dests)
    )
  )


;; Search

(enlive/defsnippet search-result-row "public/search/index.html" [[:tr.search-result-row (enlive/nth-of-type 1)]]
  [req {id :id user :user dest :destination date :date}]
  [:span.name] (enlive/content (:name user))
  [:.date] (enlive/content (format-date date))
  [:.destination] (enlive/content dest)
  [:.pic] (enlive/set-attr :src (photo-url user 50 50))
  [:a.profile] (enlive/set-attr :href (str "/profile/" (:id user)))
  [:form.delete] (if (is-current-user? req (:id user))
                   (enlive/do->
                     (insert-csrf-field)
                     (enlive/set-attr :action (str"/delete-trip/" id)
                                      :method "POST"))
                   (enlive/substitute "")))

(enlive/deftemplate -search-tpl "public/search/index.html"
  [req {dest :destination date :date results :results}]
  [:input.destination] (enlive/do->
                  (enlive/set-attr :value dest)
                  (enlive/set-attr :name "destination")
                  )
  [:input.date] (enlive/do->
                  (enlive/set-attr :value date)
                  (enlive/set-attr :name "date")
                  )

  [:form#search-terms] (enlive/do->
                         (enlive/set-attr :action "")
                         (enlive/set-attr :method "GET")
                         )

  [:#no-results-found] (if (empty? results)
                         (enlive/add-class "ok")
                         (enlive/substitute ""))

  [:#search-result-table] (if (empty? results)
                            (enlive/substitute "")
                            (enlive/add-class "ok")
                            )
  [:#search-result-table :tbody] (enlive/content (map search-result-row (repeat req) results))

  [:form#add-trip] (enlive/do->
                     (insert-csrf-field)
                     (enlive/set-attr :action "/create-trip")
                     (enlive/set-attr :method "post"))

  [:#unauthenticated :a.button] (enlive/set-attr :href "/edit-profile")

  [:#authenticated] (if (is-authenticated? req) (enlive/wrap :div) (enlive/substitute ""))
  [:#unauthenticated] (if (is-authenticated? req) (enlive/substitute "") (enlive/wrap :div))
  )

(defn search-tpl [req]
  (let [{destination "destination"
         date "date"} (:query-params req)
        results (map
                  #(assoc % :user (data/get-user (:store req) (:user-id %)))
                  (data/query-trips (:store req) destination (as-date date)))

        ]
    (-search-tpl req
      {
       :destination destination
       :date date
       :results results}
      )))


;; Profile


(enlive/deftemplate -profile-tpl "public/profile/index.html"
  [user is-current-user flash-msg]
  [:.flash-messages] (enlive/content flash-msg)
  [:.name] (enlive/content (:name user))
  [:.about] (enlive/content (:description user))
  [:img.pic] (enlive/set-attr :src (photo-url user 150 150))
  [:a.edit] (enlive/set-attr :href "/edit-profile")
  [:#authenticated] (if-not is-current-user (enlive/content "") (enlive/wrap :div))
  [:#unauthenticated] (if is-current-user (enlive/content "") (enlive/wrap :div))
  [:#send-message-form :textarea] (enlive/set-attr :name "message")
  [:#send-message-form] (enlive/do->
                          (insert-csrf-field)
                          (enlive/set-attr :action (str "/send-message/" (:id user))
                                           :method "POST")))


(defn profile-tpl [req]
  (let [user-id (-> req :route-params :id)
        user (data/get-user (:store req) user-id)
        is-current-user (= (:id (current-user req)) user-id)
        ]
    (if user
      (-profile-tpl user is-current-user (:flash req))
      "404 Not Found")))


;; Create/Edit Profile


(enlive/deftemplate edit-profile-tpl "public/edit-profile/index.html"
  [user]
  [:input.name] (enlive/set-attr :value (:name user) :name "name")
  [:input.email] (enlive/set-attr :value (:email user) :name "email")
  [:textarea.about] (enlive/do-> (enlive/content (:description user))
                              (enlive/set-attr :name "description"))
  [:img.pic] (enlive/set-attr :src (photo-url user 150 150))
  [:form] (enlive/do->
            (insert-csrf-field)
            (enlive/set-attr :action "/update-profile")
            (enlive/set-attr :method "post")))



(defn edit-profile [req]
  (let [user (current-user req)]
    (edit-profile-tpl user)))


(defn update-profile [req]
  (let [user (current-user req)
        params (:form-params req)
        user (assoc user
                    :name (get params "name")
                    :email (get params "email")
                    :description (get params "description"))
        user-id (:id user)
        ]
    (data/put-user! (:store req) user)

    (assoc (response/redirect (str "/profile/" user-id))
           :session (assoc-in (:session req)
                              [:cemerick.friend/identity :authentications user-id]
                              user))))

(defn add-trip [req]
  (let [user (current-user req)
        {date "date"
         destination "destination"} (:form-params req)
        trip (data/make-trip user destination date)]
    (data/put-trip! (:store req) trip)

    (response/redirect (str "/search?destination=" destination "&date=" date))))

(defn delete-trip [req]
  (let [user (current-user req)
        trip-id (-> req :route-params :id)
        trip (data/get-trip (:store req) trip-id)]
    (if (= (:user-id trip) (:id user))
      (data/delete-trip! (:store req) trip-id))
    (response/redirect
      (str "/search?destination="
           (:destination trip)
           "&date=" (format-date (or (:date trip) 0))))))


(defn send-email [from to msg]
  (client/post
    "https://api.postmarkapp.com/email"
    {:basic-auth ["api" "key-f5ed0911d1ab938386f743d7643f81eb"]
     :headers {"X-Postmark-Server-Token" (System/getenv "POSTMARK_SECRET")
               "Content-Type" "application/json"
               "Accept" "application/json"}

     :body (json/write-str {"From" "booker@adambard.com"
                            "ReplyTo" from
                            "To" to
                            "Subject" "New message from Booker"
                            "TextBody" msg})}))

(defn send-message [req]
  (let [from-user (current-user req)
        to-user (data/get-user (:store req) (-> req :route-params :id))
        message (get (:form-params req) "message")
        err-msg (cond
                  (empty? (:email from-user)) "Please fill in your email address"
                  (empty? (:email to-user)) "This user cannot be contacted"
                  (empty? message) "Please enter a message")]
    (if-not err-msg
      (future (send-email
        (:email from-user) (:email to-user)
        (format (str "Someone sent you a message on Booker\n\n"
                     "From: %s <%s>\n\n"
                     "---\n\n"
                     "%s")
                (:name from-user)
                (:email from-user)
                message)))
      )
    (assoc (response/redirect (str "/profile/" (:id to-user)))
           :flash (or err-msg "Message sent."))
    ))


(comment
  (def tok "CAAKB8mJi3zQBAHgxqg2AdauRemVLszoPDckeNA0HZAEg39cczhgvzdS3eCCQZBcLCrZC2o1z22R1GjWDZB2fllBBDXzuKbsjlzoeGmD4nPqER13FlQk3HZBZBQZCkErIYJ15KOzdPzwsbO0kvr79TwXKsDWVClycLZBRNeClZCJpkKL3pbbRXZBeEZBvmYOFtwle677CfVcOVyGQYe8REHe48hw")
  (require '[clj-http.client :as client])

  
  )

