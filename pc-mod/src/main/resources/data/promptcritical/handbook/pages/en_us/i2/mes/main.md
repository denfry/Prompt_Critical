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
