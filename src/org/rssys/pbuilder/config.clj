(ns org.rssys.pbuilder.config
  "Configuration processing & spec for config file"
  (:require [malli.core :as m]
            [malli.generator :as mg]
            [malli.error :as me]
            [clojure.java.io :as io]
            [malli.util :as mu]))

(defn folder-exist? [^String folder] (-> folder io/file .isDirectory))

(def Java-src-folder [:and {:title       :java-src-folder
                            :description "Existing folder with Java source files"}
                      :string
                      [:fn {:error/message "Folder with Java sources should exist"} folder-exist?]])


(def Javac-options
  [:vector {:title       :javac-options
            :description "Options for Java compiler (javac)"}
   string?])


(def Warn-on-resource-conflicts?
  [:and {:title       :warn-on-resource-conflicts?
         :description "Check if multiple jars contains the same resources/classes"}
   boolean?])


(def Deploy-signed?
  [:and {:title       :deploy-signed?
         :description "Sign artifact before deploy to repository. "}
   boolean?])


(defn string-is-url? [s] (try (io/as-url s) true (catch Throwable t false)))


(def Deploy-repo
  [:and {:title       :deploy-repo
         :description "Remote repository coordinates: id and url. "}
   [:map
    [:id string?]
    [:url [:fn {:error/message "Should be valid URL"} string-is-url?]]]])


(def Config
  [:map
   [:java-src-folder {:optional true} Java-src-folder]
   [:javac-options {:optional true  :default ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]} Javac-options]
   [:warn-on-resource-conflicts? {:default true} Warn-on-resource-conflicts?]
   [:deploy-signed? {:default true :optional true} Deploy-signed?]
   [:deploy-repo {:default {:id "clojars" :url "https://clojars.org/repo"} :optional true} Deploy-repo]])

(comment

  (m/properties Javac-options)
  (m/validate Java-src-folder "src")
  (m/explain Java-src-folder "abc")
  (me/humanize (m/explain Java-src-folder "abc"))

  (m/validate Javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"])
  (m/validate Javac-options [1 2 3])
  (m/explain Javac-options [1 2 3])

  (m/validate Warn-on-resource-conflicts? true)
  (m/validate Warn-on-resource-conflicts? 2)
  (m/explain Warn-on-resource-conflicts? 2)
  (me/humanize (m/explain Warn-on-resource-conflicts? 2))

  (m/validate Deploy-repo {:id "clojars" :url "https://clojars.org/repo"})
  (m/explain Deploy-repo {:id "clojars" :url "https://clojars.org/repo"})
  (me/humanize (m/explain Deploy-repo {:id "clojars" :url "2https://clojars.org/repo"}))



  (m/validate Config
    {
     :java-src-folder             "test-projects/javac/src/java"
     :javac-options               ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

     :warn-on-resource-conflicts? true

     :deploy-signed?              true
     :deploy-repo                 {:id "clojars" :url "https://clojars.org/repo"}
     :deploy-creds                :m2-settings              ;; :m2-settings or :password-prompt

     :target-folder               "target"
     :group-id                    "org.rssys"
     :artifact-id                 "javac-example"
     :artifact-version            "0.1.0"
     :main                        "org.rssys.java-exmpl.core"
     :omit-source?                true
     ;;:uberjar-filename            "pbuilder.jar"
     :description                 "Project builder is a build tool for Clojure projects with tools.deps."
     :url                         "https://github.com/redstarssystems/pbuilder.git"
     :scm                         {:url "https://github.com/redstarssystems/pbuilder.git"}
     :license                     {:name "EPL-2.0"
                                   :url  "https://www.eclipse.org/legal/epl-2.0/"}
     :excluded-libs               #{}                       ;; e.g #{ org.clojure/clojure my.org/lib01}
     ;;:standalone-run-script       "./my-custom-script.sh"
     ;;:manifest                    {"Multi-Release" "true"} ;; here you may override MANIFEST.MF
     ;; :jlink-options is used when building standalone bundle
     ;; :jlink-options               ["--strip-debug" "--no-man-pages" "--no-header-files" "--compress=2" "--add-modules" "java.sql"]
     })
  )
