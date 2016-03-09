(defproject yetibot-codeclimate "0.1.8"
  :description "Integration with CodeClimate"
  :url "https://github.com/devth/yetibot-codeclimate"
  :lein-release {:deploy-via :clojars}
  :deploy-repositories [["releases" :clojars]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns yetibot.core.repl
                 :timeout 120000
                 :welcome (println "Welcome to the Yetibot CodeClimate development repl!")}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [yetibot.core "0.3.15"]
                 [me.raynes/conch "0.8.0"]])
