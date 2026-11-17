# Changelog

## Unreleased

### Physics corrections
- Physics audit fixed throughout `pc-sim`: minimum film boiling temperature, out-of-phase closing torque,
  two-axis synchronous machine with the under-excitation limiter, oxygen-limited methane and dust explosion
  energy, Xe-135 Westcott factors, ENDF/B-VII.1 cross sections, photoneutron groups, blast furnace oxygen
  balance and Si/Mn reduction heat, Misaka flow stress, cast alloy heat capacity, unburnt carbon,
  flammability of inert-diluted gas, PUE conductor ampacity, transformer heating to IEC 60076-7, pump energy,
  and the low-severity list.
- Xenon, samarium and fission product decay heat in the graphite pile.

### Character body
- Choose sex, height, mass and age on the title screen or on first join; locked per world, an operator can reset.
- Height scales the player model and stride; mass sets knockback resistance and the load in the hoist cage;
  mine gas poisons faster when running or climbing (Coburn–Forster–Kane).
- Touching a live bare busbar while earthed gives an electric shock (IEC 60479-1, Dalziel); wool works as a
  dielectric mat up to 1 kV.

### Environment
- Weather layer: air temperature by biome, season and time of day, wind with gusts and height profile, sun;
  seasons from Serene Seasons when installed. `/pc env` shows it.
- Overhead line cooling follows the wind (IEEE 738); mine natural draft follows the outside air and reverses
  with the season; the water-tube boiler takes the outside air temperature.
- Rivers carry rain floods and a spring snowmelt flood.

### Power generation
- Steam turbine: a back-pressure turbine that takes steam from the boiler it touches; a condenser under it,
  cooled by river or pond water, gives vacuum and about twice the power, less in late summer.
- Hydro power: hydro turbine, dam intake gate, penstock water hammer.
- Gas turbine unit on mine methane or coke-oven gas, with a turbine gearbox.

### Grid
- Capacitor bank: 6 kV steps of 150 kvar for reactive compensation; too large a bank self-excites a small
  idling generator.

### Control power
- Station battery: a 24 V lead-acid battery next to the relay rectifier carries control power through a
  blackout (Peukert discharge, float charge, cut-off at 1.75 V per cell).

### Fixes
- A grid rebuild in the middle of a breaker's travel no longer drops the closing command; this also fixes
  the flaky switchgear game tests.

## 0.1.0-alpha (M2: era I0, coal and steam)

First public alpha. Requires Minecraft 1.21.1, NeoForge 21.1.259+, Create 6.0.11+;
CC: Tweaked 1.120.2+ is optional.

### Mine
- Vein worldgen with ore and coal assays; geological map.
- Mine network: ventilation with a main mine fan on a Create shaft, ventilation
  doors and regulators, methane and CO, degasification boreholes and pump.
- Methane explosions with a flame front; stone dust and water explosion barriers;
  canaries and flame safety lamps; self-rescuers.
- Timber and steel arch supports, powered roof supports, longwall shearer on a
  Create gantry.
- Mine hoist with headframe, sheave and cage; wire ropes with wear.
- Mine drainage pumps, sump and flooding, acid mine water.
- Gas monitoring cabinet with a panel of methane, CO, oxygen and sump readings.

### Boiler room
- Lancashire fire-tube boiler in brick setting: firing by hand or chain grate
  stoker, chimney draft, feed tank, fusible plug, relief valve, low water and
  boiler explosion.
- Horizontal steam engine: stop valve, governor with dashpot, barring over,
  output gears 16/32/64 RPM on a Create shaft.
- Local pressure gauge block.

### Foundry and machine shop
- Cupola and crucible furnaces, sand moulds, moulding machine, shakeout grate.
- Lathe and drill press with cutting tool wear; castings with defects.
- Coal jig with clean coal, middlings and refuse.

### Panels, art and effects
- Local instrument panels (A0) for every machine: dial gauges with red zones and
  needle lag, gauge glass, level bars, signal lamps, readouts, buttons and slots.
  Empty-hand use opens the panel; sneak-use keeps the quick action.
- Detailed models for the boiler room, mine machinery, foundry and machine shop.
- Particles and sounds: furnace flames and crackle, steam exhaust in time with the
  crank, cupola sparks, smoking moulds, sand dust, metal chips, water splashes.
- Machine inventories drop when the machine is broken.

### Content
- Engineering handbook with illustrated chapters, operating procedures on
  document lecterns, advancements, I0 decor blocks.
