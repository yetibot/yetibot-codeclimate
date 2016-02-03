(ns yetibot-codeclimate.plugins.routes.codeclimate
  (:require
    [yetibot.core.webapp.views.common :refer [layout]]
    [yetibot-codeclimate.github :as gh]
    [yetibot-codeclimate.views.cc :as views]
    [yetibot.core.commands.url :refer [yetibot-url]]
    [taoensso.timbre :refer [info warn]]
    [cheshire.core :as json]
    [yetibot-codeclimate.analyze :refer [run-codeclimate! get-analysis!]]
    [clojure.walk :refer [keywordize-keys]]
    [compojure.core :refer [GET POST defroutes]]))

(def cc-context "Yetibot CodeClimate")

(defn cc-url [owner repo sha]
  (yetibot-url "codeclimate" owner repo sha))

(defn handle-push-event [payload]
  (let [repo (:repository payload)
        repo-name (:name repo)
        {:keys [owner clone_url]} repo
        ;; todo verify owner name is correct when using an organization
        owner-name (:name owner)
        commits (:commits payload)]
    (info "github payload keys:" (keys payload))
    (info owner-name repo-name)
    (dorun
      ;; analyze each commit in parallel
      (for [commit commits]
        (future
          (let [sha (:id commit)
                target-url (cc-url owner-name repo-name sha)
                create-status (partial gh/create-status owner-name repo-name sha)
                cs-opts {:context cc-context :target_url target-url}]
            (info "create status")
            (create-status (merge cs-opts {:state "pending"
                                           :description "Yetibot is analyzing your code with CodeClimate"}))
            (let [cc-results (run-codeclimate! sha owner-name repo-name (:clone_url repo))]
              ;; using the cc-results post the analysis status to gh
              (cond
                ;; error
                (:error cc-results) (create-status
                                      (merge cs-opts {:state "error"
                                                      :description (:error cc-results)}))
                ;; success
                (empty? cc-results) (create-status
                                      (merge cs-opts {:state "success"
                                                      :description "Yetibot approved!"}))
                ;; failure
                :else (create-status
                        (merge cs-opts {:state "failure"
                                        :description "Yetibot detected linting problems from CodeClimate"}))
                ))))))
    "Analyzing"))

(def handled-events {"push" #'handle-push-event})

(defn handler [{:keys [] :as params} req]
  (let [event-type (-> (keywordize-keys req) :headers :x-github-event)]
    ;; listen for the right type of expected events
    (if-let [event-handler (handled-events event-type)]
      (let [payload (keywordize-keys params)]
        (info "Handling event type:" event-type)
        (event-handler payload))
      (do
        (info "Skipping unhandled event type:" event-type)
        "Skipped"))))

(defroutes routes
  (POST "/codeclimate/webhook" [& params :as req] (handler params req))
  (GET "/codeclimate/:owner/:repo" [owner repo] (views/list-cc owner repo))
  (GET "/codeclimate/:owner/:repo/:sha" [owner repo sha] (views/show-cc owner repo sha)))
