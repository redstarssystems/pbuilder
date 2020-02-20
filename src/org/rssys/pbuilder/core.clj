(ns org.rssys.pbuilder.core
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [org.rssys.pbuilder.util :as u]
            [org.rssys.pbuilder.process :as p]))

(def cli-options
  ;; An option with a required argument
  [["-f" "--file BUILDFILE" "Config for build "
    :default "pbuild.edn"]
   ["-h" "--help"]])

(defn help
  []
  (let [cmds ["help" "jar" "uberjar" "clean"]
        cmd-str (apply str (interpose "\n\t" cmds))]
    (printf "usage: pbuilder <command> [configfile (default:pbuild.edn)]\ncommand:\n\t%s" cmd-str))
  (flush))

(defn -main
  "entry point to program."
  [& args]
  (println "Project builder – is a build tool for Clojure projects" u/version)
  (println (parse-opts args cli-options))
 #_(let [config (if-let [c ()] "pbuild.edn")] (case (first args)
     "help" (help)
     "jar" (p/build-jar config)))

  (System/exit 0))
