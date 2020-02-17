(ns org.rssys.pbuilder.util
  (:require [clojure.edn :as edn]))

(def build-config-filename "pbuild.edn")

(defmacro project-version
  "# Get current project version
   * Returns:
  	  _String_ - current version of project."
  []
  (-> build-config-filename slurp edn/read-string :artifact-version))

(def ^:const version (project-version))
