(ns org.rssys.pbuilder.core
  (:gen-class)
  (:require [org.rssys.pbuilder.util :as u]))

(defn -main
  "entry point to program."
  [& args]
  (println "Project builder – is a build tool for Clojure projects" u/version)

  (System/exit 0))
