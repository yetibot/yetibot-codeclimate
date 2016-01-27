(ns yetibot-codeclimate.plugins.routes.codeclimate
  (:require
    [taoensso.timbre :refer [info warn]]
    [compojure.core :refer [GET POST defroutes]]))

(defn handler [{:keys [] :as params} req]
  (info params req)
  "codeclimate")

(defroutes routes
  (GET "/codeclimate/callback" [& params :as req] (handler params req)))
