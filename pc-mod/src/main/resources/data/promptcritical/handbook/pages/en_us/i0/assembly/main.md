A machine does not come off a crafting table: you build it from parts on the [assembly stand](item:promptcritical:assembly_stand), and it runs the way it was built. A bad shaft wears its bearing out three times faster, and an unbalanced impeller shakes the pump from day one.

## The spec

Pick a machine with the arrows on the panel. Each machine has a spec: which parts, how many, and what material. Put the parts on the plate cells in the order of the positions. The line at the bottom names the first position that is missing something.

The stand rejects:
- the wrong part;
- a material outside the group: a pump casing must be grey cast iron;
- a crack or a burnt part: a shrinkage cavity or a hot tear in a casting counts as a crack.

Everything else passes and goes into the score.

## What goes into the passport

- **Bearing seat.** A journal coarser than IT7, softer than 30 HRC or not quenched and tempered makes the bearing wear faster. A raw forged shaft that was never turned is IT14, and a bearing on it lives three to four times shorter.
- **Balancing.** The stand balances the rotor by hand on knife edges, which is grade G16 in ISO 21940-11, so a pump starts at about 2 mm/s of vibration. A porous impeller casting adds to that.
- **Alignment.** The bed and the coupling: an unmachined bed and buckling give misalignment.
- **Seals.** Defective packing leaks from the first start.
- **Winding.** Insulation resistance and efficiency come from the stator.

The lamps on the right light up when every fit is good, the fasteners are in place, and the vibration is below the first stage.

## Run-in

The Assemble button takes the parts and starts a no-load run-in. Twenty seconds later the machine appears in the brass ring, with its passport in the tooltip. When you place the machine, the passport sets the starting state of the maintenance panel, and when you break it, the machine keeps the passport.

## Where the parts come from

- Pump casings, beds, valve bodies, pulleys and end covers are cast in the foundry and turned on the lathe. A turned casting becomes a part on the crafting table: turning gives IT11, turning plus drilling gives IT10.
- The 0.4 kV electric motor is no longer crafted either: it is assembled from a wound stator, a rotor core, a shaft, two rolling bearings, two end covers, a fan wheel and fasteners. Core losses in its passport make it draw more from the grid for the same load.
- Shafts are forged on the anvil, and impellers are cast. Before you have a lathe, a file brings a soft forged shaft to IT13: it runs, but its bearing seat is rough, so the machine wears faster. The lathe turns a soft forged shaft (up to 35 HRC) to the grade its tool and feed give, about IT9. A keyed shaft from the milling machine and a gear cut from a casting carry the grade and finish of the machine that made them.
- Fasteners, bearings, couplings, springs and flanges are made from steel on the crafting table for now.

## Inspection

A new part says "Not inspected": its true grade, hardness and flaws are already in the passport, but nobody has measured them yet. Hold the part in your off hand and use a tool:

- **Caliper.** Reads to 0.1 mm, enough to prove a fit down to IT12. Forged and as-cast sizes are fine; a turned casting at IT11 is too fine for it.
- **Micrometer.** Reads to 0.01 mm and proves fits down to IT6.
- **Hardness tester.** Needed only after heat treatment.
- **Radiography camera.** Castings and welded parts need a film: pores, slag and lack of fusion hide inside the wall.

Each reading shows in the tooltip at once. When the last check the part needs is done, the passport is marked inspected.
