(ns user)

;; debug print with #p
(require 'hashp.core)

(def dev-mode true)
(println {:msg "development mode" :status (if (true? dev-mode) "on" "off")})

