(ns org.rssys.pbuilder.release
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [org.rssys.pbuilder.process :as p]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the code below is taken from Leiningen
;; https://github.com/technomancy/leiningen/blob/master/src/leiningen/release.clj

(def ^:dynamic *level* nil)

(defn string->semantic-version [version-string]
  "Create map representing the given version string. Returns nil if the
  string does not follow guidelines setforth by Semantic Versioning 2.0.0,
  http://semver.org/"
  ;; <MajorVersion>.<MinorVersion>.<PatchVersion>[-<Qualifier>][-SNAPSHOT]
  (if-let [[_ major minor patch qualifier snapshot]
           (re-matches
             #"(\d+)\.(\d+)\.(\d+)(?:-(?!SNAPSHOT)([^\-]+))?(?:-(SNAPSHOT))?"
             version-string)]
    (->> [major minor patch]
      (map #(Integer/parseInt %))
      (zipmap [:major :minor :patch])
      (merge {:qualifier qualifier
              :snapshot  snapshot}))))

(defn parse-semantic-version [version-string]
  "Create map representing the given version string. Aborts with exit code 1
  if the string does not follow guidelines setforth by Semantic Versioning 2.0.0,
  http://semver.org/"
  (or (string->semantic-version version-string)
    (throw (ex-info "Unrecognized version string:" {:version version-string}))))

(defn version-map->string
  "Given a version-map, return a string representing the version."
  [version-map]
  (let [{:keys [major minor patch qualifier snapshot]} version-map]
    (cond-> (str major "." minor "." patch)
      qualifier (str "-" qualifier)
      snapshot (str "-" snapshot))))

(defn next-qualifier
  "Increments and returns the qualifier.  If an explicit `sublevel`
  is provided, then, if the original qualifier was using that sublevel,
  increments it, else returns that sublevel with \"1\" appended.
  Supports empty strings for sublevel, in which case the return value
  is effectively a BuildNumber."
  ([qualifier]
   (if-let [[_ sublevel] (re-matches #"([^\d]+)?(?:\d+)?"
                           (or qualifier ""))]
     (next-qualifier sublevel qualifier)
     "1"))
  ([sublevel qualifier]
   (let [pattern (re-pattern (str sublevel "([0-9]+)"))
         [_ n] (and qualifier (re-find pattern qualifier))]
     (str sublevel (inc (Integer. (or n 0)))))))

(defn bump-version-map
  "Given version as a map of the sort returned by parse-semantic-version, return
  a map of the version incremented in the level argument.  Always returns a
  SNAPSHOT version, unless the level is :release.  For :release, removes SNAPSHOT
  if the input is a SNAPSHOT, removes qualifier if the input is not a SNAPSHOT."
  [{:keys [major minor patch qualifier snapshot]} level]
  (let [level (or level
                (if qualifier :qualifier)
                :patch)]
    (case (keyword (name level))
      :major {:major (inc major) :minor 0 :patch 0 :qualifier nil :snapshot "SNAPSHOT"}
      :minor {:major major :minor (inc minor) :patch 0 :qualifier nil :snapshot "SNAPSHOT"}
      :patch {:major major :minor minor :patch (inc patch) :qualifier nil :snapshot "SNAPSHOT"}
      :alpha {:major     major :minor minor :patch patch
              :qualifier (next-qualifier "alpha" qualifier)
              :snapshot  "SNAPSHOT"}
      :beta {:major     major :minor minor :patch patch
             :qualifier (next-qualifier "beta" qualifier)
             :snapshot  "SNAPSHOT"}
      :rc {:major     major :minor minor :patch patch
           :qualifier (next-qualifier "RC" qualifier)
           :snapshot  "SNAPSHOT"}
      :qualifier {:major     major :minor minor :patch patch
                  :qualifier (next-qualifier qualifier)
                  :snapshot  "SNAPSHOT"}
      :release (merge {:major major :minor minor :patch patch}
                 (if snapshot
                   {:qualifier qualifier :snapshot nil}
                   {:qualifier nil :snapshot nil})))))

(defn bump-version
  "Given a version string, return the bumped version string -
   incremented at the indicated level. Add qualifier unless releasing
   non-snapshot. Level defaults to *level*."
  [version-str & [level]]
  (-> version-str
    (parse-semantic-version)
    (bump-version-map (or level *level*))
    (version-map->string)))

;; end of Leiningen code
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assert-commited?
  "# check for all changes are commited using git VCS

    * Params:
      `config` - map produced by `build-config` function.

    * Returns:
      `true` - if all changes are commited
      `false` - not all changes are commited.
    "
  []
  (println "checking for all changes are commited... (git only)")
  (let [result (sh/sh "git" "status" "--porcelain")]
    (-> result :out str/blank?)))

(defn change-artifact-version
  "# change artifact version in pbuild file.

  * Warning: modifies external file (default `pbuild.edn`)."
  [config new-version build-filename]
  (let [old-file    (slurp build-filename)
        new-content (str/replace old-file (:artifact-version config) new-version)
        _           (spit build-filename new-content)]
    (println "file:" build-filename "modified. New artifact version is:" new-version)
    (when (:overwrite-version-file config)
      (spit (:overwrite-version-file config) new-version)
      (println "file:" (:overwrite-version-file config) " overwritten with version" new-version))))

(defn run-release
  "# run release cycle for library
     1) check all changes are committed.
     2) bump version
     3) commit changes
     4) set tag
     5) compile & deploy jar
     6) set new dev minor version
     7) commit changes"

  [config  build-filename]
  (if (assert-commited?)
    (do
      (println "ok.")
      (let [_               (println "changing artifact version...")
            new-version     (bump-version (:artifact-version config) "release")
            _               (change-artifact-version config new-version build-filename)
            config          (p/build-config build-filename)
            _               (println "adding new version to git..." new-version)
            result          (sh/sh "git" "add" ".")
            _               (when-not (zero? (:exit result))
                              (throw (ex-info "error: git add result:" result)))
            _               (println "committing changes...")
            result          (sh/sh "git" "commit" "-m" new-version)
            _               (when-not (zero? (:exit result))
                              (throw (ex-info "git commit result:" result)))
            _               (println "adding tag...")
            result          (sh/sh "git" "tag" "-a" "-m" new-version new-version)
            _               (when-not (zero? (:exit result))
                              (throw (ex-info "git tag result:" result)))
            _               (println "building & deploying new artifact version...")
            _               (p/deploy-jar config)
            new-version-dev (bump-version (:artifact-version config))
            _               (change-artifact-version config new-version-dev build-filename)
            config          (p/build-config build-filename)
            _               (println "changing artifact version to dev...")
            result          (sh/sh "git" "add" ".")
            _               (when-not (zero? (:exit result))
                              (throw (ex-info "git add dev result:" result)))
            _               (println "committing new dev version..." new-version-dev)
            result          (sh/sh "git" "commit" "-m" new-version-dev)
            _               (when-not (zero? (:exit result))
                              (throw (ex-info "git commit new dev version result:" result)))
            _               (println "pushing all changes to repo with tags...")
            result          (sh/sh "git" "push" "origin" "HEAD" "--tags")
            _               (when-not (zero? (:exit result))
                              (throw (ex-info "git push dev result:" result)))]
        (println "release complete.")))
    (println "error: not all changes are committed!")))

(defn bump-version-file
  "# bump artifact version in build file"
  [config build-file level]
  (let [bumped-ver (bump-version (:artifact-version config) level)]
    (change-artifact-version config bumped-ver build-file)))

(comment

  (def config (org.rssys.pbuilder.process/build-config "pbuild.edn"))
  (def old-file (slurp "pbuild.edn"))
  (def new-version (bump-version (:artifact-version config) (or "release")))
  (assert-commited?)
  (bump-version "0.1.0-SNAPSHOT")
  (bump-version (bump-version "0.2.1-beta1-SNAPSHOT" "release"))
  (bump-version (bump-version "1.0.0-SNAPSHOT" "beta" "release"))

  )
