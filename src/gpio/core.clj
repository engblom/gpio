(ns gpio.core
  (:import (java.nio.file FileSystems StandardWatchEventKinds WatchService Path)
           (java.io File))
  (:require [clojure.string :refer [split]])
  (:gen-class))


(def output-streams (make-array java.io.BufferedOutputStream 
                                (inc (Integer/parseInt (re-find #"\d+" (slurp "/sys/class/gpio/gpiochip0/ngpio"))))))

(defn ^:private pin-from-file
  "Converts the full /sys/class/gpio filename for a value file to the corresponding pin number"
  [file-name]
  (let [file (split file-name #"/")]
    (if-not (= (last file) "value")
      nil
      (Integer/parseInt (subs (last (butlast file)) 4)))))

(defn ^:private value-file
  "Converts the pin number to corresponding file under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/value"))

(defn ^:private active-low-file
  "Converts the pin number to corresponding file under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/active_low"))

(defn ^:private direction-file
  "Converts the pin number to corresponding file under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/direction"))

(defn ^:private edge-file
  "Converts the pin number to corresponding file under /sys/class/gpio"
  [pin]
  (str "/sys/class/gpio/gpio" pin "/edge"))

(defn ^:private temperature-sensor-file
  "Converts sensor ID to corresponding file under /sys/bus/w1/devices/"
  [sensor]
  (str "/sys/bus/w1/devices/" sensor "/w1_slave"))

(defn ^:private writeable?
  "Checks if all supplied files are writable"
  [& files]
  (reduce #(and %1 %2) (map #(.canWrite (File. ^String %)) files)))

(defn ^:private all-files-writeable?
  "Checks if active_low, direction and value are writeable for supplied pin"
  [pin]
  (writeable? (value-file pin) 
              (active-low-file pin) 
              (direction-file pin)
              (edge-file pin)))

(defn ^:private create-value-stream
  "Creates an output-stream for the value file."
  [pin]
  (when (nil? (aget ^"[Ljava.io.BufferedOutputStream;" output-streams ^long pin))
    (aset ^"[Ljava.io.BufferedOutputStream;" output-streams pin (clojure.java.io/output-stream (java.io.File. ^String (value-file pin)))))
  pin)

(defn ^:private destroy-value-stream
  "If an output-stream do exist for the value file, it will get destroyed from the stream map."
  [pin]
  (let [^java.io.BufferedOutputStream stream (aget ^"[Ljava.io.BufferedOutputStream;" output-streams pin)]
    (when-not (nil? stream)
      (.close stream)
      (aset ^"[Ljava.io.BufferedOutputStream;" output-streams pin nil))
    pin))

(defn wait 
  "Wait as many milliseconds as given"
  [^long ms]
  (when-not (zero? ms)
    (if (< ms 10)
      (let [stop-time (+ ms (System/currentTimeMillis))]
        (loop []
          (when (> stop-time (System/currentTimeMillis)) (recur))))
      (Thread/sleep ms))))

(defn open-pin 
  "Exports pin for usage and returns the pin number."
  [pin]
  (if-not (all-files-writeable? pin) 
    (do
      (spit "/sys/class/gpio/export" (str pin))
      (loop []
        (if (all-files-writeable? pin)
          (create-value-stream pin)
          (recur))))
    (create-value-stream pin)))

(defn close-pin
  "Unexports pin."
  [pin]
  (destroy-value-stream pin)
  (when (.exists (File. (str "/sys/class/gpio/gpio" pin)))
    (spit "/sys/class/gpio/unexport" (str pin)))
  pin)

(defn set-edge
  "Sets the edge of an input pin with one of the following: :both, :rising, :falling and :none"
  [pin edge]
  (let [file (edge-file pin)]
    (case edge
      :rising (spit file "rising")
      :falling (spit file "falling")
      :both (spit file "both")
      (spit file "none")))
  pin)

(defn get-edge
  "Get edge of the pin"
  [pin]
  (let [edge (slurp (edge-file pin))]
    (case edge
      "rising\n" :rising
      "falling\n" :falling
      "both\n" :both
      :none)))

(defn set-direction
  "Sets the direction of the pin. Use :in or :out for direction."
  [pin direction]
  (let [file (direction-file pin)]
    (when (or (not (writeable? file)) (nil? (aget ^"[Ljava.io.BufferedOutputStream;" output-streams pin)))
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
  [^long pin value]
  (let [^java.io.BufferedOutputStream stream (aget ^"[Ljava.io.BufferedOutputStream;" output-streams pin)]
    (if value
      (.write ^java.io.BufferedOutputStream stream (.getBytes "1\n")) 
      (.write ^java.io.BufferedOutputStream stream (.getBytes "0\n"))) 
    (.flush ^java.io.BufferedOutputStream stream))
  value)

(defn write-multiple-values
  "Takes a vector of pins and a vector of value (of true or false)"
  [pins values]
  (doall (map write-value pins values))
  values)

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
      (spit file "0")))
  value)

(defn wait-for-input
  "Waits until one of the supplied pins has been modified"
  [& pins]
  ;This function is probably the most uggly thing I have ever written. While this function seems to work in all my tests, I would accept better solutions, if anyone sends me one.
  (let [ws (.newWatchService (FileSystems/getDefault))]
    (doseq [pin pins]
      (.register (.toPath (File. (str "/sys/class/gpio/gpio" pin))) ws (into-array (type  StandardWatchEventKinds/ENTRY_CREATE) [StandardWatchEventKinds/ENTRY_MODIFY]))
      (when (= (get-edge pin) :none)
        (set-edge pin :both)))
    (loop [change false changed-pin nil]
      (if (= change true)
        (do
          (.close ws)
          changed-pin)
        (let [k (.take ws)
              all-events (.pollEvents k)
              value-event (loop [events all-events]
                            (cond 
                              (zero? (count events)) nil
                              (= "value" (.getName ^java.io.File (.toFile ^java.nio.file.Path (.resolve (.watchable k)  (.context ^java.nio.file.WatchEvent (first events)))))) (pin-from-file (.getCanonicalPath ^java.io.File (.toFile ^java.nio.file.Path (.resolve  (.watchable k) (.context ^java.nio.file.WatchEvent (first events))))))
                              :else (recur (rest events))))]
          (.reset k)
          (recur (not (nil? value-event)) value-event))))))

(defn list-temperature-sensors
  "Lists DS18B20 sensors connected to the system"
  []
  (->> "/sys/bus/w1/devices"
       (File.)
       (.listFiles)
       (seq)
       (map #(.getName ^java.io.File %)) 
       (filter #(.startsWith ^String % "28"))))

(defn read-temperature
  "Reads temperature from DS18B20 sensors and returns the temperature in C"
  [sensor]
  (loop []
    (let [file (temperature-sensor-file sensor)
          lines (split (slurp file) #"\n")]
      (if (= "YES" (re-find #"YES" (first lines)))
        (float (/ (Integer/parseInt (re-find #"\d+$" (second lines))) 1000))
        (recur)))))

(defn read-temperature-all
  "Reads the temperature from all connected DS18B20"
  []
  (into {} (map (juxt identity read-temperature)) (list-temperature-sensors)))

(defn new-stepper-motor
  "Returns a motor (a map of all information about the motor) that can be used together with turn-stepper-motor and inactivate-stepper-motor."
  [stepper-sequence initial-position pins]
  {:sequence stepper-sequence
   :position (ref initial-position)
   :pins (mapv #(set-direction % :out) pins)})

(defn inactivate-stepper-motor
  "Turns off all output to the stepper motor by writing false to all pins"
  [{^clojure.lang.PersistentVector pins :pins}]
  (write-multiple-values pins (map (fn [_] false) pins)))

(defn turn-stepper-motor
  "Turn the motor as many steps you want. Positive steps move the motor forward and negative steps backwards. The step-time is the time in ms to wait between each step."
  [{^clojure.lang.PersistentVector stepper-sequence :sequence ^clojure.lang.Atom position :position ^clojure.lang.PersistentVector pins :pins} ^long steps ^long step-time]
  (let [forward (pos? steps)
        sequence-length (count stepper-sequence)]
    (dosync
      (dotimes [_ (Math/abs steps)]
        (let [new-position (mod (if forward
                                  (inc @position)
                                  (dec @position))
                                sequence-length)]
          (write-multiple-values pins (nth stepper-sequence new-position))
          (ref-set position new-position)
          (wait step-time)))
      @position)))

