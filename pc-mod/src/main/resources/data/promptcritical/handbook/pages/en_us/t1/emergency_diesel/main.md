After a shutdown the reactor keeps heating with decay heat for a long time, so the emergency cooling pumps must always turn. While the grid is up, station service motors drive them. When the grid is lost, the emergency diesel generator takes the whole safety load. If it fails too, the plant is in station blackout.

## What the diesel generator does

The [emergency diesel generator](item:promptcritical:diesel_generator) is a diesel engine and a 750 rpm generator on one bedplate, 1.25 MVA (a 1 MW engine). It waits in standby and does nothing while the bus is live. The Auto key on its panel arms the undervoltage protection: a bus below 0.7 of rated for 1 s is a loss of offsite power, and the diesel starts by itself.

The start runs as at a real nuclear plant:

1. Compressed air from the bottles turns the shaft up to 120 rpm (2–3 s). The bottles hold five starts, and the running engine refills them.
2. The engine fires and the governor opens the fuel rack. Power is limited by the boost: the turbocharger lags by about 1.5 s.
3. At 90 % speed the field is switched on from the battery. When speed and voltage are within limits, the unit is ready.
4. The ready unit closes its breaker onto the dead bus without synchronizing. The standard is 15 s from the start signal; it usually takes 10–12.

On a live bus the breaker will not close, neither by itself nor by hand. To run the unit in parallel with the grid, connect it through a [switchgear cell](item:promptcritical:switchgear_cell) with synchronizing and switch the governor on the panel from isochronous to droop.

## Failure to start

On average a diesel fails to start once in 200 demands. A failure is more likely when:

- the unit has not been tested for more than 30 days: every 30 days overdue adds four more base probabilities;
- the fuel has a cetane number below 45. Below 38 the engine fails about three times in ten;
- it is colder than 5 °C and there is no [jacket heater](item:promptcritical:jacket_heater): a failure is four times as likely.

After a failure the automatics try again in 5 s, as long as there is air. When the air runs out, the unit locks out. The Reset button on the panel clears the lockout. Every good start counts as a test. Start the unit once a month, as the plant does.

## Control power and fuel

Without DC the starting valves do not open and the generator does not excite. A [station battery](item:promptcritical:station_battery) must touch the unit. While the diesel runs, the battery is charged.

Pipe diesel fuel to any part of the unit. The 2000 L day tank lasts about 8 hours at full power: at rated load the engine burns about 205 kg an hour. Specific consumption is lowest at rated load and rises towards no load.

## Load sequencer

Throwing the whole load on in one step pulls the frequency down: the boost cannot keep up and the speed dips below 95 %. A [load sequencer](item:promptcritical:load_sequencer) in the unit's cabinet disconnects the motors and consumers of the island before the breaker closes, then reconnects them one at a time every 5 s, the largest first. The largest are usually the safety pumps.

## How to build

1. Place the unit, run a cable from the top, a side or the end of the generator to the essential bus: the motors of the ECCS and service water pumps.
2. Put a station battery against the unit and fill it with diesel fuel.
3. Fit the jacket heater and the sequencer (right-click the unit with the item).
4. Turn the key to Auto and test the unit with the Start button.
