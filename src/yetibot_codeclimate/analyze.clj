(ns yetibot-codeclimate.analyze
  (:require
    [yetibot.core.config :as c]
    [cheshire.core :as json]
    [clojure.walk :refer [stringify-keys keywordize-keys]]
    [taoensso.timbre :refer [error info warn]]
    [clojure.string :as s]
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
  (let [cc-str (json/generate-string cc)
        dir (str (results-path) "/" owner "/" repo-name)
        file (str dir "/" sha ".json")]
    (mkdir "-p" dir)
    (spit file cc-str)))

(defn get-analysis!
  [owner repo-name sha]
  (let [dir (str (results-path) "/" owner "/" repo-name)
        file (str dir "/" sha ".json")]
    (map keywordize-keys (json/parse-string (slurp file)))))

(defn read-nth-line
  "Read line-number from the given text file. The first line has the number 1."
  [file line-number]
  (with-open [rdr (clojure.java.io/reader file)]
    (nth (line-seq rdr) (dec line-number))))

(defn annotate-cc-with-lines
  "Looks up the line of code in files referenced by CC analysis and inserts it"
  [owner repo-name sha cc]
  (let [ws (workspace repo-name sha)]
    (for [{{{{:keys [line]} :begin} :positions path :path} :location :as item} cc]
      (if line
        (do
          (info "annotate:" line path)
          (let [fullpath (str ws "/" path)
                line (read-nth-line fullpath line)]
            (info fullpath)
            (assoc item :line line)))
        (do
          (info "skip annotate:" line path item)
          item)
        ))))

(defn run-codeclimate! [sha owner repo-name git-url]
  (try
    (let [ws (workspace repo-name sha)]
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
            cc-annotated (->> cc-docker-output
                              json/parse-string
                              keywordize-keys
                              (annotate-cc-with-lines owner repo-name sha))
            cc-err (sh/stream-to-string d :err)]
        (store-analysis! owner repo-name sha cc-annotated)
        (info "CodeClimate out: " cc-docker-output)
        (info "CodeClimate err:" cc-err)
        (cleanup! sha repo-name)
        (if (s/blank? cc-err)
          cc-annotated
          {:error cc-err})

        cc-annotated))
    (catch Exception e
      (error "Error running code climate:" e)
      {:error (.getMessage e)})))
