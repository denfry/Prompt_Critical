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

### Oil and gas (I2)
- Petroleum geology: one anticlinal trap per 384-block cell with a real depth of 0.6–2.5 km, porosity,
  permeability, gas, oil or water and normal or abnormal pore pressure; a model of the dome in the deepslate,
  bedded barite in the cap rock and oil seeps over shallow fields.
- Seismic recorder with geophones and seismic charges: a seismic section of the nearest structure with
  two-way time, depth, closure, a bright spot over gas and an Eaton pore pressure forecast.
- Drilling rig around a driller's console: rotary table and triplex mud pumps on Create shafts, derrick,
  drawworks, annular preventer, choke manifold, mud tanks and a mixing hopper for barite. Bourgoyne–Young
  rate of penetration, Bingham plastic hydraulics, ECD, weight indicator, standpipe and casing gauges, pit level.
- Well control: Darcy inflow when underbalanced, pit gain alarm, shut-in SIDPP and SICP, gas migration, shoe
  breakdown and preventer limit, the driller's method on an automatic choke, and a blowout that ignites gas on
  the rig through the damage budget. Handbook chapters on seismic, drilling and well control.

### Oil production and refining (I2)
- Completing a drilled bore: a wellhead on the rig's spot, production casing from seamless pipe, perforating with
  charges, a Christmas tree with a choke. Inflow by Vogel's curve, the reservoir depletes with a rising gas-oil
  ratio and water cut, the well flows on a Gilbert choke until it dies.
- Pumpjack on a Create shaft: four-bar beam kinematics, rod stretch, inertia and fluid pound, with the
  dynamometer card drawn on the panel.
- Three-phase separator (oil, water, associated gas; Stokes settling, Souders–Brown gas capacity) and a flare.
- Atmospheric crude unit: crude heater burning associated gas and a tray column that splits a light West
  Siberian crude by its TBP curve into gasoline, kerosene, diesel and mazut; trays and reflux set the sharpness
  through Fenske–Underwood–Gilliland. Handbook chapters on production and distillation.

### Gas processing (I2)
- Low-temperature separation of associated gas: a gas compressor on a Create shaft, triethylene glycol dehydration
  with a regenerator, a propane chiller and a Joule–Thomson choke; sales gas, NGL and water come out on their own
  faces. Water dew point by Bukacek, Peng–Robinson expansion, Wilson flash.
- Hydrates by Hammerschmidt and Katz: chilling below the glycol dew point without methanol grows a hydrate plug
  that blocks the choke; methanol from dry distillation of wood is dosed to keep a 3 K margin.
- NGL fractionation into propane, butane and natural gasoline through a deethanizer, depropanizer and debutanizer
  on the Fenske–Underwood–Gilliland column. Sales gas and associated gas fire the gas turbine and furnaces.

### Nitrogen chemistry (I3)
- Steam reformer: sales gas and steam over nickel in a tube furnace (Xu and Froment kinetics), a secondary reformer on
  process air held at three hydrogen to one nitrogen by a ratio controller, and high and low temperature CO shift.
  Running short of steam lays carbon on the nickel. Catalysts are charged by hand.
- CO₂ removal with MEA (Kremser absorber, regeneration on the process heat) and a methanator that trips above 450 °C
  when CO₂ slips; off-spec gas goes to the vent.
- Ammonia synthesis loop on a Create shaft: three adiabatic beds of iron catalyst (Temkin–Pyzhev), equilibrium tested
  against the Larson–Dodge table, a separator on an ammonia chiller and a purge. The loop pressure finds its own level,
  and without a purge the inerts choke it. The purge gas fires the reformer.
- Nitric acid by Ostwald at atmospheric, 0.42 or 0.73 MPa: platinum-rhodium gauze with platinum loss, a flash above the
  lower explosive limit, Bodenstein NO oxidation and tray absorption limited by acid strength, a brown tail plume and
  tail gas reduction. Magnesium nitrate concentration to 98 % on the existing column code.

### Methanol and combined cycle (I3)
- Methanol synthesis unit on a Create shaft: its own steam reformer without air or shift (S ≈ 3), a Lurgi-type
  boiling-water tube converter on copper with Graaf equilibria and Vanden Bussche–Froment kinetics, a recycle loop
  whose pressure finds its own level and a purge to the reformer burners. The shell steam sets the bed temperature;
  copper sinters above 300 °C and a dry shell runs the bed to its adiabatic equilibrium.
- Two-column methanol distillation to grade AA on the Fenske–Underwood–Gilliland column, reboiled by the unit's own
  steam; the product is the same methanol the gas plant uses against hydrates.
- Heat recovery steam generator behind the gas turbine: single-pressure pinch-point design, steam into the boiler it
  touches. Combined cycle about 43 % on the first-generation turbine, about 50 % on a 1980s heavy-duty one.

### Uranium enrichment (I4)
- UF₆ cylinders 48Y, 30B and 5A carry their UF₆ mass and U-235 and U-234 fractions; topping up averages the isotopics,
  and overfill or enrichment beyond the cylinder's limit is refused.
- Gas centrifuge sections and a cascade control on a Create shaft: Dirac's separative power with countercurrent
  efficiency, rotor burst speed for aluminium, maraging steel and carbon fibre, more failures near burst and above the
  bending critical speed, and burst rotors wrecking their section and sometimes the next one.
- An ideal cascade built from the counted machines for the set product and tails assays: stages, stage flows, SWU tested
  against Benedict–Pigford–Levi (4.5 % from natural at 0.25 % tails takes 6.87 SWU/kg), cost-optimal tails, U-234
  following U-235, and depleted tails as a product of their own.
- Criticality safety: the product desublimer is held below 45 % of the minimum critical mass of moderated uranium by an
  interlock; with the interlock off it can be overloaded into a criticality excursion that doses everyone nearby.

### Fuel fabrication (I4)
- Enriched UF₆ to ceramic UO₂ by the ammonium diuranate route: hydrolysis, ammonia precipitation and hydrogen
  reduction, with the U-235 in the line's vessels held below the safe mass and uranium above 20 % refused.
- A pellet press on a Create shaft (Heckel green density, pressing rejects) feeding a hydrogen sintering furnace
  (95–96 % of theoretical density at 1700 °C in four hours, grain growth, O/U) with centerless grinding; sludge and rejects
  go back to the press.
- Rod loading and welding with a helium fill (argon as a marked fallback) and a helium leak test; an assembly jig for
  VVER-440, VVER-1000 and PWR 17×17 assemblies. Every powder bag, pellet tray, rod and assembly carries its lot: uranium
  mass, U-235 and U-234, density, design and fill gas.
- Nuclear material accounting of uranium and U-235 on every station's panel. Zirconium from zircon washed out of sand,
  reduced with magnesium (hafnium separated), alloyed with tin, rolled into cladding tubes and made into spacer grids.

### Silicon, chips and computers (11 §11.5)
- Submerged arc furnace (about 12 kWh/kg), trichlorosilane fluidised bed, Siemens reactor (120 kWh/kg) with the HCl
  loop, Czochralski puller on a Create shaft under argon with its growth window and Scheil boron profile, wafer saw
  with Preston polishing.
- Clean room behind an airlock with HEPA fan filter units: ISO class from filtered flow, leaks, the door and people.
  The airlock runs the planar process: Deal-Grove oxidation, contact, proximity or g-line lithography, phosphorus
  diffusion, aluminium metallisation, a process window and Poisson yield into transistors, logic ICs, memory and
  microprocessors that carry their process node.
- Vacuum tubes for A2 analog computers, transistors and boards for the PLC, ICs and memory for the RTU station.
  CC: Tweaked computers and monitors are crafted from processors, memory, boards and cathode-ray tubes when the mod
  is loaded (`content.cc_recipe_override`). Handbook chapter, panels, ru/en texts and game tests not yet run.

### Isotopes and RTGs (12)
- Irradiation channel: in the stack of the graphite pile it takes the flux of its node, against the T1 vessel
  it is one of four AM-1 experimental channels and its target is charged against the core reactivity. Capsules
  of cobalt, iridium or neptunium dioxide go through an exact Bateman solution (Co-60, Ir-192, Np-238 → Pu-238
  with product burnup and self-shielding); gamma heating can burst a capsule and leave the channel contaminated.
- Pile slug cassettes build up fission products and burnup channel by channel; discharged T1 assemblies leave
  the core with their activity, steel Co-60 included.
- Sealed sources with IAEA categories, a gamma radiography camera that exposes film through a pipe wall, finds
  the defects radiography can see and marks the pipe RT, and doses the radiographer while the source is out.
- Thermoelectric RTG on Sr-90 (taken from a vitrified waste canister) or Pu-238: about 10 W from the 230 W of a
  Beta-M, decay and thermoelement ageing, better cooling under water; it lights lamps and powers relay supplies
  off the grid, and its source drops out unshielded when the RTG is broken. Y-90 now has a bremsstrahlung line.
- Panels, ru/en texts, recipes, an I4 handbook chapter and game tests that have not been run yet.

### First nuclear power plant (T1)
- A uranium-graphite channel power reactor after AM-1 of Obninsk: 128 channels with pressurised water at 10 MPa,
  30 MW of heat, fuelled by AM-1 channel assemblies from the fuel shop. Point kinetics with six delayed groups, Doppler
  and graphite coefficients from the lattice model, a negative water coefficient from the 1954 critical experiment,
  xenon and samarium with the iodine pit, decay heat and burnup by channel with U-236 and plutonium build-up.
- Control and protection: scram, shim and automatic regulator rods; the scram trips on period, power, flow, outlet
  temperature, the button or an external signal, and process trips can be bypassed by key. Control cabinet terminals
  give power as 4-20 mA, a scram contact and an external scram coil for safety logic and PLCs.
- Primary circulation pumps on Create shafts with flywheel coastdown and steam generators that put the primary heat
  into an existing boiler for the turbines. Dryout fails a channel, lets water into the stack and releases iodine
  into the hall; the reactor is a radiation source for the dose model. Spent channel fuel carries burnup, U-236 and
  plutonium.

### Pressurized water reactor (T2a)
- A 100 MWt VVER-type starter unit: a vessel of 19 VVER-440, VVER-1000 or PWR 17×17 assemblies from the fuel shop
  (about 2.3 t of uranium with VVER-440) with the rod drive head above, two loops at 15.7 MPa and 290/320 °C.
  Doppler, coolant density and boron coefficients come from the full core k-eff, so the small core's leakage shows
  in a strongly negative coolant temperature coefficient; boron is about −1.5 % per g/kg of boric acid.
- A two-region pressurizer: insurge compresses the steam bubble, outsurge flashes the water, heaters, spray from the
  pump discharge, a relief valve and a safety valve. Charging holds its level; water exchange with clean condensate
  or 40 g/kg concentrate dilutes or borates the primary.
- Reactor coolant pumps keep their Create shafts but coast down on a 300 kg·m² flywheel (τ = 12 s). Steam generators
  at water-tube boiler drums feed the existing turbines; their drum level feeds the protection.
- The 01 §1.11 protection set with three channels per function and three DNBR calculators: power with the start-up
  setpoint and permit, period, high and low pressure, loop flow, pump speed, hot leg temperature, DNBR, SG level,
  the button and an external scram on the cabinet terminals. Safety injection below 12.5 MPa with 16 g/kg solution.
- A primary leak drains through the pressurizer to a low-pressure scram and injection; cladding that stays past
  departure from nucleate boiling for 5 s fails and releases iodine through the leak or relief valve; overpressure
  ruptures the loop through the damage budget. Control desk, ru/en texts, recipes, a T2 handbook chapter and game
  tests not yet run.
- Fuel depletion moved out of the T1 channel reactor into a shared helper; T1 behaviour is unchanged.

### Telemetry and SCADA (A4)
- Telemetry gateway (RTU): scans the local panels of every machine within 8 blocks once a second and serves the
  snapshot to a CC: Tweaked computer as the `pc_rtu` peripheral — machines, named instruments with units, lamps as
  status points, dynamometer cards as curves, and panel buttons as telecontrol commands, with a request budget and
  one-at-a-time commands. Works without CC installed.
- The handbook renders code blocks; the telemetry chapter carries a Lua boiler house mimic with a trend archive.

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
  cooled by river or pond water or by a cooling tower cell, gives vacuum and about twice the power, less in
  late summer; tower water depends on the wet-bulb temperature and the cell ices up in frost.
- Hydro power: hydro turbine, dam intake gate, penstock water hammer.
- Gas turbine unit on mine methane or coke-oven gas, with a turbine gearbox.

### Steelmaking
- Electric arc furnace: melts 6 t of steel scrap on a 6 kV bus at about 600 kWh/t and taps into a ladle; the
  arc load swings its reactive power and makes the bus flicker.

### Grid
- Capacitor bank: 6 kV steps of 150 kvar for reactive compensation; too large a bank self-excites a small
  idling generator.

### Control power
- Station battery: a 24 V lead-acid battery next to the relay rectifier carries control power through a
  blackout (Peukert discharge, float charge, cut-off at 1.75 V per cell).

### Fixes
- Building anything in a dimension rebuilds its grid and relay schemes, and that used to disturb running
  equipment everywhere: a breaker mid-travel lost its command or restarted its travel, overcurrent time
  delays, reclosing cycles and under-frequency latches reset, a synchronizing shock could be skipped, and for
  one tick every breaker read open, every current zero and every relay dropped. All of these now carry
  through the rebuild. This was also the cause of the flaky switchgear and relay-logic game tests.

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

### Plant paperwork
- Shift log: a hand-written book; right-click a machine to log its panel readings with the day and time, Shift + use
  to sign the shift handover. Reads on the document lectern.
- Work permit for electrical work: written on an open breaker or disconnector, it locks the apparatus open and hangs
  the "Do not switch on! People at work" poster; admission waits for proved absence of voltage and earthing, the
  earthing stays on until the permit is closed, and the breaker's control power is withdrawn so auto-reclose cannot
  close it. A lost permit is closed on the second copy.

### Foundry and machine shop
- Cupola and crucible furnaces, sand moulds, moulding machine, shakeout grate.
- Lathe and drill press with cutting tool wear; castings with defects.
- Coal jig with clean coal, middlings and refuse.
- Knee milling machine, shaping machine and surface grinder make spur gears, keyed shafts, slideways and planed
  and ground plates. Cutting force follows Kienzle, cutter and tool wear follow Taylor, the grinding wheel loads up
  by removed volume and burns the work past Malkin's limit; the feed step sets Ra and the IT grade, and a part
  too rough for its job, burnt or with a casting defect comes out as scrap, which the cupola remelts.
- Gears go into the rolling mill stand, keyed shafts into the generator and the electric motor, ground plates
  into the hydrotest bench. Machine bed and base plate casting patterns.

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
