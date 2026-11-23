A power grid is generators, busbars, lines, transformers and loads tied into one circuit. At every moment the generators deliver exactly what the load takes: there is nowhere to store alternating current. Any imbalance shows at once in the frequency. More load and the machines slow down, the frequency falls. Less, and they speed up.

## Busbars and cables

[Power cable](item:promptcritical:power_cable) and [busbar](item:promptcritical:busbar) are conductors without resistance. A connected group of them is one node of the grid, together with the terminals of every device it touches. Being adjacent is being connected.

The voltage classes of this era are **0.4 kV** and **6.3 kV**. Every block is built for its class. Loads tolerate ±10 %, generators ±5 %. A 0.4 kV motor on a 6 kV bus breaks down the first time it is energized: its insulation is rated many times lower. A transformer stands between classes.

## Switching

- The [6 kV switchgear cell](item:promptcritical:switchgear_cell), 1×3×1 with the busbar compartment on top, is an oil circuit breaker. The bus is on its side faces: a row of cells makes the busbar. The feeder is the back face and the top. It closes in 0.45 s, opens in 0.1 s and interrupts up to 20 kA.
- The [0.4 kV switchboard](item:promptcritical:lv_switchboard) is an air circuit breaker with a trip unit: thermal against overload, instantaneous against short circuit. Incoming at the back, outgoing at the sides and top. The trip rating is a panel button.
- A [distribution box](item:promptcritical:lv_distribution_box) splits the 0.4 kV network into sections with a [fuse](item:promptcritical:lv_fuse). A blown fuse is replaced by hand; Shift + right-click pulls it out for a visible break.
- A [disconnector](item:promptcritical:disconnector), 3×2×1, gives a visible break but cannot interrupt load current. Opened on load, its blades draw an arc: burns nearby and an even chance of a phase-to-phase fault. Breaker first, then disconnector. Shift + right-click works the earthing switch on the feeder side.

An overload in a 0.4 kV section trips only that section's breaker or fuse. Rate the switchboard one step above the sections, or the whole board goes dark.

## The transformer

The [6/0.4 kV transformer](item:promptcritical:distribution_transformer), a 630 kVA unit, 1×2×1: the upper block is the 6 kV side, the lower one the 0.4 kV side. The off-load tap changer, ±2×2.5 %, raises or lowers the 0.4 kV voltage, and only with the transformer de-energized. An overloaded transformer heats up; its panel shows loading and winding hot spot. Hot insulation ages fast.

## Overhead lines

[Wooden](item:promptcritical:wooden_pole) and [concrete](item:promptcritical:concrete_pole) poles are stacked, a [6 kV line insulator](item:promptcritical:wire_connector_mv) goes on top, spans up to 32 m. A [0.4 kV connector](item:promptcritical:wire_connector_lv) mounts on a block face, spans up to 16 m. Wire is strung from a coil: click one connector, click the other, one coil per span. [Self-supporting cable](item:promptcritical:wire_coil_lv) is insulated and for 0.4 kV only. [Bare conductor](item:promptcritical:overhead_conductor) serves 0.4 and 6 kV. A bare conductor touching a block or leaves faults to earth: keep the space under the span clear and allow for sag.

Current and sun heat the wire; it lengthens and sags. Above 90 °C copper anneals and loses strength; an overheated wire breaks and falls to earth. Lightning strikes tall lines.

In a thunderstorm every span catches strokes, the more the higher it hangs. A stroke to the wire always flashes the insulators over. The span trips and usually comes back on reclosing. A stroke to the ground nearby induces 30·I·h/d kV, enough to flash over a 6 kV line within tens of metres. A lightning rod on top of a pole takes the stroke into the pole. That helps only with a [ground electrode](item:promptcritical:ground_electrode) under the pole base, because the voltage is current times earth resistance. One rod is 33 Ω in loam, 10 Ω in clay and over 300 Ω in sand or rock, and more rods lower it. A flashover sends a 60 kV wave along the wire to the transformers at its ends. One without a [surge arrester](item:promptcritical:surge_arrester) touching it burns out one time in five and keeps a short circuit in its tank until it is replaced.

## Loads

The 3×3×3 [settlement load](item:promptcritical:grid_consumer) is a 6 kV substation; the cable goes to any outer face. Its panel sets demand, 50–2000 kW, and its main switch. The [electric motor](item:promptcritical:electric_motor) is a Create rotation source fed from the grid: 100 kW at 0.4 kV or 1 MW at 6.3 kV. Starting current is several times rated: a weak grid sags on a start, and an overloaded motor stalls. An [energy meter](item:promptcritical:energy_meter) on a cell counts kWh both ways.

A starter goes into the third place of the motor panel; without one the motor starts direct on line at 5–7 times rated current. A [star-delta starter](item:promptcritical:star_delta_starter) starts the windings in star, at a third of the current and of the torque, and switches to delta at 90 % speed with a short kick. It is built for 0.4 kV only, and a heavy load will not start on a third of the torque. A [soft starter](item:promptcritical:soft_starter) raises the voltage over 10 s and holds the current at 3 times rated. The dip on the bus is small, but torque falls with the square of the voltage, so a heavily loaded machine may never break away. A [frequency drive](item:promptcritical:variable_frequency_drive) takes no more than 1.5 times rated current, gives rated torque from standstill and turns the shaft at any speed up to the gear, set with RPM − and RPM +.

## Frequency and reserve

The generator drive has droop: as load grows, frequency sags a little and the machines share the load between them. Bringing it back to 50 Hz is the operator's job, with the generator trim. Generators need **reserve**: a machine at its limit cannot add power, and a load step pulls the frequency down until load shedding acts.

## Capacitor bank

Induction motors draw reactive power from the grid to magnetise themselves: at a power factor of 0.88 that is more than half the active power. Generators have to produce it and lines have to carry it, which means losses and voltage drop. A [capacitor bank](item:promptcritical:capacitor_bank) supplies reactive power on the spot: steps of 150 kvar at 6.3 kV switch with a right-click, and the output grows as U² with voltage.

Do not install more than the grid can take. An idling generator absorbs at most S/x_d: about 250 kvar for a 500 kVA machine with x_d = 2. More capacitance than that excites the generator by itself, the voltage runs away, and the winding breaks down. Switch steps in under motor load, not onto an empty bus.
