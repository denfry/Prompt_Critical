Associated gas from the separator is rich and wet. Up to a quarter of it is ethane, propane and butanes, and it is saturated with water. You can burn it on a flare, but splitting it pays better: dry gas for turbines and furnaces, and propane, butane and natural gasoline as products.

## The LTS unit

The [low-temperature separation unit](item:promptcritical:low_temperature_separator) takes associated gas through pipes. Within 6 blocks of it you need a [gas compressor](item:promptcritical:gas_compressor) on a Create shaft and a [glycol regenerator](item:promptcritical:glycol_regenerator). The compressor raises the gas from 0.7 to 7 MPa in two stages, an air cooler brings it down to 35 °C, and some water drops out. Triethylene glycol in the contactor takes up the rest. The hotter the regenerator reboiler, the drier the glycol: 98.8 % at 204 °C, which brings the gas dew point down to about −12 °C.

A propane chiller then cools the gas further, and it is throttled through a choke. Expanding, it cools by another 10–15 K: the Joule–Thomson effect. Propane, butanes and heavier condense and are collected as NGL. Sales gas leaves through a pipe on top, NGL from the sides, water from the bottom. Sales gas burns in the [gas turbine](item:promptcritical:gas_turbine) and in furnaces.

## Hydrates

At high pressure and low temperature, water with methane and ethane forms hydrates: ice with gas locked inside its lattice. At 3.5 MPa a rich gas forms hydrates already around +14 °C. Chill the gas below its dew point after the glycol and free water drops out, and a hydrate plug grows in the choke. Once it blocks the choke the unit stops.

The cure is methanol injected ahead of the chiller. By Hammerschmidt, 20 % methanol in the water lowers the hydrate temperature by about 10 K. The Methanol button turns on dosing, and the unit keeps a 3 K margin. Methanol comes from dry distillation of wood in a heated Create mixer. A plug melts once the chiller is set above the hydrate temperature or methanol arrives.

## NGL fractionation

The [NGL fractionation unit](item:promptcritical:ngl_fractionator) splits NGL in three columns. The deethanizer strips methane and ethane, which burn in the reboilers. The depropanizer makes propane overhead, the debutanizer makes butanes, and natural gasoline is left in its bottoms. Sales gas piped to the unit heats the reboilers. Reflux on the panel sets the purity: with 15 trays at reflux 2, propane comes out better than 95 %.

## Centrifugal compressor and surge

The [centrifugal compressor](item:promptcritical:centrifugal_compressor) of a gas pipeline station takes gas from a 5 bar header and pushes it into an 8 bar line. The Create shaft enters its front face; 64 rpm is full speed, and the rotor needs 20 s to follow the shaft. At full speed and with the line valve at 85 % it delivers about 1 m³/s of suction gas at 8.7 bar for some 400 kW.

Each speed of a centrifugal machine has a peak of pressure. Left of it, at too small a flow, the machine cannot hold the pressure it built: the flow breaks down and reverses, the discharge header blows back through the wheel, refills, and the cycle repeats every second or so. This is **surge**. Every cycle bangs in the casing, shakes the rotor above 11 mm/s and wears the bearing ten times faster; two minutes of surge in total wreck the machine. The panel counts surge cycles.

The line of peaks is the **surge line**. In reduced coordinates it does not depend on the gas: q²_r = Δp_o/p_s (orifice differential at the suction in kPa over suction pressure in bar) against the pressure ratio R_c = p_d/p_s. For this compressor the data sheet gives q²_surge = 2.489 × (R_c − 1). The surge ratio S_s = q²_surge/q²_r is 1 on the surge line.

The defence is the **recycle valve** from the discharge back to the suction, driven by the anti-surge controller `PC_ANTISURGE`. It keeps S_s below the control line 1/(1 + b_ctrl): it opens the valve at once and closes it no faster than the closing rate. When S_s crosses the recycle trip line 1/(1 + b_rtl) it adds 30 % of stroke in the same scan, and 60 % if that happens again within 10 s. A detected surge widens b_ctrl by 2 %. The compressor has this controller built in: its panel switches it to manual, sets the margin and the closing rate, and the line valve. A safe start: recycle open, shaft up to speed, then open the line valve and let the controller close the recycle slowly. Put the compressor on line without a single surge cycle for the *No Surge* advancement.
