(ns demo.client.core
  (:require [reagent.core :as r]
            [clojure.edn :as edn]))

(defn post-user [user]
  (-> (js/fetch "/users"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/edn"}
                     :body (pr-str user)})
      (.then #(.text %))
      (.then #(edn/read-string %))))

(defn get-users []
  (-> (js/fetch "/users")
      (.then #(.text %))
      (.then #(edn/read-string %))))

(defonce state (r/atom {}))

(defn users-list []
  (r/with-let [_ (-> (get-users)
                     (.then #(swap! state assoc :users %)))]
    (if-some [users (:users @state)]
      [:<>
       (map (fn [{:keys [:users/id :users/name] :as user}]
              ^{:key id}
              [:dl
               [:dt "Name"]
               [:dd name]])
            users)]
      [:span "loading..."])))

(defn create-user []
  (r/with-let [form-state (r/atom :unstarted)]
    (case @form-state
      :unstarted
      [:button {:on-click #(reset! form-state :started)}
       "Create New"]
      [:form {:on-submit #(do (.preventDefault %)
                              (reset! form-state :submitting)
                              (-> (post-user {:users/name (.. % -target -elements -name -value)})
                                  (.then (fn [user]
                                            (swap! state update :users conj user)
                                            (reset! form-state :unstarted)))))}
       [:input {:name "name"
                :required true
                :disable (= :submitting @form-state)}]
       [:button {:disable (= :submitting @form-state)}
        "Submit"]])))

(r/render [:<>
           [:h1 "Welcome"]
           [users-list]
           [create-user]]
          (js/document.getElementById "root"))
