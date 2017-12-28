(ns app.model
  (:require [app.event-source :as evs]
            [cheshire.core :as json]
            [app.sessions :as sessions]
            [app.pg :as pg]))

(defn migrate [db]
  (pg/exec db "CREATE SEQUENCE if not exists tx")
  (pg/exec db "create table if not exists rooms    (id serial primary key, tx bigint, resource jsonb)")
  (pg/exec db "create table if not exists messages (id serial primary key, tx bigint, room_id bigint, resource jsonb)"))

(defonce room-subscription (atom {}))
(defonce users (atom {}))

@room-subscription

(defn rooms-sub [{{rid :room_id id :id :as row} :row chng :change tbl :table :as event}]
  (println "ROOM SUB: " rid (:id row) event)
  (sessions/notify
   [:rooms (str rid) :messages]
   {:body {:change chng :entity (pg/jsonb-row row)} :status 200}))

(when-not (evs/has-sub? :rooms) (evs/add-sub :rooms #'rooms-sub))

(defn next-tx [db]
  (-> (pg/q db "SELECT nextval('tx');")
      first
      :nextval))

(defn create-room [db room]
  (pg/jsonb-insert db :rooms {:resource room}))

(defn get-rooms [db]
  (pg/jsonb-query db {:select [:*] :from [:rooms]}))

(defn $get-rooms [{db :db :as req}]
  {:body (get-rooms db)})

(defn $get-room [req]
  {:body {:memebers [1 2 3]}})

(defn $subscribe-messages [{sid :session-id channel :channel {user-id :user-id} :body {room-id :id} :route-params :as req}]
  (println "Subscribe:" channel room-id)
  (sessions/add-subs [:rooms room-id :messages] sid (select-keys req [:success :uri :request-method]))
  #_(swap! room-subscription assoc-in [room-id channel]
         (select-keys req [:success :uri :request-method]))
  {:status 200 :body []})


(defn $register [{channel :channel {user-id :user-id name :name :as body} :body :as params}]
  (swap! users assoc user-id (assoc body :channel channel))
  {:body []})

(defn create-message [db message]
  (pg/jsonb-insert db :messages {:room_id (:room_id message)
                                 :resource message}))

(defn $add-message [{db :db {user-id :user-id text :text} :body {room :id} :route-params :as data}]
  (let [{name :name} (get @users user-id)
        message {:author name :message text :room_id room}]
    {:status 200
     :body (create-message db message)}))

(defn $get-messages [{db :db {room :id} :route-params}]
  {:body (pg/jsonb-query db {:select [:*] :from [:messages] :where [:= :room_id room]})})


(comment
  (pg/exec pg/db "create table if not exists rooms    (id serial primary key, tx bigint, resource jsonb)")

  (pg/exec pg/db "delete from rooms")
  (pg/exec pg/db "delete from messages")

  (get-rooms pg/db)

  (next-tx pg/db)

  (evs/add-sub :rooms
               (fn [x]
                 (when (= :rooms (:table x))
                   (println "ON CHANGE" x))))

  (evs/rm-sub :rooms)

  (create-room pg/db {:title "Clojure chat"})

  (create-room pg/db {:title "Postgres chat"})
  (create-message pg/db {:room_id 13
                         :message "Hello all"})

  (migrate pg/db)





  )

