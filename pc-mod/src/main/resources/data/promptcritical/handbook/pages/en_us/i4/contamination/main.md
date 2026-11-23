A dosimeter answers one question: how much radiation reaches you from out there. Contamination is a different problem. It is radioactive dust and droplets sitting on the floor, on tools and on your hands, and it is dangerous for two reasons: you carry it out of the zone yourself, and you can breathe it or swallow it.

## Where it comes from

Contamination appears wherever activity escapes from its container:

- a [pool rack](item:promptcritical:fuel_rack) left without water: failed cladding throws fuel aerosol onto the floor around it;
- a criticality in the [PUREX canyon](item:promptcritical:purex_cell) boils the solution and splashes plutonium and fission products around the cell;
- a primary circuit rupture in a pressurised water reactor pours coolant with caesium from the failed fuel over the floor.

Fresh spills are almost entirely **removable**: dust and droplets that a swab picks up, a sole carries off and a draught lifts into the air. The rest is **fixed**, grown into the oxide film, the paint and the pores of concrete. Fixed contamination does not travel, but water will not take it either.

## How it spreads

You walk across a dirty floor and your shoes pick it up. Then you walk down a clean corridor and leave it there. Every step exchanges a couple of per cent of the loose activity under the sole. The same goes for your hands and the tool you hold. Breaking a contaminated block puts its activity on the dropped items.

## How it gets inside

Loose contamination on the floor rises into the air, about a millionth of it per metre, and for plutonium that is enough. One becquerel of plutonium per square centimetre of floor gives about 0.4 µSv/h of committed dose from breathing alone. Hands carry dirt to the mouth. Caesium at the same density is a thousand times less harmful to breathe. The DKS-04 adds the committed dose to your total, but it does not cause radiation sickness: an alpha emitter inside the body irradiates you slowly, for years.

## Measuring it

The [surface contamination meter](item:promptcritical:contamination_meter) has a flat 100 cm² probe with two channels, alpha and beta. Right-click a block to read its top surface. Right-click the air to check your hands and shoes, or hold an item in your other hand to measure that instead. The readout gives counts per second and their conversion to Bq/cm² following ISO 7503-1. Below the detection limit it shows "<MDA": what the instrument cannot see does not exist for it.

The limits come from NRB-99/2009, table 8.9. For skin: 2 alpha and 200 beta particles per cm² per minute, about 0.13 and 6.7 Bq/cm². Overalls and shoes are allowed more, and the floors of rooms where people work are judged by loose contamination only.

## Getting clean

- **[Decon shower](item:promptcritical:decon_shower)**: stand in front of it. A full minute of soap and water removes about 90 % of the loose contamination from your hands, and the water rinses your shoes. Water comes in by pipe; the wash water leaves through the bottom as spent decontamination solution.
- **[Hand and foot monitor](item:promptcritical:hand_foot_monitor)**: right-click it on your way out. Red means back to the shower.
- **[Portal gamma monitor](item:promptcritical:portal_monitor)**: a column that watches the one-block passage in front of it. While the passage is empty it learns the local background; whoever walks in is counted for everything they carry, and above background plus five sigma over two seconds it rings and gives a redstone signal. That is about 30 kBq of caesium-137, a speck. Put two columns facing each other across the exit and wire the signal to the door. Right-click shows the background and the alarm level.
- **[Ultrasonic bath](item:promptcritical:ultrasonic_bath)** for parts and tools. It takes contaminated items and three solutions by pipe: water, citric-oxalic and alkaline permanganate, and always picks the strongest treatment it has solution for. Cavitation multiplies what the chemistry can do. Water removes loose dirt, the acid dissolves part of the iron oxides, and permanganate followed by acid gets fixed contamination out of stainless steel too. Every part costs two litres of each bath used, and all of it becomes liquid waste.
- **[Decon sprayer](item:promptcritical:decon_sprayer)** for floors and equipment. Right-click a tank to hand over the collected waste and fill up; right-click a surface to treat a square metre. Water takes 20 l and only loose dirt, citric-oxalic solution takes 10 l. For fixed contamination, spray alkaline permanganate first to oxidise the film, then go over the same block with acid.
- **[Strippable coating](item:promptcritical:strippable_coating)**: spray it on, wait four hours for it to dry, then peel it off with an empty hand while sneaking. The film takes 90–99 % of the loose contamination with it. There is no liquid waste, only the film itself as solid waste.

Nothing disappears during decontamination. The removed activity ends up in the waste tank, the film or the sprayer's collector, and that waste still has to be collected and conditioned.

## Making the solutions

The citric-oxalic solution is mixed hot from sugar, a little nitric acid and water: sugar oxidised by nitric acid gives oxalic acid, the way it was made in the 19th century. Alkaline permanganate is pyrolusite with caustic soda solution and water.
