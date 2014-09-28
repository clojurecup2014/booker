(ns booker.core
  (:require
    [figwheel.client :as fw :include-macros true]
    [kioo.core :refer [handle-wrapper]]
            [kioo.reagent :as kioo]
            [reagent.core :as reagent :refer [atom]])
  (:require-macros [kioo.reagent :refer [defsnippet deftemplate]])
  )

(enable-console-print!)

(def cities [
  "London"
  "New York City"
  "Hong Kong"
  "Paris"
  "Singapore"
  "Shanghai"
  "Tokyo"
  "Beijing"
  "Sydney"
  "Dubai"
  "Chicago"
  "Mumbai"
  "Milan"
  "Moscow"
  "São Paulo"
  "Frankfurt"
  "Toronto"
  "Los Angeles"
  "Madrid"
  "Mexico City"
  "Amsterdam"
  "Kuala Lumpur"
  "Brussels"
  "Seoul"
  "Johannesburg"
  "Buenos Aires"
  "Vienna"
  "San Francisco"
  "Istanbul"
  "Jakarta"
  "Zürich"
  "Warsaw"
  "Washington, D.C."
  "Melbourne"
  "New Delhi"
  "Miami"
  "Barcelona"
  "Bangkok"
  "Boston"
  "Dublin"
  "Taipei"
  "Munich"
  "Stockholm"
  "Prague"
  "Atlanta"
  "Bangalore"
  "Lisbon"
  "Copenhagen"
  "Santiago"
  "Guangzhou"
  "Rome"
  "Cairo"
  "Dallas"
  "Hamburg"
  "Düsseldorf"
  "Athens"
  "Manila"
  "Montreal"
  "Philadelphia"
  "Tel Aviv"
  "Lima"
  "Budapest"
  "Berlin"
  "Cape Town"
  "Luxembourg"
  "Houston"
  "Kiev"
  "Bucharest"
  "Beirut"
  "Ho Chi Minh City"
  "Bogotá"
  "Auckland"
  "Montevideo"
  "Caracas"
  "Riyadh"
  "Vancouver"
  "Chennai"
  "Manchester"
  "Oslo"
  "Brisbane"
  "Helsinki"
  "Karachi"
  "Doha"
  "Casablanca"
  "Stuttgart"
  "Rio de Janeiro"
  "Geneva"
  "Guatemala City"
  "Lyon"
  "Monterrey"
  "Panama City"
  "Rica San José"
  "Bratislava"
  "Minneapolis"
  "Tunis"
  "Nairobi"
  "Cleveland"
  "Lagos"
  "Abu Dhabi"
  "Seattle"
"Hanoi"
"Sofia"
"Riga"
"Port Louis"
"Detroit"
"Calgary"
"Denver"
"Perth"
"Kolkata"
"San Diego"
"Amman"
"Antwerp"
"Manama"
"Birmingham"
"Nicosia"
"Quito"
"Rotterdam"
"Belgrade"
"Almaty"
"Shenzhen"
"Kuwait City"
"Hyderabad"
"Edinburgh"
"Zagreb"
"Lahore"
"Saint Petersburg"
"Jeddah"
"Durban"
"Santo Domingo"
"Baltimore"
"Islamabad"
"Guayaquil"
"St. Louis"
"San Salvador"
"Cologne"
"Phoenix"
"Adelaide"
"Bristol"
"Charlotte"
"George Town"
"Osaka"
"Tampa"
"Glasgow"
"San Juan"
"Marseille"
"Guadalajara"
"Leeds"
"Baku"
"Vilnius"
"Tallinn"
"Raleigh"
"Ankara"
"Belfast"
"San Jose"
"Colombo"
"Valencia"
"Cincinnati"
"Milwaukee"
"Muscat"
"Ljubljana"
"Nantes"
"Tianjin"
"Accra"
"Algiers"
"Gothenburg"
"Porto"
"Columbus"
"Utrecht"
"Orlando"
"Ahmedabad"
"Asunción"
"Kansas City"
"Seville"
"Turin"
"Dar es Salaam"
"Portland"
"Kraków"
"Managua"
"Pune"
"Leipzig"
"Malmö"
"La Paz"
"Southampton"
"Indianapolis"
"Porto Alegre"
"Strasbourg"
"Gaborone"
"Chengdu"
"Richmond"
"Pittsburgh"
"Tijuana"
"Austin"
"Qingdao"
"Nassau"
"Tegucigalpa"
"Lille"
"Curitiba"
"The Hague"
"Hartford"
"Wroclaw"
"Edmonton"
"Lausanne"
"Dhaka"
"Nuremberg"
"Lusaka"
"Kampala"
"Bilbao"
"Douala"
"Coast Abidjan"
"Salt Lake City"
"Hangzhou"
"Poznań"
"Wellington"
"Ottawa"
"Dakar"
"Querétaro"
"Dresden"
"Newcastle"
"Skopje"
"Nanjing"
"Tirana"
"Chongqing"
"Belo Horizonte"
"Florence"
"Pretoria"
"Toulouse"
"Aarhus"
"San Antonio"
"Bremen"
"Nashville"
"Bologna"
"Canberra"
"Nagoya"
"Sacramento"
"Providence"
"Luanda"
"Dalian"
"Liverpool"
"Jacksonville"
"Puebla"
"Kaohsiung"
"Minsk"
"Linz"
"Tbilisi"
"Las Vegas"
"Maputo"
"Harare"
"Cardiff"
"Xiamen"
"Birmingham"
"Mexico Leon"
"Port of Spain"
"Penang"
"Memphis"
"Aberdeen"
"Abuja"
"Hanover"
"Surabaya"
"Bern"
"Halifax"
"Ciudad Juárez"
"Alexandria"
"Bordeaux"
"Phnom Penh"
"Winnipeg"
"Cali"
"Greensboro"
"Genoa"
"Medellín"
"Santa Cruz"
"Montpellier"
"Córdoba"
"Wuhan"
"Graz"
"Jerusalem"
"New Orleans"
"Rochester"
"Nice"
"Busan"
"Windhoek"
"Dammam"
"Christchurch"
"Recife"
"Tashkent"
"Hamilton"
"Reykjavík"
"Naples"
"Tulsa"
"Ludwigshafen"
"Kingston"
"Brasília"
"Johor Baharu"
"Xi'an"
"Macau"
"Fukuoka"
"Sheffield"
"İzmir"
"Nottingham"
"Des Moines"
"Campinas"
"Chisinau"
"Haifa"
"Madison"
"Yerevan"
"Cebu"
"Labuan"
"Salvador"])

;; Utils

(defn kerlog [thing]
  (js/console.log (clj->js thing))
  thing)


(defn register-component-if-present [component-fn el-id]
  (if-let [el (js/document.getElementById el-id)]
    (reagent/render-component [component-fn] el )))



;; City spinner

(defn city-rotate []
  (let [city-name (atom "Vancouver")]
    (fn []
      (js/setTimeout (fn [] (swap! city-name #(rand-nth cities)))  5000)
      [:span @city-name])))

(register-component-if-present city-rotate "city-name")



; Splash page form

(def bh-cities
  (js/Bloodhound.
    (js-obj
      "datumTokenizer" (js/Bloodhound.tokenizers.obj.whitespace "value")
      "queryTokenizer" js/Bloodhound.tokenizers.whitespace
      "local" (clj->js (map (fn [x] {:value x}) cities)))))

(.initialize bh-cities)

(defn init-typeahead [el]
  (let [tt-opts (js-obj "hint" true
                        "highlight" true
                        "minLength" 1)
        bh-opts (js-obj "name" "bh-cities"
                        "displayKey" "value"
                        "source" (.ttAdapter bh-cities))]

    (.typeahead el tt-opts bh-opts)))

(defn els-by-tag [tag]
  (let [els (js/document.getElementsByTagName "input")]
    (map #(.item els %) (range (.-length els)))))

(defn init-datepicker [el]
  (.setAttribute el "type" "text")
  (js/Pikaday. (js-obj
      "field" el
      "format" "YYYY-MM-DD"
      "minDate" (js/Date. 2009 0 1))))

(js/jQuery
  (fn []
    (init-typeahead (js/jQuery "#inputs-row input.destination"))
    (doall (->> (els-by-tag "input")
                (filter #(= (.getAttribute % "type") "date"))
                (map init-datepicker)))))


;; Init datepickers





;(fw/watch-and-reload
;  :jsload-callback #(print "HELLI"))


