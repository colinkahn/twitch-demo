(ns demo.server-test
  "Property based testing inpired by https://lispcast.com/testing-stateful-and-concurrent-systems-using-test-check/"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [ring.core.spec]
            [clojure.edn :as edn]
            [demo.server :as server]
            [next.jdbc :as jdbc]))

; using clojure spec to define the semantics of our person keywords
(s/def :person/id int?)
(s/def :person/name (s/and string? (complement clojure.string/blank?)))
(s/def :person/person (s/keys :req [:person/id :person/name]))

; generate two element vectors of op keyword and optional map of data
; these are the operations we can do to our application
(defn gen-ops []
  (gen/vector
    (gen/one-of [(gen/tuple (gen/return :person.post/success)
                            (gen/hash-map :edn-body (s/gen (s/keys :req [:person/name]))))
                 (gen/tuple (gen/return :person.post/failure))
                 (gen/tuple (gen/return :person.get/all))])))

; create a hash-map runner of our app that given our ops tuples returns a
; hash-map of :persons results, as we add more ops we would add more here
(defn hm-run [db ops]
  (reduce (fn [hm [op {:keys [edn-body]}]]
            (case op
              ; inserts the person incrementing the id of the last person
              :person.post/success
              (let [next-id (inc (or (-> hm :persons last :person/id) 0))]
                (update hm :persons (fnil conj []) (assoc edn-body :person/id next-id)))
              ; neither of these should change the state
              :person.post/failure hm
              :person.get/all      hm))
          db ops))

; create a runner for our actual app and generate via spec + overrides the
; requests for each op
(defn app-run! [ops]
  (doseq [[op {:keys [edn-body]}] ops]
    (case op
      ; with an expected edn body
      :person.post/success
      (-> (s/gen :ring/request
                 {[:uri] #(gen/return "/person")
                  [:request-method] #(gen/return :post)})
          (gen/generate)
          (assoc :body (java.io.StringBufferInputStream. (pr-str edn-body)))
          (server/app))

      ; with random edn body
      :person.post/failure
      (-> (s/gen :ring/request
                 {[:uri] #(gen/return "/person")
                  [:request-method] #(gen/return :post)})
          (gen/generate)
          (server/app))

      ; basic get
      :person.get/all
      (-> (s/gen :ring/request
                 {[:uri] #(gen/return "/person")
                  [:request-method] #(gen/return :get)})
          (gen/generate)
          (server/app)))))

(defn reset-db! []
  (jdbc/execute! server/db ["TRUNCATE TABLE person RESTART IDENTITY"]))

; check whether our hash-map version is equivilent to our actual db state, right
; now just checks that the set of persons in the db matches those we inserted
; into our hash map and makes sure each person conforms to our spec
(defn db-equiv? [hm]
  (let [persons (jdbc/execute! server/db ["select * from person"])]
    (and (= (set persons) (set (:persons hm)))
         (s/valid? (s/coll-of :person/person) persons))))

; the actual generative test
(defspec hash-map-equiv 100
  (prop/for-all [ops (gen-ops)]
                (let [hm (hm-run {} ops)]
                  (reset-db!)
                  (app-run! ops)
                  (db-equiv? hm))))

(comment
  (hash-map-equiv 100))
