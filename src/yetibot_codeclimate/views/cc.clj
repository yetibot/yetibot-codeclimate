(ns yetibot-codeclimate.views.cc
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [error info warn]]
    [hiccup.page :refer [include-css]]
    [yetibot-codeclimate.analyze :refer [run-codeclimate! get-analysis!]]
    [hiccup.element :refer [link-to image]]
    [yetibot.core.webapp.views.common :refer [layout]]
    [ring.util.http-response :refer [ok]]
    [hiccup.element :refer [link-to image]]))

(defn fmt-lines [start-line lines]
  (s/join
    \newline
    (map-indexed (fn [idx line] (str (+ start-line idx) ": " line)) lines)))

(defn show-cc [owner repo sha]
  (let [cc-json (get-analysis! owner repo sha)]
    (layout
      "CodeClimate"
      (include-css "/codeclimate/main.css" )
      [:div.main.container-fluid
       [:h1 "Yetibot CodeClimate "]
       [:h6.repo-info [:small.lead (str owner "/" repo " at " sha)]]

       (if cc-json

         (if (empty? cc-json)
           [:div.cc-item.success
            [:div.cc-content "Looks good, no problems detected!"]]

           [:div
            [:p "Found "
             (let [c (count cc-json)]
               (str c " " (if (> 1 c) "issues" "issue") "."))]

            (doall (for [{:keys [lines engine_name content categories description location]} cc-json]
               (let [line-number (-> location :positions :begin :line)
                     column-number (-> location :positions :begin :column)]
                 [:div.cc-item
                  [:div.cc-content
                   [:div.row
                    [:div.col-md-2
                     [:span.label.label-default
                      {:title (:body content)  :data-toggle "tooltip"}
                      engine_name]
                     " "
                     [:span.label.label-warning
                      (s/join " " (map s/lower-case categories))] ]

                    [:div.col-md-10
                     [:p description]
                     [:pre
                      (when-let [{:keys [before after line]} lines]
                        (let [before-count (count before)
                              after-count (count after)]

                          [:span
                           (fmt-lines (- line-number before-count) before)
                           \newline
                           [:mark line-number ": " line \newline]
                           (fmt-lines (inc line-number) after)]))

                      [:div.file-location
                       (str
                         (:path location)
                         (when line-number (str " line " line-number))
                         (when column-number (str " column " column-number)))
                       ]]]

                    ]]])))
            ])


         ;; file doesn't exist yet
         [:div.cc-item.pending
          [:div.cc-content
           "No analysis found. Refresh at will."]])

       [:iframe
        {:height "30px",
         :width "160px",
         :scrolling "0",
         :frameborder "0",
         :src
         "https://ghbtns.com/github-btn.html?user=devth&repo=yetibot-codeclimate&type=star&count=true&size=large"}]

       [:div.row
        [:div.col-lg-4
         [:p.small "Feedback? "
          (link-to "https://github.com/devth/yetibot-codeclimate/issues"
                   "Open an issue")]
         ]]

       ]


      )))

(defn list-cc [owner repo]
  "Ok")
