(ns gpio.core
  (:import (java.nio.file FileSystems StandardWatchEventKinds WatchService Path)
           (java.io File))
  (:require [clojure.string :refer [split]])
  (:gen-class))

(defn ^:private pin-from-file
  "Converts the full /sys/class/gpio filename for a value file to the corresponding pin number"
  [file-name]
  (let [file (split file-name #"/")]
    (if-not (= (last file) "value")
      nil
      (Integer/parseInt (subs (last (butlast file)) 4)))))

(defn ^:private value-file
  "Converts the pin number to corresponding file in under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/value"))

(defn ^:private active-low-file
  "Converts the pin number to corresponding file in under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/active_low"))

(defn ^:private direction-file
  "Converts the pin number to corresponding file in under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/direction"))

(defn ^:private edge-file
  "Converts the pin number to corresponding file in under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/edge"))

(defn ^:private writeable?
  "Checks if all supplied files are writable"
  [& files]
  (reduce #(and %1 %2) (map #(.canWrite (File. %)) files)))

(defn ^:private all-files-writeable?
  "Checks if active_low, direction and value are writeable for supplied pin"
  [pin]
  (writeable? (value-file pin) 
              (active-low-file pin) 
              (direction-file pin)
              (edge-file pin)))

(defn open-pin 
  "Exports pin for usage and returns the pin number."
  [pin]
  (if (not (all-files-writeable? pin)) 
    (do
      (spit "/sys/class/gpio/export" (str pin))
      (loop []
        (if (all-files-writeable? pin)
          pin
          (recur))))
    pin))

(defn close-pin
  "Unexports pin."
  [pin]
  (when (.exists (File. (str "/sys/class/gpio/gpio" pin)))
    (spit "/sys/class/gpio/unexport" (str pin)))
  pin)

(defn set-direction
  "Sets the direction of the pin. Use :in or :out for direction."
  [pin direction]
  (let [file (direction-file pin)]
  (when (not (writeable? file))
    (open-pin pin))
    (if (= direction :in)
      (spit file "in")
      (spit file "out")))
  pin)

(defn read-value
  "Reads the value from the pin and returns 1 as true and 0 as false."
  [pin]
  (if (= "1\n" (slurp (value-file pin)))
    true
    false))

(defn write-value
  "Converts true to 1 and false to 0 and writes it to the pin."
  [pin value]
  (let [file (value-file pin)]
    (if value
      (spit file "1")
      (spit file "0"))))

(defn toggle-value
  "Toggle value of a pin"
  [pin]
  (let [old-value (read-value pin)
        new-value (not old-value)]
    (write-value pin new-value)
    new-value))

(defn active-low
  "If set to true, the read-value function will return true when the input pin is low"
  [pin value]
  (let [file (active-low-file pin)]
    (if value
      (spit file "1")
      (spit file "0"))))

(defn wait-for-input
  "Waits until one of the supplied pins has been modified"
  [& pins]
  (let [ws (.newWatchService (FileSystems/getDefault))]
    (doseq [pin pins]
      (.register (.toPath (File. (str "/sys/class/gpio/gpio" pin))) ws (into-array (type  StandardWatchEventKinds/ENTRY_CREATE) [StandardWatchEventKinds/ENTRY_MODIFY]))
      (spit (edge-file pin) "both"))
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
                              (= "value" (.getName (.toFile (.resolve (.watchable k) (.context (first events)))))) (pin-from-file (.getCanonicalPath (.toFile (.resolve (.watchable k) (.context(first events))))))
                              :else (recur (rest events))))]
          (.reset k)
          (recur (not (nil? value-event)) value-event))))))
