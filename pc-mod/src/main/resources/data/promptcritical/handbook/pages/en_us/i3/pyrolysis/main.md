Propane, butane and gasoline are fuels. To make plastic out of them, the molecules first have to be broken into ethylene and propylene. A pyrolysis furnace does that: hydrocarbon mixed with steam runs through a red-hot coil for a fraction of a second and cracks.

## Pyrolysis furnace

The [pyrolysis furnace](item:promptcritical:pyrolysis_furnace) is a 3×3×3 radiant box with coils. Pipe feed to any face: propane, butane, natural gasoline or straight-run naphtha. It also needs boiler feedwater for dilution steam and sales gas for the burners. Pick the feed with the "Feed" button while the furnace is stopped. Ethane does not come from pipes: the separation unit sends it back and the furnace cracks it once enough has collected.

The key setting is the coil outlet temperature (COT). At 820–850 °C and 0.2–0.5 s residence, naphtha gives about 30 % ethylene and 14–16 % propylene; ethane gives up to 50 % ethylene at 60–65 % conversion. Hotter means more ethylene and less propylene, and faster coking. Dilution steam (0.3 kg/kg for gas feeds, 0.5 for naphtha) lowers the hydrocarbon partial pressure and slows coke growth.

The hot cracked gas is quenched at once in the transfer-line exchanger. It drops to 400 °C within hundredths of a second, before ethylene can polymerise or decompose. The quench heat raises high-pressure steam, which drives the cracked-gas compressor turbine.

## Coke and decoking

Coke builds up on the coil wall. Each millimetre adds about 16 K to the metal, because the flame now heats the tube through a layer of soot. The HP alloy limit is 1100 °C; above it the furnace cuts its own feed. A run between decokes lasts 40–60 game days. The "Decoke" button switches the furnace to steam-air: the coke burns off at about 0.4 mm per hour and the wall cools down.

## Cracked gas separation

The [cracked gas separation unit](item:promptcritical:cracked_gas_unit) must touch the furnace, since cracked gas composition depends on feed and severity and cannot travel through pipes. Without it the furnace will not run. A quench tower first washes out the heavy tar and some pyrolysis gasoline. A five-stage compressor takes the gas to 3.6 MPa, caustic wash removes CO₂ and H₂S, and acetylene is hydrogenated to ethylene. Then come the columns: demethanizer (−100 °C, hydrogen and methane leave), deethanizer, C2 splitter, depropanizer, C3 splitter and debutanizer.

Take ethylene from the top, propylene from north and south, the C4 cut (40–50 % butadiene) from the east, tar from the west and pyrolysis gasoline from the bottom. Ethane and propane go back to the furnace on their own. Hydrogen and methane fuel the furnace burners, so much less sales gas is needed. Any tail gas the furnace cannot burn goes to the flare, and a lamp on the panel lights up.

## Low-density polyethylene

The [LDPE unit](item:promptcritical:ldpe_reactor) is a hyper compressor on a Create shaft plus a tubular reactor. Ethylene is compressed to 150–300 MPa, and a few tens of ppm of oxygen start the radical chain. The heat of polymerisation (3.6 MJ/kg) warms each reactor zone, and 20–30 % of the ethylene converts per pass. Product density is about 0.92 g/cm³. Pellets come out in 25 kg bags; pull them out with a hopper.

The hazard is real. Compressed ethylene above roughly 340 °C decomposes by itself, without oxygen, into soot, methane and hydrogen, releasing about 4.5 MJ/kg. The peak temperature rises with oxygen and pressure. The panel shows the decomposition limit at the current pressure, and the amber lamp lights 15 K before it. Overheat the reactor and the rupture discs burst, hot gas vents and ignites. Repair the unit with Shift + right-click after an accident.
