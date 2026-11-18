## Synthesis converter

The [ammonia synthesis loop](item:promptcritical:ammonia_converter) is a thick-walled vessel with three beds of iron catalyst. A Create shaft at the back drives the makeup compressor, the circulator and the ammonia chiller. Pipe the synthesis gas in and take liquid ammonia from the same face.

N₂ + 3H₂ ⇌ 2NH₃ shrinks the gas volume and releases heat, so there is more ammonia at higher pressure and lower temperature. A cold catalyst is slow, though, and the converter runs at 400–500 °C. At 450 °C and 300 atmospheres the equilibrium holds about 36 % ammonia, at 10 atmospheres 2 %. This is the Larson and Dodge table, and the model is tested against it. One pass brings the gas to 12–18 % ammonia. Every percent heats the bed by about 15 K, so between the beds the gas is cooled back to the set inlet temperature. Above 530 °C the iron recrystallises and loses activity.

## The loop

Nobody sets the loop pressure, it finds its own level. Makeup gas the converter cannot turn over piles up and raises the pressure until output matches feed, usually at 15–25 MPa. After the converter the gas is cooled by water to 35 °C and by the ammonia chiller to the set temperature. Ammonia condenses and drains into the separator. The colder the separator, the less ammonia goes back to the converter and the more power the chiller takes.

Methane and argon in the makeup gas never become ammonia. Without a purge they build up: the pressure reaches the 32 MPa limit, the makeup compressor spills back and production dies. Keep the purge so that the loop holds 10–15 % inerts. The purge gas burns in the nearest steam reformer within 24 blocks and saves it sales gas; with no reformer around it goes to the flare.
