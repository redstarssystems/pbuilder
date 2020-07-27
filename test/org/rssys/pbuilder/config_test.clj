(ns org.rssys.pbuilder.config-test
  (:require [clojure.test :refer :all]
            [org.rssys.pbuilder.config :as sut]
            [matcho.core :refer [match not-match]]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]
            [malli.transform :as mt]))

(defn contains-keys? [m & ks]
  (every? #(contains? m %) ks))


(deftest ^:unit Java-src-folder-test
  (testing "java-src-folder test."
    (match (m/properties sut/Java-src-folder) {:title :java-src-folder})
    (match (m/validate sut/Java-src-folder "test-projects/javac/src/java"))
    (match (not (m/validate sut/Java-src-folder "abc")))
    (me/humanize (m/explain sut/Java-src-folder "abc"))))

(deftest ^:unit Javac-options-test
  (testing "Java compiler options test."
    (match (m/properties sut/Javac-options) {:title :javac-options})
    (match (m/validate sut/Javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]))
    (match (not (m/validate sut/Javac-options ["-target" "1.8" 3])))
    (m/explain sut/Javac-options ["-target" "1.8" 3])
    (m/decode sut/Javac-options nil mt/default-value-transformer) ;; just add :default to see value
    (mg/generate sut/Javac-options {:size 10})
    ))
