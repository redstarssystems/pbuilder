(ns org.rssys.pbuilder.util
  (:require [clojure.edn :as edn]))

(def build-config-filename "pbuild.edn")

;; these functions allow you to detect current version of your project

(defmacro project-version
  "# Get current project version
   * Returns:
  	  _String_ - current version of project."
  []
  (-> build-config-filename slurp edn/read-string :artifact-version))

(def ^:const version (project-version))
