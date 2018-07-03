(ns cerber.db
  (:require [conman.core :as conman]))

(defonce db (atom :not-initialized))

(defn init-pool [config]
  (when (and config (= @db :not-initialized))
    (Class/forName (:driver-class config))
    (conman/bind-connection (reset! db (conman/connect! config))
                            "db/cerber/tokens.sql"
                            "db/cerber/clients.sql"
                            "db/cerber/authcodes.sql"
                            "db/cerber/users.sql"
                            "db/cerber/sessions.sql")))

(defn close-pool []
  (when (not= @db :not-initialized)
    (conman/disconnect! db)))
