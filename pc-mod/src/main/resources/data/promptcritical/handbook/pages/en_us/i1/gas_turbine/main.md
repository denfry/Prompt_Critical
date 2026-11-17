A [gas turbine unit](item:promptcritical:gas_turbine) burns fuel gas right in the airflow: the compressor squeezes air sevenfold, the combustor heats it to 850 °C, and the turbine expands it again. Half of the turbine's work drives its own compressor, the rest goes to the shaft. It needs no boiler and no water, and it goes from cold to load in a minute.

## Assembly

The unit takes 3×3×9: compressor, combustor, turbine. Pipe fuel gas (mine methane or coke-oven gas) to any of its cells. Put the [gearbox](item:promptcritical:turbine_gearbox) right against the compressor with its back face: a Create shaft comes out of its front at 64, 128 or 256 rpm (change with a wrench).

Right-clicking the unit starts and stops it. A starter spins the rotor up to 25 % speed, the gas lights, and the turbine accelerates on its own. Shift and right-click resets the overspeed trip, which fires at 110 %.

## Air decides

The 3 MW rating is at 15 °C and sea level. A compressor swallows volume, not mass, so hot or thin air gives less: at +35 °C the unit loses about 13 %, at 1000 m about 11 %. On a −10 °C winter day it delivers 17 % more than its rating. The exhaust temperature limit will not let you feed more gas than the air allows.

Simple-cycle efficiency is about 28 %. The exhaust leaves at 470 °C, heat a waste-heat boiler can use.

The speed governor holds speed with a 4 % droop. If the gas runs out, the flame goes out and the rotor coasts to a stop.
