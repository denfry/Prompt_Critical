The telemetry gateway tells a dispatcher what every machine is doing. The level above it asks a different question: is the plant as a whole making money? That is the job of the plant server, the MES of automation level A5.

## What it reads

The [plant server](item:promptcritical:mes_server) collects the snapshots of every [telemetry gateway](item:promptcritical:rtu_station) within 16 blocks once a second. An instrument in kW or MW counts as generation, unless its label speaks of load, consumption or demand: then it counts as load. A generator's capacity is the highest output the server has seen from it.

The panel shows generation, load, the dispatch target, fuel cost and sales per hour, CO2 per hour and the dispatch efficiency.

## Equal incremental cost

Each generator gets a quadratic fuel cost curve F(P) = a + bP + cP² by its fuel: wind and water cost nothing, a reactor little, gas more, coal and wood the most, and burning fuel also pays a CO2 fee per tonne. The cheapest way to carry a given load is the one where every running unit has the same incremental cost dF/dP, called lambda; a unit stuck at its limit stays there. The server finds lambda by bisection and shows the recommended setpoint of each unit.

Dispatch efficiency compares the cost of the current split with the cost of the best split of the same output. 100% means nothing to gain by moving load between units.

## Setpoints

The target follows the measured load by default. The panel buttons move it by 10% or return it to following the load. The server only recommends: it does not touch the machines, you or your program set them.

With CC: Tweaked, a computer attached to the server sees the **pc_mes** peripheral:

- **getKpi()** — generation, load, target, capacity, costRate, revenueRate, profitRate, emissions, efficiency, lambda;
- **getDispatch()** — units: **id**, type, output, setpoint, rated;
- **setDispatchTarget(mw)** — set the target; a negative value returns to following the load.

## Closed loop

The "Closed loop" button (or `mes.setAuto(true)` in Lua) turns the server from an adviser into a controller. Every cycle it compares each unit's output with its economic dispatch setpoint. If a unit is off by more than 5 % of its rating, the server sends a "raise" or "lower" pulse to its panel through the RTU: the trim of a shaft generator, the setpoint of a diesel generator or the set point of a pumped-storage unit. This is how secondary control from a dispatch desk works. A unit without such buttons stays under manual control.


## Historian

The [historian server](item:promptcritical:historian_server) polls the telemetry gateways within 16 blocks once a second by itself and archives each of their analog measurements as a "machine/point" tag. No Lua tokens are spent on it. The archive is compressed: values that have barely changed are dropped, and of the rest only the bends of the trend are kept (the swinging door algorithm). The restored curve stays within about a quarter of a percent of the value. Process trends compress tens of times this way. The panel shows the number of tags and the compression, and for the selected tag the current value and the hour's average, minimum and maximum. Through the `pc_historian` peripheral a CC: Tweaked computer gets `listTags()`, `query(tag, t0, t1)`, `interpolate(tag, t0, t1, step)`, `aggregate(tag, t0, t1, "avg"|"min"|"max"|"count"|"integral"|"stdev")` and `getStats()`. Times are in seconds; the current one is `getStats().now`.


## Digital twin

The [simulation workstation](item:promptcritical:simulation_workstation) checks a PLC program before it drives real equipment. **FAT** takes the program of the nearest PLC (within 16 blocks) and runs it on a copy of a reference plant: a tank farm, a coal-fired boiler with a steam engine, or a generator closing onto the grid. The copy uses the same simulation as the world and the same PLC processor, so the program behaves the same way in both. Nothing moves in the world, and a boiler that bursts in the copy does no damage. The acceptance test disturbs the plant on a timetable: on the tank farm the outlet pump trips and the high level switch sticks, on the boiler the feed pump stops. The **Fault** button adds faults by hand: a frozen transmitter, a broken 4–20 mA line, a stuck valve, a PLC watchdog. The panel shows the instruments of the copy, the PLC mode and the loop metrics: integral of absolute error, overshoot, valve travel and the score. The PLC inputs and outputs are wired to the copy as 0–10 V signals: the tank level to `%IW0`, the inlet valve to `%QW0`, the pump to `%QX0.0`, the high level switch to `%IX0.0`. On the boiler the low water switch is on `%IX0.0` and the trip of the engine's stop valve on `%QX0.1`. **To PLC** loads the program that passed the test into the real PLC and turns its key to RUN. A program changed after the test needs a new one. With CC: Tweaked, the `pc_twin` peripheral lets a Lua program read the instruments of the copy and command its valves: `read(signal)`, `command(actuator, value)`, `getState()`, `startFat()`, `getLog()`.

## Training simulator

A [training simulator](item:promptcritical:training_simulator) cabinet runs a lesson on a copy of a plant. The [instructor station](item:promptcritical:instructor_station) and the [simulator console](item:promptcritical:simulator_console) within 8 blocks of it work with that copy, not with real equipment. The instructor picks a lesson, starts it, sets the speed and can add faults. The operator reads the instruments on the console and works the valves, pumps and breakers. All mistakes stay in the simulator. The lessons are taken from real accidents:

- **Tank overfill at night.** The level transmitter freezes, the independent high level switch fails, then the delivery grows. The level on the panel does not move, but the inflow is larger than the outflow, and the operator has to throttle the inlet before the tank overflows.
- **The forced bit.** After commissioning somebody left the high level input of the interlock PLC forced. The pump stops, and the interlock that should close the inlet stays silent. Watch the level and the forces lamp, remove the force or close the valve by hand.
- **Out-of-phase closing.** Bring the slip down with the speeder and close the breaker near zero on the synchroscope. Closing far from zero damages the shaft or shears the coupling.
- **Low water in the boiler.** The feed pump stops. Take the fire off and close the engine's stop valve before the fusible plug melts.
- **The lamp said closed.** A pressure transient lifts the pressurizer relief valve, and it does not reseat. The lamp on the panel follows the solenoid, so it goes dark as soon as the close command is sent. The position sensor on the stem and the hot tailpipe say the valve is still open. The pressure falls to saturation and the pressurizer level rises while coolant is lost: cutting the makeup because the level looks high makes it worse. Believe the position sensor and close the block valve (button 1 −) early. The simulator plant has no PLC of its own; a FAT on the engineering station checks a PLC program that closes the block valve when stem position and command disagree.

After a lesson every operator gets a debrief in the chat: when each fault came and what the copy did. A lesson passed with a score of 0.75 or more goes into the cabinet's record. With it the instructor station issues an [operator license](item:promptcritical:operator_license): trainee for one lesson, operator for all lessons of a plant, shift supervisor for all lessons. A server runs at most two simulators and four twins at once, and they spend at most 2 ms of each server tick. Short of time, the copies slow down, the world does not.
