A hydro plant turns falling water into work: power is η·ρ·g·Q·H, efficiency times density times g times flow times head. Terrain gives the head; a river gives the flow.

## Building one

1. Put a [dam intake gate](item:promptcritical:dam_gate) in water: a pond, a lake or a river higher up the slope.
2. Put the [hydro turbine](item:promptcritical:hydro_turbine) lower down, at least 3 blocks below the water surface and within 48 blocks of the gate. Its Create shaft comes out of the top, and a wrench changes the ratio (64, 128 or 256 rpm).
3. Right-click the gate to raise it. The turbine finds it by itself: head is the water height above the turbine, penstock length is the distance to the gate.

Right-clicking the turbine shows head, flow, power and runner type. Kaplan suits low heads up to 40 m, Francis up to 300 m, Pelton above that.

## Where the water comes from

A river biome gives a catchment of 20 to 400 km², a pond or brook 2 to 10 km². River flow follows the weather. Rain reaches the river a few days later; in winter precipitation piles up as snow, and in spring the snow melts and brings a flood. At low water the turbine cannot open its wicket gates wider than the river allows.

## Water in the penstock

Water in the pipe is heavy and takes time to accelerate. That time is Tw = L·Q/(g·A·H), usually a fraction of a second or a few seconds. So when the wicket gates open, power first drops and only then rises. An ordinary governor hunts on such a unit, which is why hydro plants use a governor with temporary droop.

Close the gates fast and the moving water slams into them: water hammer. The gates close no faster than 5 s, and on a short penstock that is enough. On a long penstock (Tw of several seconds) a sudden load rejection bursts the pipe. Replace a burst penstock with Shift and right-click on the turbine. On a full load rejection the speed jumps by tens of percent, which is normal for hydro.
