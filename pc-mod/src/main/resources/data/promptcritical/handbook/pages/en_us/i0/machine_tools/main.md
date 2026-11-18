The lathe and the drill press finish a casting. Building machines takes more: gears, keyed shafts, slideways and flat plates. Three machine tools make them. The first is built from Create parts, and each of the others is built from what the one before it makes.

## The machines

- The [shaping machine](item:promptcritical:shaping_machine), like a 7305, planes. The ram runs back and forth, and the tool cuts only on the forward stroke; the return is half again as fast. A machine-bed casting becomes a [slideway](item:promptcritical:machine_slideway); a base-plate casting or a steel plate becomes a [planed plate](item:promptcritical:planed_plate). It uses an ordinary [cutting tool](item:promptcritical:cutting_tool).
- The [knee milling machine](item:promptcritical:milling_machine), like a 6R82, cuts with a spinning [disc cutter](item:promptcritical:milling_cutter). On a turned gear blank it cuts the teeth, which gives a [spur gear](item:promptcritical:spur_gear). In a steel bar it mills a keyway, which gives a [keyed shaft](item:promptcritical:keyed_shaft).
- The [surface grinder](item:promptcritical:surface_grinder), like a 3G71, takes a planed plate down to a [ground plate](item:promptcritical:ground_plate) with a [grinding wheel](item:promptcritical:grinding_wheel).

All three take a Create shaft on the left. A steam engine or a mains electric motor can turn it; the machine does not care which. Nominal speed is 64 rpm.

## Feed and finish

The shaft sets the cutting speed. The Feed button on the panel sets the feed, in four steps. A fine feed leaves a clean surface and cuts slowly. A coarse feed is fast and leaves ridges.

Ridge height grows as the square of the feed: halve the feed and the surface comes out four times smoother. From the roughness Ra the machine also gives the IT tolerance grade. Both show in the part's tooltip.

Each part needs its own finish:

- gear teeth: Ra 3.2 µm or finer;
- a keyway: 6.3;
- a slideway: 3.2;
- a planed plate: 12.5 is enough, the wheel takes off the rest;
- a ground plate: 0.8.

A part rougher than its limit goes to scrap. Scrap goes back to the furnace.

## Tools

Cutters and tools wear by Taylor's law, as on the lathe. Double the shaft speed and a cutter lives 256 times shorter; at four times the speed it burns out in seconds. A worn edge also tears the surface: near the end of its life Ra is half again as rough.

A grinding wheel does not wear by Taylor's law. It loads up: the grains rub down into flats, and the flats rub on the metal. A loaded wheel heats the work until it burns, and a burnt plate is scrap. A deep pass burns even with a fresh wheel; a down-feed of 0.04 mm per pass is already past the limit.

The wheel is rated for 35 m/s, and the maker proof-tests it at one and a half times that. Run the shaft above 95 rpm and your wheel fails that test. The pieces fly three blocks.

## Castings for the shaper

The casting pattern has two new shapes, a machine bed and a base plate, both in grey iron. A shrinkage cavity or porosity opens up under the tool and the part goes to scrap, so cast for the machine shop as carefully as for a valve body.

## Where the parts go

Gears go into the pinion stand of the rolling mill. Keyed shafts go into the generator and the electric motor. Ground plates clamp the pipe on the hydrotest bench. Slideways and gears go into the machine tools themselves: the milling machine is built on slideways, the grinder on slideways and gears.
