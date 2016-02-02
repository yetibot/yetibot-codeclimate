(ns yetibot-codeclimate.github
  (:require
    [taoensso.timbre :refer [info warn]]
    [tentacles
     [core :as tc]
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
    (alter-var-root #'tentacles.core/url (partial :endpoint conf))
    conf))

(defn endpoint [] (or (:endpoint (config)) "https://api.github.com/"))

(defn auth [] {:oauth-token (:token (config))})

(defn create-status [owner repo-name sha opts]
  (info "create-status" owner repo-name sha opts)
  (r/create-status owner repo-name sha (merge (auth) opts)))
