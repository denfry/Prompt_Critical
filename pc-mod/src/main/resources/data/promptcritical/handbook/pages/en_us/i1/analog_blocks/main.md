Relays know only yes and no. Control, holding a level, a pressure, a temperature, needs quantities. Analog computing blocks calculate with voltages: an operational amplifier with feedback adds, integrates and differentiates. Valve-based blocks of the 1950s solved differential equations this way and ran processes long before digital machines.

## Signals

- **±10 V**: the signal between computing blocks.
- **4–20 mA**: the signal of field instruments. Zero is 4 mA, not 0: a broken wire is not mistaken for a zero reading.

Wiring uses the same [wiring tool](item:promptcritical:wiring_tool), terminal to terminal of the same type. The [signal converter](item:promptcritical:signal_converter) turns one into the other: mA to V as U = 10·(I − 4)/16, and below 3.6 mA the output is bad; V to mA as I = 4 + 1.6·U.

## Blocks

- [Summer](item:promptcritical:analog_summer): four inputs, Y = −(k₁x₁ + k₂x₂ + k₃x₃ + k₄x₄), coefficients from −10 to 10.
- [Integrator](item:promptcritical:analog_integrator): Y = −(1/T)·∫Σkx dt. Terminals set its mode: IC sets the output to the initial condition on the IC input, HOLD freezes it, otherwise it computes.
- [Differentiator](item:promptcritical:analog_differentiator): a filtered derivative. It is noisy: it amplifies input noise.
- [Multiplier](item:promptcritical:analog_multiplier): x₁x₂/10, 10x₁/x₂, x²/10 or √(10x).
- [Function generator](item:promptcritical:analog_function): a piecewise-linear curve through set points.
- [Comparator](item:promptcritical:analog_comparator): contacts 11-12-14 change over when x₁ > x₂ and return when x₁ ≤ x₂ minus the reset band (0.1 V). This is the bridge into relay logic.
- [Manual/auto station](item:promptcritical:manual_station): the operator's place, below.

The summer and the integrator **invert** the sign, like real operational amplifiers. Set the signs so that the result acts in the right direction. Get a sign wrong and you have positive feedback: the output runs to its limit within seconds.

## Imperfection

The blocks are not ideal, and it shows. Output is limited to ±10.5 V. The integrator's zero drifts: it accumulates error even with no input. Balance the zero with the "Zero" button in IC or HOLD; the panel reminds you when it is due. Valves age and fail: the output drops to zero, quality reads "Bad", and the block is replaced. A loop of blocks with no integrator inside is an **algebraic loop**: the circuit cannot be solved and its blocks sit at the limit.

Two outputs on one net are a conflict; the inputs on that net read 0.

## The manual/auto station

Station inputs: AUTO, the controller output; PV, the measured value. Outputs: SP, the operator's setpoint; OUT, the output to the final element in mA; TRK, the tracking signal. MAN and AUTO buttons, and ▲▼ knobs move the setpoint and the manual output. A discrete MAN input from a relay forces the station to manual; that is how a trip takes control away from the controller.

Switching AUTO to MAN, the manual output picks up the controller's last output. The way back is bumpless only if the circuit tracks: in manual, the controller's integrator sits in IC and takes the TRK signal on its IC input. Without tracking the output jumps.

## A PI controller from blocks

There is no ready-made PID block: you build it.

- Summer Σ1: the error e = SP − PV.
- Proportional part: a summer with gain Kc.
- Integral part: an integrator with time constant T = Ti/Kc.
- Summer Σ3 adds the parts and feeds the station's AUTO input.

If you need derivative, take it from the measurement, not the error, through a differentiator: then a setpoint step does not kick the final element.

Try the controller on a model: an integrator fed with the station output and a "draw-off" from another setpoint behaves like the level in a tank. That is how analog computers were used: model the process first, then tune. A controller that holds PV at SP while the station is in AUTO is a working one.

The analog loop reaches Create machinery through the comparator: its contacts drive a relay and the redstone interface. That is on-off control with a reset band, like a thermostat.
