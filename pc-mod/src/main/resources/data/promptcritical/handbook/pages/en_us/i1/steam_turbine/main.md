The [steam turbine](item:promptcritical:steam_turbine) replaces the reciprocating engine where you need a lot of power and a steady speed. Steam speeds up as it expands through the nozzles and drives the rotor blades. A piston passes two dead centres per revolution; a turbine has none. This is a back-pressure turbine: it has no condenser, and the exhaust steam leaves at 0.12 MPa for heating, a deaerator or the atmosphere.

## Connecting it

Place the turbine right against a boiler: the drum, water wall or panel of a water-tube boiler, or a fire-tube boiler. The Create shaft comes out of the front through a built-in reduction gear: the turbine's 3000 rpm become 64, 128 or 256 rpm, and a Create wrench changes the ratio. Right-click opens and closes the stop valve.

## What it delivers

Design point: 1.3 MPa and 250 °C steam from a DKVR boiler, 750 kW on the shaft at about 2.5 kg/s (9 t/h), almost the whole output of one boiler. Flow follows Stodola's ellipse: the lower the boiler pressure, the less steam passes and the less power you get. The work of one kilogram of steam is the IF97 enthalpy drop from live steam to exhaust, times a stage efficiency of 0.65. If the exhaust is wet, droplets brake the blades: Baumann's rule takes 1 % of efficiency for every percent of moisture.

The blades are designed for rated speed. At low speed the steam flows over them less well, but the starting torque is twice rated, so the turbine starts under load.

## Governor and overspeed trip

A centrifugal governor holds speed with a 5 % droop: 100 % speed at full load, 105 % at no load. Throw off the load suddenly and the speed jumps, but the governor closes the valve in time.

If the speed still reaches 110 %, the overspeed trip fires: the stop valve shuts and the turbine runs down. Reset the trip with Shift and right-click, and only once the speed is below rated. On turbines of that era, a rotor that burst at runaway went through the casing.

## Condenser

Put a [condenser](item:promptcritical:condenser) under the turbine so that it touches water: a river, pond or lake. The steam then exhausts into vacuum instead of the atmosphere. Condenser pressure is the saturation pressure at the water temperature plus about 13 K (water rise and terminal difference): about 4 kPa with 15 °C water. The same steam then expands through 870 kJ/kg instead of 485, and the shaft gets roughly twice the power.

River water is warmest at the end of summer, and the vacuum is worse then: the plant loses a few percent. In winter the water is near zero and the vacuum is best. Right-clicking the turbine shows the exhaust pressure.

## Cooling tower

With no river nearby, put the condenser right against a [cooling tower cell](item:promptcritical:cooling_tower_cell). Water in it cools by evaporation, and it cannot get colder than the wet-bulb temperature: in dry heat at 30 °C and 40 % humidity that is about 20 °C. The approach adds about 5 K on top, more under higher load. Right-click switches the fan; without it the draft is natural and the approach triples. Every 5 MW of heat evaporates about 2 kg of water per second. In frost a cell with its fan running ices up.
