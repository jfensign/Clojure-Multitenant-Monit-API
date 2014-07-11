(defproject tenancy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [ring "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [imintel/ring-xml "0.0.2"]
                 [incanter "1.5.5"]
                 [ring-cors "0.1.4"]
                 [com.novemberain/monger "2.0.0-rc1"]]
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler tenancy.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
