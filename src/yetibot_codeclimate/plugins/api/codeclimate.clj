(ns yetibot-codeclimate.plugins.api.codeclimate
  (:require
    [tentacles
     [search :as search]
     [pulls :as pulls]
     [issues :as issues]
     [users :as u]
     [repos :as r]
     [events :as e]
     [data :as data]
     [orgs :as o]]
    [yetibot.core.config :as c]))

(defn config []
  (let [conf (c/get-config :yetibot :api :github)]
    ; propogate the configured endpoint to the tentacles library
    (alter-var-root #'tentacles.core/url (partial :endpoint conf))))

(defn endpoint [] (or (:endpoint (config)) "https://api.github.com/"))

(comment
  (require 'yetibot.core.webapp.route-loader)
  (def r (yetibot.core.webapp.route-loader/load-plugin-routes))

  (map #(ns-resolve % 'routes) r)

  yetibot.core.config
  )

