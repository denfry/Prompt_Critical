Pump and valve bodies, flywheels, wheels and gear blanks are not forged or crafted: they are cast. The I0 foundry melts iron and non-ferrous metals, pours them into sand moulds and knocks out the finished castings.

## Melting

- The [cupola furnace](item:promptcritical:cupola_furnace) is a shaft furnace for grey iron. Charges go in at the top: [pig iron](item:promptcritical:pig_iron) or iron ingots, with charcoal in place of coke, 100 kg per item. Air comes from a blower on a Create shaft. The fuel share sets the temperature: 12 % of the metal gives about 1450 °C, 10 % gives 1345 °C, cold iron; 14 % gives 1545 °C. The top gas is carbon monoxide.
- The [crucible furnace](item:promptcritical:crucible_furnace) is for copper, bronze and aluminium. Ingot into the crucible, coal into the hearth. The crucible holds one alloy at a time: change alloy only after pouring.

Both furnaces tap by themselves into a prepared mould beside them: the cupola at its foot, the crucible furnace in front of it, at the same level or one block lower. A redstone signal holds the tap hole closed. An empty-hand click on a furnace shows mass, temperature and fuel.

## Pattern and mould

A [casting pattern](item:promptcritical:casting_pattern) is a wooden copy of the part. Right-click with the pattern in hand to cycle the part: valve body, pump casing, impeller, gear blank, wheel, flywheel, ingot mould, bearing housing. Sneak and right-click to toggle the riser.

The [moulding machine](item:promptcritical:molding_station) jolts on a Create shaft and rams [foundry sand](item:promptcritical:foundry_sand) around the pattern. Give it sand, the pattern and a bucket of water. Each cycle makes one prepared [sand mould](item:promptcritical:sand_mold). The pattern is not used up. A crafted flask is empty: only the machine gives a mould with an impression.

## Solidification

A casting cools through the surface of the mould. Solidification time grows with the square of the **modulus**, the ratio of volume to surface: this is Chvorinov's rule. A thick part freezes many times slower than a thin one. An empty-hand click on a mould shows how long is left.

A **riser** is a reservoir of liquid metal above the casting. Metal shrinks as it freezes, and the riser feeds the shrinkage. Its modulus must be at least 1.2 times the casting's, or the riser freezes before the part.

## Defects

- **Shrinkage cavity**: no riser, or too small a one. A through-leak in a pressure body.
- **Hot tear**: sharp changes of section in an alloy prone to them.
- **Cold shut**: metal less than 50 K above its melting range, which fails to fuse in the mould.
- **Gas porosity**: sand moisture above 6 %. Keep moulds out of the rain.
- **Chill**: iron cooled too fast and turned white and hard. No cutting tool will touch it.

## Shakeout

The [shakeout grate](item:promptcritical:shakeout_grate) on a Create shaft knocks out cooled moulds placed on it. The casting and 90 % of the sand go to its store; take them with an empty hand. You can also break a cooled mould by hand, but the sand is lost. Leave a mould with liquid metal alone: the metal will run out.

Castings go on to the [machine shop](promptcritical:i0/machine_shop).
