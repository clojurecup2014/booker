(defproject booker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/algo.monads "0.1.5"]
                 [compojure "1.1.9"]
                 [ring "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [javax.servlet/servlet-api "2.5"]
                 [com.cemerick/friend "0.2.0"]
                 [friend-oauth2 "0.1.1"]
                 [http-kit "2.1.16"]
                 [enlive "1.1.5"]
                 [clj-time "0.8.0"]
                 [com.rubiconproject.oss/jchronic "0.2.6"]
                 [com.taoensso/carmine "2.7.0" :exclusions [org.clojure/clojure]]
                 [com.taoensso/timbre "3.3.1"]

                 ; Cljs
                 [org.clojure/clojurescript "0.0-2197"]
                 [figwheel "0.1.4-SNAPSHOT"]
                 [reagent "0.4.1"]
                 [kioo "0.4.1-SNAPSHOT"]
                 ]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-figwheel "0.1.4-SNAPSHOT"]]

  :main booker.core
  :source-paths ["src/clj"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/cljs"]
              :compiler {:output-to "resources/public/js/compiled/all.js"
                         :output-dir "resources/public/js/compiled/out"
                         :externs ["resources/public/js/react-0.9.0.js"]
                         :optimizations :none
                         :source-map true
                         }}
             {:id "min"
              :source-paths ["src/cljs"]
              :compiler {:output-to "resources/public/js/booker.min.js"
                         :optimizations :whitespace
                         :pretty-print false
                         :preamble ["public/js/react-0.9.0.js"
                                    "public/js/jquery-1.11.1.min.js"
                                    "public/js/moment.min.js"
                                    "public/js/pikaday.js"
                                    "public/js/typeahead.bundle.min.js"
                                    ]
                        }}]}
  :figwheel {
             :http-server-root "public" ;; default and assumes "resources" 
             :server-port 3449 ;; default
             :css-dirs ["public/resources/css"] ;; watch and update CSS
             ;; :ring-handler figwheel-test.server/handler
             } 
  )
