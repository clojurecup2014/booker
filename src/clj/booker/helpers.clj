(ns booker.helpers
  (:require [clojure.algo.monads :refer [domonad defmonad]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.format :as f]
            )
  (:import com.mdimension.jchronic.Chronic)
  )

(declare try*)
(declare as-date)

(defn- -parse-date [d]
  (-> (Chronic/parse d)
      (.getBeginCalendar)
      (as-date)))

(defn parse-date
  "Turn a string date into an integer one"
  [d]
  (try (-parse-date d) (catch Exception e -1))
  )


(defprotocol CoerceDate
  (as-date [this]))

(extend-protocol CoerceDate
  java.util.Calendar
  (as-date [this]
    (-> this
        (doto
          (.setTimeZone (java.util.TimeZone/getTimeZone "UTC"))

          ; A bug only appeared here when I was developing in the afternoon. WTF Java?
          (.set java.util.Calendar/AM_PM java.util.Calendar/AM)

          (.set java.util.Calendar/HOUR 0)
          (.set java.util.Calendar/MINUTE 0)
          (.set java.util.Calendar/SECOND 0)
          (.set java.util.Calendar/MILLISECOND 0))
        (.getTime) ; java.util.Date
        (.getTime)))

  java.util.Date
  (as-date [this]
    ; Send to Calendar to zero out non-date-parts
    (let [cal (java.util.Calendar/getInstance)]
      (.setTime cal this)
      (as-date cal)))

  java.lang.Long
  (as-date [this] this)

  java.lang.String
  (as-date [this] (parse-date this))

  org.joda.time.DateTime
  (as-date [this] (c/to-long this))

  java.lang.Object
  (as-date [this]
    (str "as-date is not implemented for " (class this))
    -1))

(defn date [y m d]
  (as-date (t/date-time y m d)))

(defn format-date [d]
  (f/unparse (f/formatters :date) (c/from-long (as-date d))))


;; Error handling

(defrecord Failure [message])
(defn fail [message] (->Failure message))

(defprotocol ComputationFailed
  (has-failed? [self])
  (user-error-message [self])
  )

(extend-protocol ComputationFailed
  Object
  (has-failed? [self] false)
  (user-error-message [self] nil)

  Failure
  (has-failed? [self] true)
  (user-error-message [self] (:message self))

  Exception
  (has-failed? [self] true)
  (user-error-message [self] "A server error occurred."))


(defmonad error-m
  [m-result identity
   m-bind   (fn [m f]
              (if (has-failed? m)
                m
                (f m)))])


(defmacro attempt-all
  ([bindings return] `(domonad error-m ~bindings ~return))
  ([bindings return else]
     `(let [result# (attempt-all ~bindings ~return)]
        (if (has-failed? result#)
            ~else
            result#))))


(defn try* [f]
  (fn [& args]
    (try
      (apply f args)
      (catch Exception e e))))
