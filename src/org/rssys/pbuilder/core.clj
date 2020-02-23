(ns org.rssys.pbuilder.core
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [org.rssys.pbuilder.util :as u]
            [org.rssys.pbuilder.process :as p]
            [org.rssys.pbuilder.release :as r]))

(def cli-options
  ;; An option with a required argument
  [["-f" "--file BUILDFILE" "Config for build "
    :default "pbuild.edn"]
   ["-h" "--help"]])


(defn -main
  "entry point to program."
  [& args]
  (println "Project builder – is a build tool for Clojure projects" u/version)
  (let [opts   (parse-opts args cli-options)
        config (p/build-config (-> opts :options :file))]
    (case (-> opts :arguments first)
      "clean" (p/clean config)
      "javac" (p/compile-java config)
      "compile" (p/compile-clj config)
      "jar" (p/build-jar config)
      "uberjar" (p/build-uberjar config)
      "install" (p/local-install-jar config)
      "deploy" (p/deploy-jar config)
      "conflicts" (p/print-conflict-details)
      "standalone" (p/build-standalone config)
      "release" (r/run-release config (-> opts :arguments second) (-> opts :options :file)) ;; major minor patch alpha beta rc qualifier release
      ))

  (System/exit 0))

(comment
  (def config (p/build-config "pbuild.edn")))
