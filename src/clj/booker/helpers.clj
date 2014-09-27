(ns booker.helpers)

(defn parse-date
  "Turn a string date into an integer one"
  [d]
  ; TODO
  (System/currentTimeMillis))

(defprotocol CoerceDate
  (as-date [this]))

(extend-protocol CoerceDate
  java.util.Calendar
  (as-date [this] (as-date (.getTime this)))

  java.util.Date
  (as-date [this] (.getTime this))

  java.lang.Long
  (as-date [this] this)

  java.lang.String
  (as-date [this] (parse-date this))

  java.lang.Object
  (as-date [this]
    (throw (Exception. (str "as-date is not implemented for " (class this))))))

(defn date [y m d]
  (as-date
    (doto (java.util.Calendar/getInstance)
      (.set y m d 0 0 0))))

(defn format-date [d]
  (let [cal (java.util.Calendar/getInstance)]
    (.setTime cal (java.util.Date. (as-date d)))
    (str
      (.get cal java.util.Calendar/YEAR)
      "-"
      (inc (.get cal java.util.Calendar/MONTH))
      "-"
      (.get cal java.util.Calendar/DAY_OF_MONTH)
      )
    )
  )
