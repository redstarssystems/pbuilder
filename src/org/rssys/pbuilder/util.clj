(ns org.rssys.pbuilder.util
  (:require [aero.core :as aero]))

(def build-config-filename "pbuild.edn")


(defn read-config
  "Reads config from EDN file."
  ([]
   (read-config build-config-filename))

  ([config-name]
   (aero/read-config config-name)))


;; these functions allow you to detect current version of your project

(defmacro project-version
  "# Get current project version
   * Returns:
  	  _String_ - current version of project."
  []
  (-> (read-config) :artifact-version))

(def ^:const version (project-version))
