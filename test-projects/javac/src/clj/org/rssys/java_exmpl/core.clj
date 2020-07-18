(ns org.rssys.java-exmpl.core
  (:import [org.rssys.javac_example Hello] ))

(defn  -main [& args]
  (println "running main")
  (Hello/sayHello "Java world!")
  )
