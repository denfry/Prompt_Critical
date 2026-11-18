An early-80s microprocessor is sand, coal, chlorine and very clean air. Here is the whole chain.

## Silicon

- [Submerged arc furnace](item:promptcritical:submerged_arc_furnace): 4 quartz and 2 coke under a graphite electrode give a 4 kg lump of 98 % metallurgical silicon. The reaction SiO₂ + 2C → Si + 2CO alone costs 6.8 kWh per kg; with losses the furnace spends about 12. That is a 3 MW load on the grid.
- [Trichlorosilane reactor](item:promptcritical:trichlorosilane_reactor): silicon in a fluidised bed with HCl at 300 °C. Liquid SiHCl₃ comes out at the bottom, hydrogen at the sides. The reaction keeps itself hot.
- [Siemens reactor](item:promptcritical:siemens_reactor): trichlorosilane and hydrogen deposit 9N silicon on glowing rods, and HCl goes back by pipe. At 120 kWh per kg this is the most expensive step.
- [Czochralski puller](item:promptcritical:czochralski_puller): the seed turns with the shaft (speed divided by 4), the pull rate is set on the panel, and the chamber is purged with argon. The window is 0.5–2 mm/min and 5–30 rpm. Leave it even once and you get a polycrystal, which you remelt. Boron is pushed into the melt by Scheil segregation, so resistivity drops towards the tail, and the tail goes back for remelting.
- [Wafer saw and polisher](item:promptcritical:wafer_saw): 50 mm wafers, polished by Preston's law. A faster shaft polishes faster.

## Clean room

The [airlock](item:promptcritical:cleanroom_airlock) faces out; the room is behind it. Build the walls from [panels](item:promptcritical:cleanroom_panel) and [windows](item:promptcritical:cleanroom_window), and put [HEPA filters](item:promptcritical:hepa_filter) in the ceiling, 1500 m³/h each while the airlock has power. Any other solid block in the shell is a leak. An open door (sneak + use) and every person inside dirty the air. The panel shows the ISO class: 5 is the old class 100.

## Planar process

On the airlock panel you set the product, lithography, oxidation and phosphorus diffusion. The wafer goes through five steps:

1. Oxidation by Deal–Grove: dry oxygen grows slowly and makes a thin gate oxide, wet makes a thick one fast.
2. Lithography. Contact printing gives 2 µm but wears the mask. Proximity gives 1.5·√(λ·gap), 2 to 9 µm. A g-line stepper with its [lens](item:promptcritical:stepper_lens) gives 1 µm.
3. Phosphorus diffusion: junction depth grows as √(Dt).
4. Metallisation: [aluminium](item:promptcritical:ferroalloy), one per 4 wafers.
5. Check: the oxide must be 15–50 nm per micron of feature, the junction 0.1–0.6 of the feature. Outside that window every die is scrap.

Good dice follow Poisson: Y = e^{−A·D₀}. Defect density rises with the particles about half a feature in size. A 29,000-transistor microprocessor at 3 µm covers 34 mm²; in an ISO 5 room more than half come out good, in ISO 7 almost none do.

## Where the parts go

[Tubes](item:promptcritical:vacuum_tube) go into A2 analog computers. [Transistors](item:promptcritical:transistor) on a [board](item:promptcritical:printed_circuit_board) are what the PLC (A3) needs. [ICs](item:promptcritical:integrated_circuit) and [memory](item:promptcritical:memory_chip) go into the RTU station (A4). With CC: Tweaked, a computer takes a [processor](item:promptcritical:microprocessor), memory and a board, and a monitor takes a [cathode-ray tube](item:promptcritical:cathode_ray_tube).
