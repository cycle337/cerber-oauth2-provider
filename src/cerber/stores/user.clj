(ns cerber.stores.user
  (:require [mount.core :refer [defstate]]
            [cerber
             [db :as db]
             [helpers :as helpers]
             [config :refer [app-config]]
             [store :refer :all]])
  (:import [cerber.store MemoryStore RedisStore]
           [org.mindrot.jbcrypt BCrypt]))

(declare ->map)

(defrecord User [id login email name password authoritites enabled created-at])

(defrecord SqlUserStore []
  Store
  (fetch-one [this [login]]
    (->map (first (db/find-user {:login login}))))
  (revoke-one! [this [login]]
    (db/delete-user {:login login}))
  (store! [this k user]
    (when (= 1 (db/insert-user user)) user))
  (purge! [this]
    (db/clear-users)))

(defmulti create-user-store identity)

(defstate ^:dynamic *user-store*
  :start (create-user-store (-> app-config :cerber :users :store)))

(defmethod create-user-store :in-memory [_]
  (MemoryStore. "users" (atom {})))

(defmethod create-user-store :redis [_]
  (RedisStore. "users" (-> app-config :cerber :redis-spec)))

(defmethod create-user-store :sql [_]
  (SqlUserStore.))

(defmacro with-user-store
  "Changes default binding to default users store."
  [store & body]
  `(binding [*user-store* ~store] ~@body))

(defn bcrypt
  "Performs BCrypt hashing of password."
  [password]
  (BCrypt/hashpw password (BCrypt/gensalt)))

(defn create-user
  "Creates new user"
  ([user password]
   (create-user user password nil))
  ([user password authorities]
   (let [enabled (:enabled user)
         merged  (merge-with
                  #(or %2 %1)
                  user
                  {:id (helpers/uuid)
                   :name nil
                   :email nil
                   :enabled (if (nil? enabled) true enabled)
                   :password (bcrypt password)
                   :authorities authorities
                   :created-at (helpers/now)})]

     (when (store! *user-store* [:login] merged)
       (map->User merged)))))

(defn find-user [login]
  (if-let [found (and login (fetch-one *user-store* [login]))]
    (map->User found)))

(defn revoke-user
  "Removes user from store"
  [login]
  (revoke-one! *user-store* [login]))

(defn purge-users
  []
  "Removes users from store. Used for tests only."
  (purge! *user-store*))

(defn valid-password?
  "Verify that candidate password matches the hashed bcrypted password"
  [candidate hashed]
  (BCrypt/checkpw candidate hashed))

(defn ->map [result]
  (when-let [{:keys [created_at modified_at]} result]
    (-> result
        (assoc  :created-at created_at :modified-at modified_at)
        (dissoc :created_at :modified_at))))
