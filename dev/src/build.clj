(ns build
  (:require [badigeon.clean :as clean]
            [badigeon.classpath :as classpath]
            [badigeon.javac :as javac]
            [badigeon.compile :as compile]
            [badigeon.jar :as jar]
            [badigeon.install :as install]
            [badigeon.prompt :as prompt]
            [badigeon.sign :as sign]
            [badigeon.deploy :as deploy]
            [badigeon.bundle :as bundle]
            [badigeon.uberjar :as uberjar]
            [badigeon.zip :as zip]
            [badigeon.pom :as pom]
    ;; Requires a JDK 9+
            [badigeon.jlink :as jlink]
            [badigeon.war :as war]
            [badigeon.exec :as exec]
            [clojure.tools.deps.alpha.reader :as deps-reader]
            [clojure.tools.deps.alpha.util.maven :as maven]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.util.jar JarEntry JarOutputStream)
           (java.nio.file Path Paths)
           (java.net URI)))


(def deps-content (deps-reader/slurp-deps "deps.edn"))
(def java-src-folder (-> deps-content :build :java-source-paths))
(def javac-options (-> deps-content :build :javac-options))
(def group-id (-> deps-content :build :group-id))
(def artifact-id (-> deps-content :build :artifact-id))
(def group-artefact-id (symbol (str group-id "/" artifact-id)))
(def artifact-version (-> deps-content :build :artifact-version))
(def uberjar-filename (-> deps-content :build :uberjar-name))
(def jar-name (str "target/" artifact-id "-" artifact-version ".jar"))
(def main-ns (symbol (-> deps-content :build :main)))
(def omit-source? (-> deps-content :build :omit-source))

(defn clean
  []
  (clean/clean "target"
    {;; By default, clean does not allow deleting folders outside the target directory,
     ;; unless :allow-outside-target? is true
     :allow-outside-target? false}))

(defn make-classpath
  []
  ;; Builds a classpath by using the provided deps spec or, by default, the deps.edn file of the current project.
  ;; Returns the built classpath as a string.

  (classpath/make-classpath))

(defn compile-java
  ([emit-path]
   ;; Compile java sources under the src-java directory
   (javac/javac java-src-folder {;; Emit class files to the target/classes directory
                                 :compile-path  emit-path
                                 ;; Additional options used by the javac command
                                 :javac-options javac-options}))
  ([]
   (compile-java "target/classes")))

(defn compile-clj
  ([classes-folder]
   (compile/compile [main-ns]
     {;; Emit class files to the classes-folder (default target/classes) directory
      :compile-path     classes-folder
      ;; Compiler options used by the clojure.core/compile function
      :compiler-options {:disable-locals-clearing false
                         :elide-meta              [:doc :file :line :added]
                         :direct-linking          true}
      ;; The classpath used during AOT compilation is built using the deps.edn file
      :classpath        (classpath/make-classpath {:aliases []})}))
  ([]
   (compile-clj "target/classes")))

(defn- copy-pom-properties [out-path group-id artifact-id pom-properties]
  (let [path (format "%s/META-INF/maven/%s/%s/pom.properties"
               out-path group-id artifact-id)]
    (.mkdirs (.toFile (.getParent ^Path (Paths/get path (into-array String "")))))
    (io/copy pom-properties (io/file path))))

(defn- copy-pom [out-path group-id artifact-id pom-file]
  (let [path (format "%s/META-INF/maven/%s/%s/pom.xml"
               out-path group-id artifact-id)]
    (.mkdirs (.toFile (.getParent ^Path (Paths/get path (into-array String "")))))
    (io/copy (io/file pom-file) (io/file path))))

(defn make-pom
  []
  (when (.exists (io/file "pom.xml"))
    (io/delete-file "pom.xml"))
  (let [lib (symbol group-artefact-id)
        maven-coords {:mvn/version artifact-version}]
    (pom/sync-pom lib maven-coords (-> (deps-reader/slurp-deps "deps.edn")))))

(defn jar
  []
  ;; Package the project into a jar file
  (jar/jar group-artefact-id {:mvn/version artifact-version}
    {;; The jar file produced.
     :out-path                jar-name
     ;; Adds a \"Main\" entry to the jar manifest with the value
     :main                    main-ns
     ;; Additional key/value pairs to add to the jar manifest. If a value is a collection, a manifest section is built for it.
     ;;:manifest                {"Project-awesome-level" "super-great"
     ;;                          :my-section-1           [["MyKey1" "MyValue1"] ["MyKey2" "MyValue2"]]
     ;;                          :my-section-2           {"MyKey3" "MyValue3" "MyKey4" "MyValue4"}}

     ;; By default Badigeon add entries for all files in the directory listed in the
     ;; :paths section of the deps.edn file. This can be overridden here.
     :paths                   ["src" "target/classes"]

     ;; The dependencies to be added to the \"dependencies\" section of the pom.xml file.
     ;; When not specified, defaults to the :deps entry of the deps.edn file, without
     ;; merging the user-level and system-level deps.edn files
     :deps                    '{org.clojure/clojure {:mvn/version "1.10.1"}}
     ;; The repositories to be added to the \"repositories\" section of the pom.xml file.
     ;; When not specified, default to nil - even if the deps.edn files contains
     ;; a :mvn/repos entry.
     :mvn/repos               '{"clojars" {:url "https://repo.clojars.org/"}}
     ;; A predicate used to excludes files from beeing added to the jar.
     ;; The predicate is a function of two parameters: The path of the directory
     ;; beeing visited (among the :paths of the project) and the path of the file
     ;; beeing visited under this directory.
     :exclusion-predicate     badigeon.jar/default-exclusion-predicate
     ;; A function to add files to the jar that would otherwise not have been added to it.
     ;; The function must take two parameters: the path of the root directory of the
     ;; project and the file being visited under this directory. When the function
     ;; returns a falsy value, the file is not added to the jar. Otherwise the function
     ;; must return a string which represents the path within the jar where the file
     ;; is copied.
     :inclusion-path          (partial badigeon.jar/default-inclusion-path "badigeon" "badigeon")
     ;; By default git and local dependencies are not allowed. Set allow-all-dependencies? to true to allow them
     :allow-all-dependencies? true}))

(defn uberjar []

  ;; compile Java sources if present
  (when java-src-folder (compile-java))

  (compile-clj)
  (make-pom)
  (let [pom-props (pom/make-pom-properties (symbol group-artefact-id) {:mvn/version artifact-version})
        ;; Automatically compute the bundle directory name based on the application name and version.
        out-path (badigeon.bundle/make-out-path (symbol artifact-id) (str artifact-version "-standalone"))]
    (uberjar/bundle out-path
      {;; A map with the same format than deps.edn. :deps-map is used to resolve the project resources.
       :deps-map                    (deps-reader/slurp-deps "deps.edn")
       ;; Alias keywords used while resolving the project resources and its dependencies. Default to no alias.
       :aliases                     []
       ;; The dependencies to be excluded from the produced bundle.
       ;;:excluded-libs #{'org.clojure/clojure}
       ;; Set to true to allow local dependencies and snapshot versions of maven dependencies.
       :allow-unstable-deps?        true
       ;; When set to true and resource conflicts are found, then a warning is printed to *err*
       :warn-on-resource-conflicts? true})

    (copy-pom-properties out-path  group-id artifact-id pom-props)
    (copy-pom out-path group-id artifact-id "pom.xml")

    ;; Recursively walk the bundle files and delete all the Clojure source files
    (when omit-source?
      (uberjar/walk-directory
       out-path
       (fn [dir f] (when (.endsWith (str f) ".clj")
                     (java.nio.file.Files/delete f)))))
    ;; Output a MANIFEST.MF file defining 'badigeon.main as the main namespace
    (spit (str (badigeon.utils/make-path out-path "META-INF/MANIFEST.MF"))
      (jar/make-manifest main-ns
        {:Group-Id         group-id
         :Artifact-Id      artifact-id
         :Artifact-Version artifact-version}))
    ;; Return the paths of all the resource conflicts (multiple resources with the same path) found on the classpath.
    (uberjar/find-resource-conflicts {:deps-map (deps-reader/slurp-deps "deps.edn")
                                      ;; Alias keywords used while resolving the project resources and its dependencies. Default to no alias.
                                      ;; :aliases [:1.7 :bench :test]
                                      })
    ;; Zip the bundle into an uberjar
    (zip/zip out-path (if uberjar-filename (str "target/" uberjar-filename) (str out-path ".jar")))))

(defn standalone
  []
  ;; compile Java sources if present
  (when java-src-folder (compile-java))

  (compile-clj)
  ;; Make a standalone bundle of the application.
  (let [;; Automatically compute the bundle directory name based on the application name and version.
        out-path (badigeon.bundle/make-out-path (symbol artifact-id) artifact-version)]
    (badigeon.bundle/bundle out-path
      {;; A map with the same format than deps.edn. :deps-map is used to resolve the project dependencies.
       :deps-map             (deps-reader/slurp-deps "deps.edn")

       ;; Alias keywords used while resolving the project resources and its dependencies. Default to no alias.
       ;;:aliases              [:1.7 :bench :test]

       ;; The dependencies to be excluded from the produced bundle.
       ;; :excluded-libs        #{'org.clojure/clojure}

       ;; Set to true to allow local dependencies and snapshot versions of maven dependencies.
       :allow-unstable-deps? true
       ;; The path of the folder where dependencies are copied, relative to the output folder.
       :libs-path            "lib"})
    ;; Extract native dependencies (.so, .dylib, .dll, .a, .lib, .scx files) from jar dependencies.
    (bundle/extract-native-dependencies out-path
      {;; A map with the same format than deps.edn. :deps-map is used to resolve the project dependencies.
       :deps-map             (deps-reader/slurp-deps "deps.edn")
       ;; Alias keywords used while resolving dependencies. Default to no alias.
       ;; :aliases              [:1.7 :bench :test]

       ;; Set to true to allow local dependencies and snapshot versions of maven dependencies.
       :allow-unstable-deps? true
       ;; The directory where native dependencies are copied.
       :native-path          "lib"
       ;; The paths where native dependencies should be searched.
       ;; The native-prefix is excluded from the output path of the native dependency.
       ;;:native-prefixes      {'org.lwjgl.lwjgl/lwjgl-platform "/"}

       ;; A collection of native extension regexp.
       ;; Files which name match one of these regexps are considered a native dependency.
       ;; Default to badigeon.bundle/native-extensions.
       ;; :native-extensions    #{#"\.so$"}

       })

    ;; Extract native dependencies (.so, .dylib, .dll, .a, .lib, .scx files) from a jar file
    #_(bundle/extract-native-dependencies-from-file
        out-path
        ;; The path of the jar file the native dependencies are extracted from
        (str (System/getProperty "user.home")
          "/.m2/repository/overtone/scsynth/3.10.2/scsynth-3.10.2.jar")

        {;; The directory where native dependencies are copied.
         :native-path   "lib"
         ;; The path where native dependencies should be searched.
         ;; The native-prefix is excluded from the output path of the native dependency.
         :native-prefix "/"

         ;; A collection of native extension regexp.
         ;; Files which name match one of these regexps are considered a native dependency.
         ;; Default to badigeon.bundle/native-extensions.
         ;; :native-extensions #{#"\.so$"}
         })

    ;; Requires a JDK9+
    ;; Embeds a custom JRE runtime into the bundle.
    (jlink/jlink out-path {;; The folder where the custom JRE is output, relative to the out-path.
                           :jlink-path    "runtime"
                           ;; The path where the java module are searched for.
                           :module-path   (str (System/getProperty "java.home") "/jmods")
                           ;; The modules to be used when creating the custom JRE
                           :modules       ["java.base" "java.xml" "java.desktop" "java.management" "java.logging"]
                           ;; The options of the jlink command
                           :jlink-options ["--strip-debug" "--no-man-pages"
                                           "--no-header-files" "--compress=2"]})

    ;; Create a start script for the application
    (bundle/bin-script out-path main-ns
      {;; Specify which OS type the line breaks/separators/file extensions should be formatted for.
       :os-type       bundle/posix-like
       ;; The path script is written to, relative to the out-path.
       :script-path   "bin/run.sh"
       ;; A header prefixed to the script content.
       :script-header "#!/bin/sh\n\n"
       ;; The java binary path used to start the application. Default to \"java\" or \"runtime/bin/java\" when a custom JRE runtime is found under the run directory.
       :command       "runtime/bin/java"
       ;; The classpath option used by the java command.
       :classpath     ".:./lib/*"
       ;; JVM options given to the java command.
       :jvm-opts      ["-Xmx1g"]
       ;; Parameters given to the application main method.
       :args          [""]})

    ;; Recursively walk the bundle files and delete all the Clojure source files
    (when omit-source?
      (uberjar/walk-directory
       (str out-path "/" group-id)
       (fn [dir f] (when (.endsWith (str f) ".clj")
                     (java.nio.file.Files/delete f)))))

    (exec/exec "chmod" {:proc-args ["+x" (format "%s/bin/run.sh" out-path)]})
    ;; Zip the bundle
    (exec/exec "tar" {:proc-args ["cfz" (str out-path ".tar.gz") "-C" "target" (str artifact-id "-" artifact-version)]})
    ;; zip/zip doesn't preserve file attributes
    #_(zip/zip out-path (str out-path ".zip"))))

(comment
  (def cp (make-classpath))
  (clean)
  (compile-java)
  (compile-clj)
  (extract-classes-from-deps)
  (jar)
  (standalone))

