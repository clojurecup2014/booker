(ns booker.data
  (:require
    [booker.helpers :refer [date as-date]]
    [taoensso.carmine :as car]
    )
  (:import java.util.UUID))

(defn uuid []
  (str (UUID/randomUUID)))

;; AtomicStore for today

(defrecord AtomicStore [data])


(defn get-things [data thing-type]
  (vals (get @data thing-type)))

(defn get-thing [data thing-type id]
  (get-in @data [thing-type id]))

(defn populate-id [thing]
  (let [id (or (:id thing) (uuid))]
    (assoc thing :id id)))

(defn put-thing! [data thing-type thing]
  (let [thing (populate-id thing)]
    (swap! data assoc-in [thing-type (:id thing)] (populate-id thing))))

(defn delete-thing! [data thing-type thing-id]
  (swap! data (fn [data]
                (assoc data thing-type
                       (dissoc (get data thing-type) thing-id)))))

(defrecord RedisStore [conn])


;; Users


(defrecord User [id facebook-access-token facebook-user-id email name pic description])

; #justuserrhings
(defprotocol UserStore
  (get-users [this])
  (get-user [this id])
  (get-user-by-facebook-id [this facebook-id])
  (put-user! [this user]))


(defn make-user [tok]
  (->User (uuid) tok nil nil nil nil nil))

(extend-protocol UserStore
  booker.data.AtomicStore
  (get-users [this] (get-things (:data this) :users))
  (get-user [this id] (get-thing (:data this) :users id))
  (get-user-by-facebook-id [this facebook-id]
    (first (filter #(= (:facebook-user-id %) facebook-id) (get-users this))))
  (put-user! [this user] (put-thing! (:data this) :users user))

  booker.data.RedisStore
  (put-user! [this user]
    (let [user (populate-id user)]
      (car/wcar (:conn this)
                (car/hset "users" (:id user) user)
                (car/hset "users-by-fb-id" (:facebook-user-id user) user))))
  (get-users [this]
    (->>
      (car/wcar (:conn this) (car/hgetall "users"))
      (partition 2)
      (map last)))
  (get-user [this id]
    (car/wcar (:conn this) (car/hget "users" id)))
  (get-user-by-facebook-id [this fb-id]
    (car/wcar (:conn this) (car/hget "users-by-fb-id" fb-id))))

(comment
  (def rstore (->RedisStore nil))

  (let [trip-ids (map first (partition 2 (car/wcar nil (car/hgetall "trips"))))]
    (mapv (partial delete-trip! rstore) trip-ids)
    )
  (doseq [u (get-users rstore)]
    (let [trips (get-trips-by-user rstore u)]
      
      )
    )

  (get-users rstore)
  (get-user rstore (:id u))
  (put-user! rstore (assoc u :facebook-user-id "1"))
  (get-user rstore (:id u))
  (get-user-by-facebook-id rstore "1")
  (def u (make-user "HEY YOU"))
  (car/wcar nil
    (car/hset "users" (:id u) (assoc u :name "GUY"))
    (car/hset "users-by-fb-id" (:facebook-user-id))
    )

  (map last (partition 2 (car/wcar nil
    (car/hgetall "users")

            )))

  (car/wcar nil
            (car/hget "users" (:id u))
            )
  )



;; Trips


(defrecord Trip [id user-id destination date])

(defn make-trip [user destination date]
  (->Trip (uuid) (:id user) destination (as-date date)))

(defprotocol TripStore
  (get-trip [this trip-id])
  (put-trip! [this trip])
  (delete-trip! [this trip-id])
  (get-trips-by-user [this user])
  (query-trips [this destination date]))

(defn by-user-key [trip] (str "trips-by-user:" (:user-id trip)))
(defn by-dest-key [trip] (str "trips-by-dest:" (:destination trip)))

(extend-protocol TripStore
  booker.data.AtomicStore
  (get-trip [this trip-id] (get-thing (:data this) :trips trip-id))
  (put-trip! [this trip] (put-thing! (:data this) :trips trip))
  (delete-trip! [this trip-id] (delete-thing! (:data this) :trips trip-id))

  (get-trips-by-user [this user]
    (filter #(= (:user-id %) (:id user)) (get-things (:data this) :trips)))

  (query-trips [this destination date]
    (->> (get-things (:data this) :trips)
        (filter #(= (:destination %) destination))
        (sort-by #(Math/abs (- (as-date date) (as-date (:date %)))))))

  booker.data.RedisStore

  (put-trip! [this trip]
    (let [trip (populate-id trip)
          score (as-date (:date trip))]
      (car/wcar (:conn this)
                (car/hset "trips" (:id trip) trip)
                (car/zadd (by-dest-key trip) score (:id trip))
                (car/zadd (by-user-key trip) score (:id trip)))))

  (delete-trip! [this trip-id]
    (let [trip (get-trip this trip-id)]
      (car/wcar (:conn this)
                (car/hdel "trips" trip-id)
                (car/zrem (by-dest-key trip) trip-id)
                (car/zrem (by-user-key trip) trip-id))))

  (get-trip [this trip-id]
    (car/wcar (:conn this)
      (car/hget "trips" trip-id)))

  (get-trips-by-user [this user]
    (let [user-key (by-user-key {:user-id (:id user)})
          trip-ids (car/wcar (:conn this) (car/zrangebyscore user-key Long/MIN_VALUE Long/MAX_VALUE))]
      (if-not (empty? trip-ids)
        (car/wcar (:conn this)
                  (apply car/hmget "trips" trip-ids)))))

  (query-trips [this destination date]
    (let [dest-key (by-dest-key {:destination destination})
          trip-ids (car/wcar (:conn this)
                    (car/zrangebyscore dest-key
                        (- (as-date date) (* 1000 60 60 24) 1) ; 1 day before, off by 1
                        (+ (as-date date) (* 1000 60 60 24 7) 1) ; 1 week after
                        ))]
      (if-not (empty? trip-ids)
        (car/wcar (:conn this) (apply car/hmget "trips" trip-ids)))))
  )

(comment
  (def u (make-user "test"))
  (def t (make-trip u "Istanbul" (java.util.Date.)))
  (def t2 (make-trip u "Istanbul" (java.util.Date.)))

  (put-trip! rstore t)
  (put-trip! rstore t2)
  (get-trip rstore (:id t))
  (get-trips-by-user rstore u)
  (query-trips rstore "Istanbul" (System/currentTimeMillis))
  (car/wcar nil
            (car/hget "trips" (:id t))
            )
  (def trip-ids (car/wcar nil (car/zrangebyscore (by-user-key t) Long/MIN_VALUE Long/MAX_VALUE)))
 (car/wcar
    nil
    (apply car/hmget "trips" trip-ids)
    )
  )


(def init-data
  (let [ids (vec (for [i (range 10)] (uuid)))]
    {:users {(nth ids 0) (->User (nth ids 0) nil nil "user1@example.com" "Steve Q" "/img/pic.jpg" "A test user named Steve")
             (nth ids 1) (->User (nth ids 1) nil nil "user2@example.com" "Jane R" "/img/pic.jpg" "A test user named Jane")
             }
     :trips {(nth ids 2) (->Trip (nth ids 2) (nth ids 0) "Istanbul" (date 2014 10 6))
             (nth ids 3) (->Trip (nth ids 3) (nth ids 0) "Timbuktu" (date 2015 2 4))
             (nth ids 4) (->Trip (nth ids 4) (nth ids 1) "Timbuktu" (date 2015 1 25))
             (nth ids 5) (->Trip (nth ids 5) (nth ids 1) "Istanbul" (date 2015 2 4)) }}))

;(def store (->AtomicStore (atom init-data)))
(def store (->RedisStore nil)) ; Redis store with default conn

(defn wrap-atom-store [handler store]
  (fn [req]
    (handler (assoc req :store store))))


(comment
  (def user (->User (uuid) nil nil "test@adambard.com" "Test User" nil nil))
  (def trip (->Trip (uuid) (:id user) "Istanbul" (System/currentTimeMillis)))

  (def store (->AtomicStore (atom {})))

  (get-users store)

  (put-trip! store trip)
  (get-trips-by-user store user)
  (query-trips store "Krakow" (System/currentTimeMillis))
  (def trip (first (query-trips store "Istanbul" (System/currentTimeMillis))))

  (get-trip store (:id trip))
  (put-trip! store trip)
  (delete-trip! store (:id trip))

  (prn store)

  )


