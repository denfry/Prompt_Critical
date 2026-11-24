# Greenhouse on waste heat

A steam turbine throws most of its fuel's heat into the condenser water. A greenhouse can catch some of it.

## Heating coil

Place the coil inside a glazed room, touching the condenser under a turbine. The room is found by filling its air from the coil, like a ventilation zone, up to 4096 blocks. Every air face against glass counts as a square metre of single glass, U = 5.8 W/(m²·K); any other face is an insulated wall, 0.5. Air also leaks out at half a room volume per hour.

Losses are U·A·(T_in − T_out) plus 0.33·n·V·(T_in − T_out). A 10 × 4 × 10 m house with 260 m² of glass loses 44 kW at 18 °C inside and −10 °C outside. The coil takes what it needs from the condenser, up to 200 kW, and holds 18 °C. Cooling the water by 10 K, 44 kW needs about 1.05 kg/s of it. With the turbine stopped there is no heat, and the house cools toward the outside air.

Right-click the coil for the volume, glass, inside and outside temperatures, heat and growth.

## Growth

Plants grow by the cardinal temperature curve: nothing below 5 °C or above 36 °C, best at 24 °C. Inside, growth runs at 1 + 0.5·f(T); at 18 °C that is ×1.44. Below 5 °C nothing grows inside. Outside greenhouses vanilla growth is untouched.

With a seasons mod (Serene Seasons or Ecliptic Seasons) a warm greenhouse lets crops grow in winter at their normal chance.

## Cold frame

A cold frame over a crop has no heating: by day the crop sits 8 K warmer than the air, at night it is as cold as outside. It speeds growth by the same curve and stops it below 5 °C.
