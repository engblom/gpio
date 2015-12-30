(ns gpio.core
  (:gen-class)
  (:require [juxt.dirwatch :refer :all]))

(defn open-port 
  "Exports port for usage and returns the port number."
  [port]
  (spit "/sys/class/gpio/export" (str port))
  port)

(defn close-port
  "Unexports port."
  [port]
  (spit "/sys/class/gpio/unexport" (str port)))

(defn set-direction
  "Sets the direction of the port. Use :in or :out for direction."
  [port direction]
  (let [direction-file (str "/sys/class/gpio/gpio" port "/direction")]
    (if (= direction :in)
      (spit direction-file "in")
      (spit direction-file "out"))))

(defn read-value
  "Reads the value from the port and returns 1 as true and 0 as false."
  [port]
  (if (= "1\n" (slurp (str "/sys/class/gpio/gpio" port "/value")))
    true
    false))

(defn write-value
  "Converts true to 1 and false to 0 and writes it to the port."
  [port value]
  (let [value-file (str "/sys/class/gpio/gpio" port "/value")]
    (if value
      (spit value-file "1")
      (spit value-file "0"))))
