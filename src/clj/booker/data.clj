(ns booker.data
  (:require
    [booker.helpers :refer [date as-date]])
  (:import java.util.UUID))

(defn uuid []
  (str (UUID/randomUUID)))

;; AtomStore for today

(defrecord AtomStore [data])


(defn get-things [data thing-type]
  (vals (get @data thing-type)))

(defn get-thing [data thing-type id]
  (get-in @data [thing-type id]))

(defn put-thing! [data thing-type thing]
  (let [id (or (:id thing) (uuid))]
    (swap! data assoc-in [thing-type id] (assoc thing :id id))))



;; Users


(defrecord User [id facebook-access-token email name pic description])

; #justuserrhings
(defprotocol UserStore
  (get-users [this])
  (get-user [this id])
  (put-user! [this user]))


(extend-protocol UserStore
  AtomStore
  (get-users [this] (get-things (:data this) :users))
  (get-user [this id] (get-thing (:data this) :users id))
  (put-user! [this user] (put-thing! (:data this) :users user)))



;; Trips


(defrecord Trip [id user-id destination date])

(defprotocol TripStore
  (put-trip! [this trip])
  (get-trips-by-user [this user])
  (query-trips [this destination date]))

(extend-protocol TripStore
  AtomStore
  (put-trip! [this trip] (put-thing! (:data this) :trips trip))

  (get-trips-by-user [this user]
    (filter #(= (:user-id %) (:id user)) (get-things (:data this) :trips)))

  (query-trips [this destination date]
    (->> (get-things (:data this) :trips)
        (filter #(= (:destination %) destination))
        (sort-by #(Math/abs (- date (:date %))))
        (map #(assoc % :user (get-user this (:user-id %))))
        )))


(def init-data
  (let [ids (vec (for [i (range 10)] (uuid)))]
    {:users {(nth ids 0) (->User (nth ids 0) nil "user1@example.com" "Steve Q" "/img/pic.jpg" "A test user named Steve")
             (nth ids 1) (->User (nth ids 1) nil "user2@example.com" "Jane R" "/img/pic.jpg" "A test user named Jane")
             }
     :trips {(nth ids 2) (->Trip (nth ids 2) (nth ids 0) "Istanbul" (date 2014 10 6))
             (nth ids 3) (->Trip (nth ids 3) (nth ids 0) "Timbuktu" (date 2015 2 4))
             (nth ids 4) (->Trip (nth ids 4) (nth ids 1) "Timbuktu" (date 2015 1 25))
             (nth ids 5) (->Trip (nth ids 5) (nth ids 1) "Istanbul" (date 2015 2 4)) }}))

(def store (->AtomStore (atom init-data)))

(defn wrap-atom-store [handler store]
  (fn [req]
    (handler (assoc req :store store))))



(comment
  (def user (->User (uuid) nil "test@adambard.com" "Test User" nil nil))
  (def trip (->Trip (uuid) (:id user) "Istanbul" (System/currentTimeMillis)))

  (def store (->AtomStore (atom {})))

  (put-trip! store trip)
  (get-trips-by-user store user)
  (query-trips store "Krakow" (System/currentTimeMillis))
  (query-trips store "Istanbul" (System/currentTimeMillis))
  (prn store)

  )


