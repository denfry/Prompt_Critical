## Seamless pipe

The Mannesmann [piercing mill](item:promptcritical:piercing_mill), 3×2×1, runs on a Create shaft through its port. Two skewed rolls spin the hot billet and draw it onto a mandrel. Squeezed this way, a round billet opens up at its centre, and the mandrel rolls the cavity out into a hollow. Elongation and sizing are folded into the same mill in the game: out comes finished [seamless pipe](item:promptcritical:seamless_pipe).

- The billet or ingot arrives on a roller table from the left, across the front.
- The piercing window is 1200–1250 °C. Below 1150 °C the mandrel will not go in.
- Piercing takes 3 seconds. Pipe in 1 m lengths, as much as the billet mass gives, leaves on the roller table to the right or into the outputs.
- Size and service are set by panel buttons: from 57×4 to 219×8 mm; line, boiler, heat exchanger, drill, casing, tubing, structural.

The wall comes out uneven. Eccentricity above 12.5 % is a defect. A porous ingot of unkilled steel gives laps. The defect is hidden: from outside the pipe looks like any other.

## Welded pipe

The [induction pipe welder](item:promptcritical:pipe_welder), 3×2×1, forms [plate](item:promptcritical:steel_plate) into a tube and welds the edges with high-frequency current. The HF generator is a rotating machine on the same Create shaft: the shaft carries both forming and welding. Wall thickness is the plate thickness; diameter comes from the panel. Out comes [welded pipe](item:promptcritical:welded_pipe) in 1 m lengths.

Weld quality depends on the settings. The optimum is 20 m/min and about 50 kW per millimetre of wall. Every departure in speed or power raises the risk of a hidden defect; the panel shows it in ppm per metre. Steel with a carbon equivalent above 0.43 (C + Mn/6 + (Cr + Mo + V)/5 + (Ni + Cu)/15) hardens at the weld: three times the risk of cracks.

## Strength and hydrotest

Yield pressure of a sound pipe is Barlow's formula:

- p = 2·σy·t / D,

where σy is yield strength, t the wall, D the outside diameter. A 219×8 pipe of X52 steel (σy 359 MPa) holds 26.2 MPa. A flaw half the wall deep brings that down to 24.9 MPa; a deep, long one to 17 MPa and below.

The [hydrostatic test bench](item:promptcritical:hydrotest_bench), 3×1×1, tests one pipe at a time. The pipe must be below 100 °C. It is capped, filled with water from a Create pipe and pressed up to 0.9 of yield pressure. If a flaw drops its strength below the test pressure, the pipe bursts on the bench and goes to scrap. A pipe that holds is marked "Passed hydrotest".

Testing with water is safe: compressed water stores almost no energy, so a burst on the bench is not an explosion. The same burst in service, under steam or gas, is an accident. Untested pipe is a lottery.

## Where pipe goes

One seamless pipe makes four Create pipes. Seamless pipe goes into the cryogenic tank, the test bench and the piercing mill itself. The test mark and steel grade show in the pipe tooltip.
