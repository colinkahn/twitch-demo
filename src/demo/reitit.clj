(ns demo.reitit
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [next.jdbc :as jdbc]
            [clojure.edn :as edn]))

(def db {:dbtype "postgresql"
         :dbname "postgres"
         :host "localhost"
         :user "postgres"
         :password "example"})

(defn get-users-handler [request]
  {:status 200
   :body (pr-str (jdbc/execute! db ["select * from users"]))
   :headers {"Content-Type" "application/edn"}}
  )

(defn post-users-handler [request]
  (try
    (let [input (some-> (:body request) (slurp) (edn/read-string))
          result (jdbc/execute-one! db ["insert into users (name) values (?)"
                                        (:users/name input)]
                                    {:return-keys true})]
      {:status 200
       :body (pr-str result)
       :headers {"Content-Type" "application/edn"}})
    (catch Exception e
      {:status 500
       :body (pr-str (Throwable->map e))
       :headers {"Content-Type" "application/edn"}})))

(def app (-> [["/users" {:get {:handler #'get-users-handler}
                         :post {:handler #'post-users-handler}}]
              ["/*" (ring/create-resource-handler)]]
             (ring/router {:conflicts (constantly nil)})
             (ring/ring-handler)))

(defonce server (jetty/run-jetty #'app {:port 8887 :join? false}))

(comment
  (.stop server))
