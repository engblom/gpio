# gpio

This is a simple library for using the GPIO headers on Raspberry Pi computers in a REPL friendly way.

Most functionality is done, but there surely is room for improvements. Feel free to open an issue if you have suggestions!

## Usage

If you use leingingen, add gpio as a dependency:

````
[gpio "0.1.1"]
````

All functions asking for `pin` wants the Broadcom GPIO number.

### Opening a pin for use

````
(open-pin pin)
````

As a side effect to this function, the pin number provided will be exported for use. This function will always return the same value as given to it. For example `(open-pin 4)` will export GPIO4 and will also return `4`. If the pin is already exported, this function will only return the pin.

### Setting the direction of a pin

````
(set-direction pin direction)
````

The direction is given by either `:in` or `:out`. This function will return the pin number. For example `(set-direction 4 :out)` will set GPIO4 to :out and return 4. If the pin is not already opened, it will get opened before setting the direction.

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

Instead of wasting CPU cycles by busy-waiting for an input, you can use this function to block the program from running until one of the supplied pins got rewritten either by an GPIO input or another program modifying the pin. This function will return the `pin` that got first modified.

For example `(wait-for-input 4)` will stop the execution of the current function until something has been rewriting the value of GPIO4.

## License

Copyright © 2015 Lars Engblom

Distributed under the Eclipse Public License either version 1.0 or any later version.
