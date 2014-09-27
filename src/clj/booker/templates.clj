(ns booker.templates
  (:require [net.cgrand.enlive-html :as enlive]
            net.cgrand.reload
            [booker.data :as data]
            [booker.helpers :refer [as-date format-date]]
            )
  )

(net.cgrand.reload/auto-reload *ns*)



(enlive/deftemplate index-tpl "public/index.html"
  [req]
  )


;; Search

(enlive/defsnippet search-result-row "public/search.html" [[:tr.search-result-row (enlive/nth-of-type 1)]]
  [{id :id user :user dest :destination date :date}]
  [:span.name] (enlive/content (:name user))
  [:.date] (enlive/content (format-date date))
  [:.destination] (enlive/content dest)
  [:.pic] (enlive/set-attr :src (:pic user))
  [:a.button] (enlive/set-attr :href (str "/profile/" (:id user)))
  )


(enlive/deftemplate -search-tpl "public/search.html"
  [{dest :destination date :date results :results}]
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

  [:#search-result-table :tbody] (enlive/content (map search-result-row results))
  )

(defn search-tpl [req]
  (let [{destination "destination"
         date "date"} (:query-params req)
        results (data/query-trips (:store req) destination (as-date date))
        ]
    (-search-tpl
      {
       :destination destination
       :date date
       :results results})))


;; Profile


(enlive/deftemplate -profile-tpl "public/profile.html"
  [user]
  [:.name] (enlive/content (:name user))
  [:.about] (enlive/content (:description user))
  )


(defn profile-tpl [req]
  (let [user-id (-> req :route-params :id)
        user (data/get-user (:store req) user-id)]
    (if user
      (-profile-tpl user)
      "404 Not Found")))


;; Edit Profile


(enlive/deftemplate edit-profile-tpl "public/edit-profile.html"
  [req]
  )
