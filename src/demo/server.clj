(ns demo.server
  (:require [reitit.ring :as ring] ; <-- ring adatper for reitit
            [ring.adapter.jetty :as jetty] ; <--  jetty adapter for ring
            [next.jdbc :as jdbc] ; <-- jdbc
            [clojure.edn :as edn] ; <-- edn
            ))

(def db {:dbtype "postgresql"
         :dbname "postgres"
         :host "localhost"
         :user "postgres"
         :password "example"})

(defn get-person-handler [request]
  {:status 200
   :body (pr-str (jdbc/execute! db ["select * from person"]))
   :headers {"Content-Type" "application/edn"}})

(defn valid-post-body [x]
  (when (contains? x :person/name)
    x))

(defn post-person-handler [request]
  (try
    (if-some [person (some-> (:body request)
                             (slurp)
                             (edn/read-string)
                             (valid-post-body))]
      (let [result (jdbc/execute-one! db ["insert into person (name) values (?)"
                                          (:person/name person)]
                                      {:return-keys true})]
        {:status 200
         :body (pr-str result)
         :headers {"Content-Type" "application/edn"}})
      ; else
      (throw (ex-info "Invalid body" {})))
    (catch Exception e
      {:status 500
       :body (pr-str (Throwable->map e))
       :headers {"Content-Type" "application/edn"}})))

(def app (-> [["/person" {:get  {:handler #'get-person-handler}
                          :post {:handler #'post-person-handler}}]]
             (ring/router)
             (ring/ring-handler)))

(defonce server (atom nil))

(defn start []
  (reset! server (jetty/run-jetty #'app {:port 8887 :join? false})))

(defn stop []
  (some-> @server (.stop)))
