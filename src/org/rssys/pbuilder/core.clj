(ns org.rssys.pbuilder.core
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
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
  (println "Project builder is a build tool for Clojure projects with tools.deps\n")
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
      "release" (r/run-release config (-> opts :options :file))
      "bump" (r/bump-version-file config (-> opts :options :file) (second (:arguments opts)))   ;; should be one of: major minor patch alpha beta rc qualifier
      (do (println "error: no parameters.")
          (println "valid parameters: clean, javac, compile, jar, uberjar, install, deploy, conflicts, standalone, release, bump."))))

  (flush)
  (System/exit 0))

(comment
  (def config (p/build-config "pbuild.edn")))
