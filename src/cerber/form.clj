(ns cerber.form
  (:require [failjure.core :as f]
            [cerber.config :refer [app-config]]
            [cerber.oauth2.context :as ctx]
            [ring.util
             [anti-forgery :refer [anti-forgery-field]]
             [response :as response]]
            [selmer.parser :as selmer]))

(defn default-landing-url []
  (get-in app-config [:cerber :landing-url] "/"))

(defn default-authentication-endpoint []
  (get-in app-config [:cerber :enpoints :authentication] "/login"))

(defn default-approve-endpoint []
  (get-in app-config [:cerber :enpoints :client-approve] "/approve"))

(defn default-refuse-endpoint []
  (get-in app-config [:cerber :enpoints :client-refuse] "/refuse"))

(defn render-form [file kv]
  (-> (selmer/render-file file kv)
      (response/response)
      (response/header "Content-Type" "text/html")))

(defn render-login-form [req]
  (let [session (:session req)]
    (-> (render-form "forms/login.html" {:csrf (anti-forgery-field)
                                         :action (default-authentication-endpoint)
                                         :failed? (boolean (:failed? req))})

        ;; clear up auth info if already existed
        (assoc :session (dissoc session :login)))))

(defn render-approval-form [client req]
  (render-form "forms/authorize.html" {:csrf (anti-forgery-field)
                                       :client client
                                       :action-approve (str (default-approve-endpoint) "?" (:query-string req))
                                       :action-refuse  (str (default-refuse-endpoint) "?" (:query-string req))}))

(defn handle-login-submit [req]
  (let [result (ctx/user-password-valid? req)
        landing (or (get-in req [:session :landing-url]) (default-landing-url))]
    (if (f/failed? result)
      (-> (assoc req :failed? true)
          (render-login-form))
      (-> (response/redirect landing)
          (assoc :session {:login (-> result ::ctx/user :login)})))))
