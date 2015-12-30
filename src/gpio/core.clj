(ns gpio.core
  (:gen-class))

(defn open-pin 
  "Exports pin for usage and returns the pin number."
  [pin]
  (spit "/sys/class/gpio/export" (str pin))
  pin)

(defn close-pin
  "Unexports pin."
  [pin]
  (spit "/sys/class/gpio/unexport" (str pin)))

(defn set-direction
  "Sets the direction of the pin. Use :in or :out for direction."
  [pin direction]
  (let [direction-file (str "/sys/class/gpio/gpio" pin "/direction")]
    (if (= direction :in)
      (spit direction-file "in")
      (spit direction-file "out"))))

(defn read-value
  "Reads the value from the pin and returns 1 as true and 0 as false."
  [pin]
  (if (= "1\n" (slurp (str "/sys/class/gpio/gpio" pin "/value")))
    true
    false))

(defn write-value
  "Converts true to 1 and false to 0 and writes it to the pin."
  [pin value]
  (let [value-file (str "/sys/class/gpio/gpio" pin "/value")]
    (if value
      (spit value-file "1")
      (spit value-file "0"))))

