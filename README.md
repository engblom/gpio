# gpio

This is a simple library for using the GPIO headers on Raspberry Pi computers in a REPL friendly way. Currently it only works on Linux as it is using the /sys/ filesystem. This library is developed on Raspbian Buster.

Most functionality is done, but there surely is room for improvements. Feel free to open an issue if you have suggestions!

## Usage

If you use leiningen, add gpio as a dependency:

[![Clojars Project](https://img.shields.io/clojars/v/gpio.svg)](https://clojars.org/gpio)

Any functions asking for `pin` wants the Broadcom GPIO number.

### Opening a pin for use

````
(open-pin pin)
````

As a side effect to this function, the `pin` number provided will be exported for use. This function will always return the same value as given to it. For example `(open-pin 4)` will export GPIO4 and will also return `4`. If the `pin` is already exported, this function will only return the `pin`.

### Closing a pin

````
(close-pin pin)
````

As a side effect to this function, the `pin` number provided will be unexported. This function will always return the same value as given to it. For example `(close-pin 4)` will unexport GPIO4 and will also return `4`. If the `pin` is already closed, this function will only return the `pin`.

### Setting the direction of a pin

````
(set-direction pin direction)
````

The direction is given by either `:in` or `:out`. This function will return the pin number. For example `(set-direction 4 :out)` will set GPIO4 to `:out` and return `4`. If the pin is not already opened, it will get opened before setting the direction.

### Reading value

````
(read-value pin)
````

This function will return `true` if the pin is high and `false` if the pin is low. For example: `(read-value 4)`

### Writing value

````
(write-value pin value)
````

The value needs to be either `true` or `false`. For example: `(write-value 4 true)`

````
(write-multiple-values pins values)
````

The `pins` argument need to be a vector of GPIO pins, The `values` argument needs to be a vector of either `true` or `false`. For example: `(write-multiple-values [4 18] [false true])`
This function is useful when controlling a stepper motor.

### Toggle value

````
(toggle-value pin)
````

This function will read the current value of the pin and write the inverted result back to the pin.

### Change input mode

````
(active-low pin value)
````

If `value` is set to `true`, the `(read-value pin)` function will return `true` for low inputs and `false` for high inputs.
For example: `(active-low 4 true)`

### Blocking while waiting for input

````
(wait-for-input pin1 pin2 pin3 ...)
````

Instead of wasting CPU cycles by busy-waiting for an input, you can use this function to block the program from running until one of the supplied pins got rewritten either by a GPIO input or another program modifying the pin. This function will return the `pin` that got first modified.

For example `(wait-for-input 4)` will stop the execution of the current function until something has been rewriting the value of GPIO4.

`(wait-for-input pin1 pin2 pin3 ...)` will set edge to `:both`, in case it is set to `:none`.

````
(set-edge pin value)
````

These are the values `(set-edge pin value)` is able to take: `:rising`, `:falling`, `:both` and `:none`.
`:rising` will cause `(wait-for-input pin1 pin2 pin3 ...)` to block until one of the pins goes from `false` to `true`, `:falling` will cause a block until one of the pins goes from `true` to `false`.

````
(get-edge pin)
````

This function will return the currently set `edge`.

### Temperature sensors
The libary is able to list all DS18B20 sensors connected with this function:

````
(list-temperature-sensors)
````

Using one `sensor` of the listed sensors you can read the temperature in Celcius:

````
(read-temperature sensor)
````

You can also read all the sensors at once:

````
(read-temperature-all)
````

### Stepper motors

This library is able to control stepper motors as long as you know the stepping sequence. The stepping sequence can usually be found from the  manufacturer datasheet of the stepper motor.

The example below should be pretty much self-explaining.

````
; The stepping sequence for the 28BYJ-48 stepper motor.
(def byj48-seq [[false false false true]
                [false false true true]
                [false false true false]
                [false true true false]
                [false true false false]
                [true true false false]
                [true false false false]
                [true false false true]])

; (new-stepper-motor sequence inital-position pins)
; The initial position is the position in the sequence you want to start from
(def motor (new-stepper-motor byj48-seq 0 [12 16 20 21]))

(defn -main
  "Turn the motor 5000 steps clockwise, then 5000 steps counter-clockwise"
    [& args]
    ; (turn-stepper-motor motor steps time)
    ; Positive number of steps will turn the motor clockwise and 
    ; negative number of steps will turn it counter-clockwise.
    ; The time is in milliseconds the time between each step.
    (turn-stepper-motor motor 5000 2)
    (turn-stepper-motor motor -5000 2)
    
    ; Put the stepper motor to rest
    (inactivate-stepper-motor motor))

````

## License

Copyright © 2019 Lars Engblom

Distributed under the Eclipse Public License either version 1.0 or any later version.
