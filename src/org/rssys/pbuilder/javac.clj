;;;;
;; MIT License
;;
;; Copyright (c) 2019-2020 Ilshat Sultanov
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.
;;;;

;;;;
;; Important!
;; Add the following dependency to the alias you are using:
;; org.clojure/tools.deps.alpha {:mvn/version "0.8.677"}
;;;;

;; code taken from https://gist.github.com/just-sultanov/e2f61734e22e2ed3981216ed9b880bc0

(ns org.rssys.pbuilder.javac
  "A simple java compiler for working with the Clojure CLI tools.

  Links:
    * https://clojure.org/guides/getting_started
    * https://clojure.org/guides/deps_and_cli"
  (:gen-class)
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.edn :as edn]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.maven :as mvn]
    [clojure.java.io :as io])
  (:import
    (java.util EnumSet)
    (java.io ByteArrayOutputStream)
    (javax.tools ToolProvider JavaCompiler)
    (java.nio.file.attribute BasicFileAttributes FileAttribute)
    (java.nio.file Path Paths
                   Files FileVisitor FileVisitResult
                   FileVisitOption FileSystemLoopException NoSuchFileException)))

(set! *warn-on-reflection* true)


(defn- parse-opts [x]
  (cond
    (string? x) (binding [*read-eval* false] (edn/read-string x))
    (sequential? x) x
    :else []))


(defn- merge-default-repos [repos]
  (merge mvn/standard-repos repos))


(defn- ^Path make-path [path & paths]
  (Paths/get (str path) (into-array String (map str paths))))


(defn- make-visitor [*paths visitor]
  (reify FileVisitor
    (postVisitDirectory [_ _ _] FileVisitResult/CONTINUE)
    (preVisitDirectory [_ _ _] FileVisitResult/CONTINUE)
    (visitFile [_ path attrs] (visitor *paths path attrs) FileVisitResult/CONTINUE)
    (visitFileFailed [_ _ ex]
      (cond
        (instance? FileSystemLoopException ex) FileVisitResult/SKIP_SUBTREE
        (instance? NoSuchFileException ex) FileVisitResult/SKIP_SUBTREE
        :else (throw ex)))))


(defn- java-file? [path ^BasicFileAttributes attrs]
  (and (.isRegularFile attrs)
    (.endsWith (str path) ".java")))


(defn- find-java-file [*paths path attrs]
  (when (java-file? path attrs)
    (swap! *paths conj path)))


(defn- make-classpath []
  (let [deps-map (-> (io/file "deps.edn")
                   deps/slurp-deps
                   (update :mvn/repos merge-default-repos))
        paths    (:paths deps-map)
        args-map {}]
    (-> deps-map
      (deps/resolve-deps args-map)
      (deps/make-classpath paths args-map))))


(defn- make-command [classpath compile-path compiler-options paths]
  (into `[~@classpath ~@compiler-options "-d" ~(str compile-path)]
    (map str paths)))


(defn- make-dirs! [target-path]
  (Files/createDirectories target-path (make-array FileAttribute 0)))


(defn- find-paths [source-path]
  (let [*paths (atom [])]
    (Files/walkFileTree source-path
      (EnumSet/of FileVisitOption/FOLLOW_LINKS) Integer/MAX_VALUE (make-visitor *paths find-java-file))
    @*paths))


(defn- user-defined? [compiler-options]
  (some #(= "-cp" %) compiler-options))


(defn- unwrap [baos]
  (let [res (str baos)]
    (when (pos? (.length res))
      res)))


(defn- print-some [baos]
  (some-> baos unwrap println))


(defn- compile [source-path target-path compiler-options]
  (if-some [compiler ^JavaCompiler (ToolProvider/getSystemJavaCompiler)]
    (let [source-path (make-path source-path)
          target-path (make-path target-path)
          classpath   (if (user-defined? compiler-options) compiler-options ["-cp" (make-classpath)])
          paths       (find-paths source-path)]
      (if (seq paths)
        (let [command (make-command classpath target-path compiler-options paths)
              out     (ByteArrayOutputStream.)
              err     (ByteArrayOutputStream.)]
          (make-dirs! target-path)
          (.run compiler nil out err (into-array String command))
          (println (format "\nProcessed %s files\n" (count paths)))
          (print-some out)
          (print-some err))
        (println "\nNot found files for processing\n")))
    (throw (ex-info "Can't find the java compiler"
             {:source-path source-path, :target-path target-path, :compiler-options compiler-options}))))


(defn javac
  "Java source compiler.

  Params:
    * source-path      - path to java sources
    * target-path      - files are compiled to the target path. (Default: \"target/classes\")
    * compiler-options - java compiler options.

  Example:
    * (javac \"src/main/java\")
    * (javac \"src/main/java\" \"classes\")
    * (javac \"src/main/java\" \"classes\" [\"-target\" \"8\" \"-source\" \"8\" \"-Xlint:-options\"])
    * (javac \"src/main/java\" \"classes\" [\"-cp\" \"src/main/java:target/classes\" \"-target\" \"8\" \"-source\" \"8\" \"-Xlint:-options\"])"
  ([source-path]
   (compile source-path "target/classes" nil))

  ([source-path target-path]
   (compile source-path (or target-path "target/classes") nil))

  ([source-path target-path compiler-options]
   (compile source-path (or target-path "target/classes") (parse-opts compiler-options))))


(def -main javac)



(comment

  ;; Examples

  (javac "src/main/java")
  ;; calling from the command line:
  ;;   $ clojure -A:javac --main tools.java.compiler src/main/java
  ;; both results are identical:
  ;;   => compiles into "target/classes" directory with the current java version

  (javac "src/main/java" "classes1")
  ;; calling from the command line:
  ;;   $ clojure -A:javac --main tools.java.compiler src/main/java classes1
  ;; both results are identical:
  ;;   => compiles into "classes1" directory with the current java version

  (javac "src/main/java" "classes2" ["-target" "8" "-source" "8" "-Xlint:-options"])
  ;; calling from the command line:
  ;;   $ clojure -A:javac --main tools.java.compiler src/main/java classes2 '["-target" "8" "-source" "8" "-Xlint:-options"]'
  ;; both results are identical:
  ;;   => compiles into "classes2" directory with the java 8 version

  (javac "src/main/java" "classes3" ["-cp" "src/main/java:target/classes" "-target" "14" "-source" "14" "-Xlint:-options"])
  ;; calling from the command line:
  ;;   $ clojure -A:javac --main tools.java.compiler src/main/java classes3 '["-cp" "src/main/java:target/classes" "-target" "14" "-source" "14" "-Xlint:-options"]'
  ;; both results are identical:
  ;;   => compiles into "classes3" directory with the java 14 version
  )
