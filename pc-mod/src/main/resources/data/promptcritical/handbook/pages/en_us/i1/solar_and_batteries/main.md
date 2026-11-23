## Solar panels

A [solar panel](item:promptcritical:solar_panel) is one square metre of cells, 15 % efficient. The sun of this world rises in the east and goes over the zenith to the west, so a flat panel catches the most. At noon in a clear sky a panel gives about 140 W; hot cells give less, about 0.4 % per degree above 25 °C.

A [solar inverter](item:promptcritical:solar_inverter) of 100 kW gathers every panel within 8 blocks and feeds the 0.4 kV grid. Run a cable to any face but the front. The inverter follows the grid: it needs a live bus with a generator on it, trips in 0.2 s when the voltage goes and comes back five seconds after it returns.

- **Shade.** A panel with no open sky above it is shaded, and a shaded panel drags its whole string down.
- **Weather.** Clouds drift in over hours: deserts are clear 90 % of the time, plains 60 %, jungles 40 %. Rain leaves 15 % of the direct sun, a thunderstorm 5 %.
- **Dust and snow.** Dust builds up, fastest in deserts and badlands, and rain washes it off. Snow covers the panels until the air is above 2 °C.

A [tracking panel](item:promptcritical:solar_panel_tracker) turns east to west with the sun while a [tracker drive](item:promptcritical:solar_tracker_drive) within 8 blocks of the inverter turns on a Create shaft. One drive turns up to 100 panels and takes about 750 W from the shaft. Over a clear day a tracker gathers about a third more than a flat panel. Under clouds it aims a little off and loses 2 %. With the shaft stopped the panels lie flat.

## Grid battery

A [grid battery rack](item:promptcritical:grid_battery_rack) holds 250 kWh of lead-acid cells. A [battery inverter](item:promptcritical:battery_inverter) of 500 kW on the 6.3 kV bus takes up to 40 racks within 4 blocks. Each rack gives at most a quarter of its capacity per hour and charges at a tenth. About 78 % of the energy put in comes back out.

Right click switches the mode: off, charge, discharge or frequency regulation. In regulation the inverter answers a frequency drop in a fifth of a second, faster than any turbine governor. It gives full power at 2 Hz off and adds synthetic inertia. It also drifts the battery slowly back to half charge.

Deep discharges wear the cells faster than shallow ones, and capacity falls as the cycles add up. Shift + right click shows the charge, the wear and the hydrogen in the room.

Charging above 90 % gives off hydrogen into the room around the inverter. Hydrogen burns in air from 4 % (the lower explosive limit, LEL). A room open to the sky changes its air about six times an hour and stays safe. A sealed room changes it only about once every two hours, and a long top-up charge can fill it. A running portable ventilator in the room adds its own airflow. At a quarter of the LEL the gas detector rings a bell. Above the LEL, the next contactor spark while the battery is charging sets the mix off.
