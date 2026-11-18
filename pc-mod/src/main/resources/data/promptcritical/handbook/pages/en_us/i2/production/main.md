A drilled bore is not yet a well. It has to be completed: cased, perforated and hooked up.

## Completion

Place a [wellhead](item:promptcritical:wellhead) exactly where the rotary table stood: the rig logged the bore, its depth and the pay below it. Run the production casing by using [seamless pipe](item:promptcritical:seamless_pipe) on it, one per 12 m to the bottom. Then perforate the oil-bearing part of the pay with [seismic charges](item:promptcritical:seismic_charge), one per metre of gun. The casing seals off everything except the perforated interval, so the gas above the oil and the water below it stay out. Put a [Christmas tree](item:promptcritical:christmas_tree) on the wellhead.

## Natural flow

As long as reservoir pressure can push the column of oil and gas up to the surface, the well flows. Inflow follows Vogel's curve. Above the bubble point the rate is proportional to drawdown; below it gas comes out of the oil and the curve bends over. The choke limits the rate: Gilbert's correlation puts the tubing head pressure up with rate and gas-liquid ratio and down with a wider bean. The Choke buttons change it in 64ths of an inch.

As the field is produced, pressure falls, the gas-oil ratio climbs and water moves in. Sooner or later the well stops flowing.

## Pumpjack

A [pumpjack](item:promptcritical:pumpjack) within 4 blocks of the wellhead lifts the oil with a rod pump. Drive the high-speed shaft of its gearbox with Create: 64 rpm gives 8 strokes a minute. The crank and walking beam form a four-bar linkage that moves the polished rod 1.9 m. The pump draws down to a minimum intake pressure, so if it displaces more than the well gives, the barrel only partly fills.

The wellhead panel draws the dynamometer card, rod load against position. With a full pump it is nearly a parallelogram. Load builds at the start of the upstroke while the rods stretch, then drops at the start of the downstroke. With a half-full barrel the load hangs on during the downstroke until the plunger hits the liquid. That is fluid pound: the card narrows and each stroke does less work.

The card comes from the rod string itself: eight masses on springs with damping, pulled by the beam at the top and loaded by the pump at the bottom, 200 points a stroke. At the bottom of the stroke the plunger waits while the rods stretch to lift the fluid, so the load climbs before the polished rod gets far. Free gas that the gas anchor does not send up the annulus takes room in the barrel and the load comes off gradually instead of with a bang; with almost no liquid the travelling valve never opens and the card goes thin: gas lock. A worn travelling valve leaks, and the top line of the card slopes down.

The crank counterweights balance the rods and half the fluid. The Weights buttons move them 2 kN at a time. The gearbox has to deliver the peak torque of the stroke, torque factor times rod load minus counterweight; an unbalanced unit pulls two or three times as much from the Create network, and above the 25.8 kN·m rating of an API 228 gearbox the teeth break. Rods part when the peak stress goes past the modified Goodman line of grade D rods. Repairs are done on the wellhead: a [sucker rod](item:promptcritical:sucker_rod) after a rod part, a [downhole rod pump](item:promptcritical:downhole_rod_pump) for new valves, a large cogwheel for the gearbox.

The Echo button fires a shot down the annulus. The echo from the liquid level comes back after t seconds, and the level is at z = c·t/2, c being the speed of sound in the casing gas, about 400 m/s.

## Gas lift

Instead of a pumpjack, gas can lift the oil. [Gas lift valves](item:promptcritical:gas_lift_valve) go into the tubing, up to six. Each one carries its depth and its opening pressure: use it in the air to step the depth by 100 m, with Shift to step the opening pressure by 0.5 MPa. Use it on the wellhead to install it, and use one with Shift to pull the tubing and get all valves back.

The Gas buttons set the injection in steps of 0.05 nm³/s. The gas comes from the separator and comes back with the production, so the separator needs some gas to start. At start the annulus is full of kill fluid. 8 MPa of gas pushes it down to the top valve if the gas column there is above the liquid in the tubing plus 0.7 MPa across the valve. Then the well flows through that valve, the tubing pressure below falls, and the next valve uncovers, as deep as the gas can reach. The working casing pressure is what the deepest open valve needs. An upper valve whose opening pressure is below the gas pressure at its depth stays open and takes the gas: the well is stuck on the top valve. Set the opening pressures to fall with depth. More gas lightens the column, until gas friction takes over.

## Separator and flare

Production goes to a [three-phase separator](item:promptcritical:three_phase_separator) within 8 blocks. Gas rises to the top, water settles to the bottom, oil floats in between. Water drops settle by Stokes' law. Small drops can't get through the oil layer in the residence time and leave with the oil, so a high rate gives wet oil. Create pipes take oil from the sides, gas from the top and water from the bottom. Gas with nowhere to go burns at a [flare tip](item:promptcritical:flare_tip). Without a flare the separator fills up and shuts the well in.
