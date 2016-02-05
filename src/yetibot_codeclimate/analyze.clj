(ns yetibot-codeclimate.analyze
  (:require
    [yetibot.core.config :as c]
    [cheshire.core :as json]
    [clojure.walk :refer [stringify-keys keywordize-keys]]
    [taoensso.timbre :refer [error info warn]]
    [clojure.string :as s]
    [cemerick.url :refer [url]]
    [me.raynes.conch :refer [programs]]
    [me.raynes.conch.low-level :as sh]))

(defn cc-config [] (c/get-config :yetibot-codeclimate))

(defn gh-config [] (c/get-config :yetibot :api :github))

(defn docker-config [] (c/get-config :yetibot :docker))

(defn workspace-path [] (or (:workspace-path (cc-config) "/tmp/cc")))

(defn results-path [] (or (:results-path (cc-config) "./codeclimate")))

(defn workspace [repo-name uid] (str (workspace-path) "/" repo-name "-" uid))

(defn docker-machine-config [] (or (:machine (docker-config)) {}))

;;

(defn in-dir [dir & cmds]
  (let [r (apply sh/proc (conj (vec cmds) :dir dir))
        err (sh/stream-to-string r :err)
        out (sh/stream-to-string r :out)]
    (info (s/join " " cmds))
    (when-not (s/blank? err) (warn  "produced:" err))
    (info out)
    out))

(programs docker mkdir rm)

(defn init-repo! [sha repo-name git-url]
  (let [ws (workspace repo-name sha)]
    (info "init-repo!" ws)
    (let [t (:token (gh-config))
          repo-url (s/replace git-url #"\/\/" (str "//" t "@"))]
      ;; (in-dir ws "git" "archive" "--remote" repo-url "--format" "zip" "--output" repo-name sha)
      ;; (in-dir ws "unzip" repo-name)
      (info "mkdir " ws (mkdir "-p" ws))
      (in-dir ws "git" "clone" repo-url ws)
      (in-dir ws "git" "reset" "--hard" sha)
      #_(in-dir ws "ls" "-a")
      (rm "-rf" (str ws "/.git")))))

(defn cleanup! [uid repo-name]
  (sh/proc "rm" "-rf" (workspace repo-name uid)))

(defn store-analysis!
  "Write analysis json to disk"
  [owner repo-name sha cc]
  (info "store analysis!" owner repo-name sha)
  (let [cc-str (json/generate-string cc)
        dir (str (results-path) "/" owner "/" repo-name)
        file (str dir "/" sha ".json")]
    (mkdir "-p" dir)
    (spit file cc-str)))

(defn get-analysis!
  "Returns null if analysis doesn't exist yet"
  [owner repo-name sha]
  (try
    (let [dir (str (results-path) "/" owner "/" repo-name)
          file (str dir "/" sha ".json")]
      (keywordize-keys (json/parse-string (slurp file))))
    (catch Exception e nil)))

(defn read-nth-line-with-surrounding
  "Read line-number from the given text file along with surrounding lines
   according to `surrouding-count`.
   Returns:
     {:before [lines]
      :line line
      :after [line]}"
  [file line-number surrounding-count]
  (let [start-line (max 1 (- line-number surrounding-count))
        before-size (- line-number start-line)
        take-count (+ before-size 1 surrounding-count)]
    (with-open [rdr (clojure.java.io/reader file)]
      (let [lines (doall (take take-count (drop (dec start-line) (line-seq rdr))))
            before (take (- line-number start-line) lines)
            line (nth lines (- line-number start-line))
            after (take surrounding-count (drop (inc before-size) lines))]
        {:before before :line line :after after}))))

(defn annotate-cc-with-lines
  "Looks up the line of code in files referenced by CC analysis and inserts it"
  [owner repo-name sha cc]
  (let [ws (workspace repo-name sha)]
    (for [{{{{:keys [line]} :begin} :positions path :path} :location :as item} cc]
      (if line
        (do
          (info "annotate:" line path)
          (let [fullpath (str ws "/" path)
                lines (read-nth-line-with-surrounding fullpath line 2)]
            (info fullpath)
            (assoc item :lines lines)))
        (do
          (info "skip annotate:" line path item)
          item)))))

(defn extract-base-url [u]
  (let [u (url u)]
    (str (:protocol u) "://" (:host u))))

(defn str-to-json [s]
  (try
    (json/parse-string s)
    (catch Exception e
      (error "Error parsing json" e)
      (info s)
      (throw e))))

;; clojure error handling sucks
(defn run-codeclimate! [sha owner repo-name git-url commit-url]
  (let [ws (workspace repo-name sha)
        cc (try
             (cleanup! sha repo-name)
             (info (init-repo! sha repo-name git-url))
             ; run codeclimate via docker
             (let [d (sh/proc "docker" "run"
                              "--interactive" "--rm"
                              "--env" (str "CODE_PATH=" ws)
                              "--volume"  (str ws ":/code")
                              "--volume" "/var/run/docker.sock:/var/run/docker.sock"
                              "--volume" "/tmp/cc:/tmp/cc"
                              "codeclimate/codeclimate"
                              "analyze" "-f" "json"
                              :env (stringify-keys (docker-machine-config)))
                   cc-docker-output (sh/stream-to-string d :out)
                   cc-json (str-to-json cc-docker-output)
                   cc-annotated (->> cc-json
                                     keywordize-keys
                                     (annotate-cc-with-lines owner repo-name sha))
                   cc-err (sh/stream-to-string d :err)]
               #_(info "CodeClimate out: " cc-docker-output)
               (dorun cc-annotated) ;; do all the work before we cleanup
               (when-not (s/blank? cc-err) (info "CodeClimate err:" cc-err))
               (cleanup! sha repo-name)
               (if (s/blank? cc-err)
                 cc-annotated
                 {:error cc-err}))
             (catch Exception e
               (error "Error running code climate:" e)
               {:error (.getMessage e)}))
        results {:commit-url commit-url
                 :base-url (extract-base-url commit-url)
                 :sha sha
                 :owner owner
                 :repo repo-name
                 :analysis (if (:error cc) :error cc)}]
    (info "store analysis")
    (try
      (store-analysis! owner repo-name sha results)
      (catch Exception e
        (error "Error storing analysis" e)
        {:error (.getMessage e)}))
    cc))
