(ns yetibot-codeclimate.plugins.commands.codeclimate
  (:require
    [clojure.string :refer [join]]
    [yetibot.core.hooks :refer [cmd-hook]]))

(defn codeclimate-cmd
  "codeclimate # TODO"
  [_]
  "WIP")

(cmd-hook #"codeclimate"
  _ codeclimate-cmd)
