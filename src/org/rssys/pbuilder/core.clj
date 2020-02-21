(ns org.rssys.pbuilder.core
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [org.rssys.pbuilder.util :as u]
            [org.rssys.pbuilder.process :as p]))

(def arguments ["help" "jar" "uberjar" "install" "deploy" "clean"])

(def cli-options
  ;; An option with a required argument
  [["-f" "--file BUILDFILE" "Config for build "
    :default "pbuild.edn"]
   ["-h" "--help"]])


(defn -main
  "entry point to program."
  [& args]
  (println "Project builder – is a build tool for Clojure projects" u/version)
 (let [opts (parse-opts args cli-options)
       config (p/build-config (-> opts :options :file))]
   (case (-> opts :arguments first)
     "help" (help)
     "jar" (p/build-jar config)))

  (System/exit 0))
