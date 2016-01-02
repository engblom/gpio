(ns gpio.core
  (:import (java.nio.file FileSystems StandardWatchEventKinds WatchService Path)
           (java.io File))
  (:require [clojure.string :refer [split]])
  (:gen-class))

(defn ^:private convert-filename-to-pin
  "Converts the full /sys/class/gpio filename for a value file to the corresponding pin number"
  [file-name]
  (let [file (split file-name #"/")]
    (if-not (= (last file) "value")
      nil
      (Integer/parseInt (subs (last (butlast file)) 4)))))

(defn ^:private convert-pin-to-filename
  "Converts the pin number to corresponding file in under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/value"))

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
  (if (= "1\n" (slurp (convert-pin-to-filename pin)))
    true
    false))

(defn write-value
  "Converts true to 1 and false to 0 and writes it to the pin."
  [pin value]
  (let [value-file (convert-pin-to-filename pin)]
    (if value
      (spit value-file "1")
      (spit value-file "0"))))

(defn toggle-value
  "Toggle value of a pin"
  [pin]
  (let [old-value (read-value pin)
        new-value (not old-value)]
    (write-value pin new-value)
    new-value))

(defn low-input
  "If set to true, the read-value function will return true when the input pin is low"
  [pin low]
  (let [active_low (str "/sys/class/gpio/gpio" pin "/active_low")]
    (if low
      (spit active_low "1")
      (spit active_low "0"))))

(defn wait-for-input
  "Waits until one of the supplied pins has been modified"
  [& pins]
  (let [ws (.newWatchService (FileSystems/getDefault))]
    (doseq [pin pins]
      (.register (.toPath (File. (str "/sys/class/gpio/gpio" pin))) ws (into-array (type  StandardWatchEventKinds/ENTRY_CREATE) [StandardWatchEventKinds/ENTRY_MODIFY])))
    (loop [change false changed-pin nil]
      (if (= change true)
        (do
          (.close ws)
          changed-pin)
        (let [k (.take ws)
              all-events (.pollEvents k)
              value-event (loop [events all-events]
                            (cond 
                              (= (count events) 0) nil
                              (= "value" (.getName (.toFile (.resolve (.watchable k) (.context (first events)))))) (convert-filename-to-pin (.getCanonicalPath (.toFile (.resolve (.watchable k) (.context(first events))))))
                              :else (recur (rest events))))]
          (.reset k)
          (recur (not (nil? value-event)) value-event))))))
