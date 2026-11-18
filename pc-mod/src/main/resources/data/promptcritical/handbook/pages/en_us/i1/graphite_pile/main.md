A graphite pile is the simplest reactor there is: lumps of natural uranium metal sit in channels cut through a stack of very pure graphite, and nothing else. No pumps, no pressure, no coolant but the air in the hall. Chicago Pile 1 was built this way on a squash court in 1942. It ran at half a watt.

Natural uranium is only 0.7 % uranium-235, so it multiplies neutrons only barely. Graphite slows them without eating many. The game has no ready-made "reactor" block: the core takes the pile you lay, block by block, and solves for the neutron flux in it. Whether it goes critical depends on its size, its shape and how much fuel you load.

## The chain of supply

- **Pitchblende** occurs in hydrothermal veins between Y −48 and 32, alongside galena, pyrite and quartz. An iron pickaxe at least.
- **Crushing and milling**: Create crushing wheels make crushed ore, the millstone grinds it fine.
- **Yellowcake**: two ground ore, one ammonium sulfate (from the coke-oven by-product plant) and water in a heated mixer.
- **Uranium dioxide**: yellowcake through a vanilla Blast Furnace block.
- **Magnesium**: four salt, burnt lime and water in a superheated mixer. It stands in for the brine and electrolysis of the Dow process.
- **Uranium metal**: two dioxide powders and a magnesium ingot in a superheated mixer, the "bomb" reduction. Out come two **slug cassettes**.

## Reactor graphite

Ordinary graphite carries enough boron to kill the pile. Reactor graphite comes from the [Acheson furnace](item:promptcritical:acheson_furnace), which graphitizes **sound baked electrodes** at 2500–3000 °C. It is a 500 kW load on a 6 kV cable, like a settlement: no grid, no graphite. A cracked electrode is refused. Two graphite electrodes make one [reactor graphite](item:promptcritical:moderator_block_graphite) block; a stonecutter, or a Create saw, cuts **fuel channels** and **rod channels** into it.

## Laying the pile

Every graphite block in the stack is one cubic metre of the core. A [fuel channel block](item:promptcritical:fuel_channel) holds up to eight slug cassettes; full, it is the CP-1 lattice. Empty channels are plain moderator. Keep a layer of graphite without fuel all round: it is the reflector and it saves a great deal of uranium.

- With a one-metre reflector, a 3×3×3 cube of full fuel blocks stays subcritical and 4×4×4 is already supercritical. Odd shapes need more.
- [Control rod drives](item:promptcritical:control_rod_drive) and [zip rod winches](item:promptcritical:zip_rod_winch) sit **on top** of a column of [rod channels](item:promptcritical:control_rod_channel); the rod is as long as the column.
- A rod is worth roughly the square of the flux where it stands. The **zip rod goes in the middle**. A rod at the edge of the fuel is worth a fraction of one in the centre.
- The [Ra-Be source](item:promptcritical:startup_source) is a block of the stack. Without it the counter shows only background until the pile is already critical, and that is how people get hurt.
- [BF₃ counters](item:promptcritical:neutron_detector) go against an outer face.
- The [control desk](item:promptcritical:reactor_controller) must touch the stack. It finds the pile, the rods and the counters itself, every second.

## Approach to criticality

Bring the pile up the way Fermi did. The desk does not show k: you find criticality from the count rate.

1. All rods in. Read the counter: that is the source level.
2. **Wind the zip rods** out. The control rods will not move up until the zip rods are cocked.
3. Withdraw the control rods in steps. After each step, wait for the count to settle and note it.
4. Plot **1/count** against rod position. It falls in a straight line towards zero. Where the line would cross zero, the pile is critical. Never take a step bigger than the distance left to that point.
5. Past critical the count no longer settles: it climbs with a steady **period**. A 60 s period is gentle. Stop the drive and the power keeps rising on that period until you drive the rods back in.

## Protection

The pile's protection belongs to the pile, not to your circuits. It trips on a **period shorter than 5 s** or **power above 200 kW**, and on the desk or [scram button](item:promptcritical:scram_button). On a trip the zip rods fall in a second or two and the control rods drive in. A redstone pulse on a scram button trips it too, so your relay logic can scram the pile but can never hold it out of a trip.

The zip rods only help if they are worth more than the pile's excess reactivity. A big pile with one zip rod at the edge can go on rising after a scram. A pile with no rods at all just runs away: the slugs heat until their cans fail, and above about 450 °C the graphite catches fire in air, as at Windscale in 1957.

## Wigner energy and annealing

Fast neutrons knock carbon atoms out of the graphite lattice. In cold graphite they stay out, and the lattice stores the energy: up to about 2000 J per gram in graphite irradiated near room temperature, less the hotter it runs, none above about 330 °C. The desk shows the store as **Wigner energy, J/g**. The graphite and its store run on the slow clock, so an hour at 150 kW leaves a cold pile with roughly 100 J/g.

Heated about 50 K above the temperature it was irradiated at, the graphite starts to give the energy back as heat, most of it around 200 °C. That heat warms the graphite further. If the store just ahead is more than the graphite's heat capacity, about 0.7 J/g per kelvin, the release feeds itself: the **Self-heating** lamp lights and the graphite climbs into the next part of the store. A pile carrying 300–400 J/g goes past 450 °C this way and the graphite catches fire. That is how Windscale burned in October 1957, during exactly this procedure.

To **anneal**, do it while the store is small:

1. Bring the pile to power and **shut the dampers**. The faces then lose only a fifth of their heat and the graphite warms by a degree or two a minute.
2. Watch the release on the desk. Hold the power steady while the graphite passes 100–250 °C and the store comes out.
3. If Self-heating lights, take the power off and open the dampers. The graphite will still rise by the store divided by 0.7 J/(g·K).
4. When more than half of the store is out and the release has died down, and the graphite never went above 400 °C (where it starts to burn in air), the anneal is done. Open the dampers.

Graphite irradiated hot keeps its store above the temperature it ran at, so a pile that ran warm needs a hotter anneal.
