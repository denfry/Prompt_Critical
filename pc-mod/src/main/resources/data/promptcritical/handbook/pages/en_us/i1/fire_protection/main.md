A fire at a plant starts small: a cable smoulders, rags catch fire. It has to be noticed in seconds and put out in minutes, or it spreads to the equipment next to it.

## Fire classes

What to fight it with depends on what burns (GOST 27331):

| Class | What burns | Fight with | Never with |
|---|---|---|---|
| A | wood, coal, cables | water, foam, powder | — |
| B | flammable liquids | foam, powder, CO₂ | a water jet splashes the burning liquid |
| C | gases | shut off the gas first, then powder | — |
| D | metals: sodium, magnesium, zirconium | metal-fire powder, sand | **water**: hydrogen and a flash |
| E | live electrical equipment | powder, CO₂ | water and foam: electric shock |

A fire at a sodium leak of the fast reactor is class D. A fire right next to a grid device is class E, unless the device is locked out with the absence of voltage verified.

## Detection

- A [smoke detector](item:promptcritical:smoke_detector) trips when smoke stays in the volume for more than 15 s.
- A [heat detector](item:promptcritical:heat_detector) trips at 57 °C or on a rise faster than 10 K a minute. Its element heats in the ceiling jet: excess temperature ΔT = 16.9·Q^(2/3)/H^(5/3), velocity u = 0.96·(Q/H)^(1/3) (Alpert). The higher the ceiling, the later it trips.
- A [flame detector](item:promptcritical:flame_detector) sees an open flame in a straight line and trips in 3 s.
- A [manual call point](item:promptcritical:manual_call_point): right-click. Reset with Shift and right-click.

The [fire alarm panel](item:promptcritical:fire_alarm_panel) collects the detectors within 16 blocks. It declares a fire only on **two** detectors, since one can trip falsely. A manual call point declares it at once. On a fire the panel opens the deluges of its zone and releases the gas after 30 s of siren. With no detector in the zone it shows a fault. Reset it with a right-click once everything is out.

## Fire fighting

- A [sprinkler](item:promptcritical:sprinkler_head) hangs over the protected spot. It holds a glass bulb rated at 68 °C. Over a 1 MW fire under a 6 m ceiling the bulb bursts in about 36 s, and the head pours water over 12 m² around it: 6.1 mm a minute, 73 L a minute. Pipe the water in. Replace a burst bulb with a right-click once there is no fire nearby.
- A [deluge nozzle](item:promptcritical:deluge_nozzle) is always open; the panel lets the water into it. It sprays its whole group at once. This is how vessels next to a fire are cooled.
- A [gas suppression cylinder](item:promptcritical:gas_suppression_cylinder) protects switch rooms and control rooms. The gas puts out class A, B and E fires, but it cannot be breathed: CO₂ at the needed concentration is deadly. A 30 s siren sounds before the release; leave in that time. While people work in the room, block the module by hand (right-click) and lock it out with a padlock. Recharge a discharged module with Shift and right-click.
- A [fire monitor](item:promptcritical:fire_monitor): a right-click starts a water jet 20 blocks along its face, 1000 L a minute from a pipe.
- Extinguishers: the [ABCE powder](item:promptcritical:fire_extinguisher) one suits almost everything except metals; the [water](item:promptcritical:fire_extinguisher_water) and [foam](item:promptcritical:fire_extinguisher_foam) ones are not for metals or live equipment; the [CO₂](item:promptcritical:fire_extinguisher_co2) one is for liquids and electrical equipment; the [metal](item:promptcritical:fire_extinguisher_metal) one is for sodium. A right-click on a fire puts it out within a block. A charge lasts ten uses.
