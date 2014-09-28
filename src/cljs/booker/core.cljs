(ns booker.core
  (:require
    [figwheel.client :as fw :include-macros true]
    [kioo.core :refer [handle-wrapper]]
            [kioo.reagent :as kioo]
            [reagent.core :as reagent :refer [atom]])
  (:require-macros [kioo.reagent :refer [defsnippet deftemplate]])
  )

(enable-console-print!)


;; Utils

(defn kerlog [thing]
  (js/console.log (clj->js thing))
  thing)


(defn register-component-if-present [component-fn el-id]
  (if-let [el (js/document.getElementById el-id)]
    (reagent/render-component [component-fn] el )))



;; City spinner

(def cities ["Istanbul"
             "Timbuktu"
             "New York"
             "Kuala Lumpur"
             "Bangkok"
             "Krakow"
             "Vienna"
             "Paris"
             "Milan"
             "Rome"
             "Bratislava"
             "Wroclaw"
             "Vancouver"
             ])

(defn city-rotate []
  (let [city-name (atom "Vancouver")]
    (fn []
      (js/setTimeout (fn [] (swap! city-name #(rand-nth cities)))  5000)
      [:span @city-name])))

(register-component-if-present city-rotate "city-name")



; Splash page form

(defn city-select [input-name]
  [:select {:name input-name}
   (map (fn [city] [:option {:value city} city]) cities)])


(defsnippet splash-inputs "public/index.html" [:#inputs]
  []
  {
    [:form] (kioo/set-attr "action" "/search")
    [:.destination] (kioo/substitute (city-select "destination"))
  })

(register-component-if-present splash-inputs "inputs-row")



;; Init datepickers

(defn init-datepicker [el]
  (.setAttribute el "type" "text")
  (js/Pikaday. (js-obj
      "field" el
      "format" "YYYY-MM-DD"
      "minDate" (js/Date. 2009 0 1))))


(defn els-by-tag [tag]
  (let [els (js/document.getElementsByTagName "input")]
    (map #(.item els %) (range (.-length els)))))


(doall (->> (els-by-tag "input")
  (filter #(= (.getAttribute % "type") "date"))
  (map init-datepicker)))


;(fw/watch-and-reload
;  :jsload-callback #(print "HELLI"))
