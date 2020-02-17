(ns org.rssys.pbuilder.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcho.core :refer [match]]
            [org.rssys.pbuilder.core :as sut]))


(deftest ^:unit a-test
  (testing "simple test."
    (is (= 1 1))))
