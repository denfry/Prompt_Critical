# Changelog

## 1.1.0 (2026-11-24)

Reactor prototypes at real scale, hydrogen power, the I2 and I3 real-scale plants, waste heat for greenhouses,
fish and district heating, stations with roles and a journal for multiplayer, and an addon API.

### Breaking changes
- Machines draw their rated power: about twenty-five process machines take it from a socket, power board or UPS and
  slow down when underpowered; the catalytic reformer, delayed coker and alumina calciner need their own 6.3 kV feed
  and drop out below 0.85 pu until the player switches them on again.
- Grid motors have undervoltage release: half a second below 0.85 pu after reaching speed opens the contactor, and the
  motor stays off until the player starts it again.
- The graphite pile console is a 2 kW instrument load: without power its rods drop and stay in. Existing piles without
  a power board or UPS nearby shut down on load. The blast furnace console loses its gauges and alarms without power,
  the furnace keeps running.
- PWR pressurizer heaters are fed from the 0.4 kV station-service board or a UPS next to the pressurizer.
- In the licensing profile the breaker cell, water-tube boiler, Acheson furnace and T0 graphite pile need
  commissioning before closing, light-off or rod withdrawal; the coke battery, oxygen converter, air separation unit,
  sinter strand, hot blast stove and reheating furnace need it before they heat, blow or load. Running units are not
  stopped.
- The drainage pump, main mine fan, mine hoist, steam engine and shaft generator are no longer crafted: they are built
  from parts on the assembly stand.

### Real-scale machines
- I2 drilling and production: the R-560 rotary table under a derrick whose sections are drawn as bottom, middle or top
  by their neighbours, the U2-5-5 drawworks paying out while drilling, the U8-6M duplex mud pump, the SK-8 pumpjack
  that solves its four-bar linkage for the walking beam, and the NGS-1.6-2000 three-phase separator with its level
  floats, each on its own panel board.
- I2 oil transport (09 §9.8): the NM 1250-260 mainline pump on its head-flow curve, driven from a Create shaft, on a
  6 km DN 350 line whose friction follows the crude viscosity; the PP-0.63 line heater; the RVS-1000 stock tank with a
  fixed or floating roof, breathing losses and a vapour cloud.
- I3 plant: the 150 MW class gas turbine, the horizontal HRSG behind it, the primary steam reformer, the SRT ethylene
  pyrolysis furnace, the quench ammonia and Lurgi methanol converters, the bipolar membrane chlor-alkali electrolyser
  and the 30 m3 suspension PVC reactor. Downstream units are found at the outlet nozzle or along Create pipes.
- Radiation protection: heavy concrete, lead brick, borated water and borated polyethylene shield tiles, portal and
  hand-and-foot monitors, a hot cell with master-slave manipulators, a CASTOR-class cask that closes only within the
  heat and SSR-6 dose limits, a rail cart and a NUHOMS-class dry storage module whose cladding fails when its vents
  stay blocked.
- Fuel cycle: gas centrifuges and sixteen-machine cascade sections, the UF6 autoclave, the 30B cylinder, the flame
  tower fluorinator, the rotary pellet press, the pusher sintering furnace, the end plug welder and the VVER-440
  assembly stand. Placing a 30B cylinder or a fuel assembly respects build rights and protection mods.
- Create pipes connect to the nozzles of real-scale machines. Machines built in 1.0.1 get their nozzle cells when the
  chunk loads, without being rebuilt.

### Reactor prototypes
- VVER-70 after Rheinsberg (265 MWt, three loops): reactor, steam generator, sealless main coolant pump and a gas
  volume compensator on a nitrogen cushion, a unit board with ARK group bars, PZ-1 and PZ-2 lamps and an AZ key that
  trips only when held, a spent fuel pool that boils off without power and bursts uncovered cladding, and a
  refuelling machine that carries assemblies between core and pool. Spent assemblies leave the core with their
  activity.
- CANDU NPD (92 MWt): 132 horizontal channels of natural uranium whose power is held by the moderator level and shut
  down by dumping the moderator, fuelling machines that refuel on power and can leak D2O at the snout seal, the NPD
  boiler and the heat transport pump.
- BOR-60 (60 MWt loop-type sodium fast reactor): reactor under argon cover gas, intermediate heat exchanger, primary
  sodium pump and sodium-water steam generator, a board with a hold-to-scram key, and a tube leak drill whose
  sodium-water reaction bursts the rupture disc.

### Hydrogen power
- An alkaline electrolyser of the Nel A485 class on its own 6.3 kV rectifier feed with a hydrogen-in-oxygen trip, a
  two-stage diaphragm compressor to 200 bar, a tube bank and a gasholder with relief valve and burst disc, and a
  200 kW PEM fuel cell that feeds the grid through its inverter. Hydrogen flows by pressure along Create pipes.
- Leaks: an open pipe end on a gas line empties the vessel at choked flow, the hydrogen mixes with the room air, a gas
  alarm rings at a quarter of the lower flammable limit, and a flame, lava or running machine ignites the mixture at
  the limit.

### Radiation protection (04b)
- Geiger counter with tube saturation, electronic personal dosimeter, TLD badge and reader, and an annual dose
  ledger.
- Half-mask, full-face mask with iodine cartridge, powered air-purifying hood, air-fed suit on a 16 m hose,
  overalls, boot covers, lead apron and potassium iodide.
- Radiation zone controller with a class check, controlled area door, clean and dirty lockers and a dose rate
  detector. Heavy concrete, polyethylene, borated materials and lead glass join the shielding model, with capture
  gammas.

### Machines from parts (GDD 18, MF5)
- Assembly stand: pick a machine spec, lay out the parts, the stand rejects wrong parts, materials, cracks and burnt
  parts, scores fits, balancing, alignment and seals, runs the machine in and hands it out with a machine passport.
- The passport sets the starting wear of the maintenance model when the machine is placed and stays with the machine
  when it is broken.
- Specs for the drainage pump, the main mine fan, the mine hoist, the steam engine and the DC shaft generator, with
  their parts (fan wheel, rope drum, brake shoe, cylinder, piston, connecting rod, crosshead, flywheel rim, governor,
  stator winding, commutator, brush holder, slip rings and others).

### Plant operation
- Commissioning defects have effects: flange leaks vent gas into the flammable cloud, a slow trip device delays the
  breaker and the boiler and pile trips, a miswired flux loop under-reads pile power, and damp insulation faults to
  earth when the unit is energised.
- A loaded grid motor follows a pump curve below 0.9 pu speed, so it accelerates from standstill instead of sticking
  in the start.

### Integrations (GDD 16)
- Commodity chemicals and fuels in c: fluid tags (nuclear materials never), Curios and Accessories slots, body
  temperature handed to Cold Sweat or Tough As Nails, Carry On blacklist, foreign biome environment data map,
  JourneyMap waypoints from read geological maps.
- Operator terminal: a workstation without CC: Tweaked that shows every machine on the RTUs within 16 blocks with its
  values, a ten-minute trend, the alarm summary and the panel's telecontrol buttons.
- Plant public address: an intercom station with six priority channels, sixteen zones, prerecorded messages,
  press-to-talk through Simple Voice Chat and a typed call with a gong and a subtitle, and horn loudspeakers heard as
  far as the workshop noise allows.
- Direct speech in Simple Voice Chat carries as far as the workshop noise allows and is muffled by masks and
  breathing apparatus.
- Ghost rule: tracked radioactive sources are followed to where they were last seen; an unseen source keeps radiating
  there, its last holder gets an orphan alarm, and sources on the ground never despawn. `/pc sources list` shows them.
- The Immersive Engineering diesel generator burns c:diesel. Its burn time stops at IE's limit of 10000 ticks per
  bucket, so it gives under one percent of the fuel's real energy.
- Ghost rule: a source burned in fire or lava releases its iodine, part of its caesium and a trace of the rest into
  the air; an explosion throws one percent up and spreads the rest over three blocks; cactus breaks it open.
- Typed public address calls and announcements from CC: Tweaked through the intercom station (`pc_ops`), with the
  same filter, length limit and cooldown as the panel.
- Xaero's Minimap waypoints in chat for orphaned sources and read geological maps; Ecliptic Seasons next to Serene
  Seasons for the season of the climate.
- Addon API: read-only access to the sensor tags Lua sees and an event bus for alarms, trips, SIS trips, releases,
  milestones and contract states; a KubeJS plugin with `PromptCriticalEvents` and a read-only `PromptCritical`
  binding; `/pc export ftbquests` writes the route as an FTB Quests book in English and Russian.

### Stations and roles (multiplayer, 04d)
- Placing a reactor, cascade or fuel cycle controller founds a station on its chunk, owned by the player's scoreboard
  team or the player; `/pc station grant|revoke|claim|info|journal|computer` manage roles and chunks.
- Eight roles with the permission matrix of 04d: panel buttons, protection resets and bypasses, tests, chemistry,
  breaking and placing station blocks. A single player owns everything; outside stations nothing changes.
- Computers, RTUs, MES servers and watchdog relays act with their owner's role only while they stand in the station;
  any CC block in a station needs the control permission to open.
- The station journal records commands, trips and resets, bypasses, block series and refused access, exports to CSV
  with `/pc journal export`, and announces incidents by a scale after INES.

### Waste heat (17, 07)
- Greenhouse: a heating coil inside a glazed room takes up to 200 kW from the turbine condenser it touches, holds
  18 °C, speeds growth by the cardinal temperature curve, stops it below 5 °C and lets crops grow in winter with a
  seasons mod. A cold frame keeps a crop 8 K above the air by day.
- Condensers cooled straight from a pond or river warm that water; a fish cage grows fish on it by temperature and
  feed, and every fish dies above 36 °C.
- Combined heat and power: a district heating steam heater under a turbine makes it exhaust against the 150/70
  heating schedule and heats the settlements within 128 blocks by their beds.

### Simulation
- Geothermal station model: flash separator, silica scaling, reservoir with a cold front, induced seismicity with a
  traffic light and an isobutane binary cycle (no blocks yet).

### Panels and art
- Drawn panel boards for the I2 oilfield machines, the hydrogen machines and the reactor prototypes; the operator
  terminal and the public address redrawn; item icons for the new machine parts.
- Panel buttons can be keys that trip only when held, and panels can have a text field.

### Fixes
- The VVER-70 reactor model loads in the game: fourteen of its parts used a rotation the game rejects. The build now
  fails on such rotations.
- Create pipes dock at the nozzles of real-scale machines, and machines from 1.0.1 worlds get their nozzle cells
  converted on load.
- Panel labels of the VVER-70, CANDU NPD and electrolyser boards fit their buttons.

## 1.0.1 (2026-11-24)

Real-scale machines for the eras I0 to I2, the first GDD gaps closed after rc.1, and fixes from survival playtests.
Requires Minecraft 1.21.1, NeoForge 21.1.259+, Create 6.0.10+; CC: Tweaked 1.120.2+ is optional. Back up worlds
before updating: see *Breaking changes*.

### Breaking changes
- The medium-voltage switchgear cell is 1 x 3 x 1 (controller in the middle, busbar compartment on top). Cells placed
  in rc.1 keep working as they are; breaking a structure now removes only its own parts.
- The grid dispatch center is 3 x 2 x 1, the UPS cabinet and the grid battery rack are 1 x 2 x 1, so every cell their
  models draw blocks the player. Placing them needs the free volume; old single-block ones keep working.
- Cells drawn as ladders or stairs in real-scale models no longer collide, like vanilla ladders.
- Ladles and a converter that is not blowing lose heat on a real-time hold clock (`metallurgy.hold_k`, default 4,
  range 1-72) instead of the ×72 process clock, so a full 10 t ladle stays liquid for about 20 real minutes; the blow
  itself is unchanged.

### Real-scale machines
- 78 site plans with drawings: every I0 machine (22), the I1 ironmaking, coke by-product, power, water, mining and
  steel lines, the boiler house, degasification and ore dressing, the I2 steel line, the carbon line and vent stack,
  the smithy and machine building, and the non-ferrous line. Each is a whole-model block on the old block entity, built
  through a construction site from its drawing, with ports only on the faces the model shows.
- Parts the renderers draw over a model (the winding engine drum, flywheels, rotors, electrodes, rams, rolls) count in
  the collision shape, and GameTests check that every drawn cell blocks the player.

### New mechanics (GDD)
- Motor starters (07 §7.6.3): star-delta, soft starter and variable frequency drive modules in the motor panel.
- CC methods for pc_generator, pc_breaker and pc_transformer (07 §7.17).
- Lightning protection (07 §7.15.1): stroke currents, flashover above the insulation CFO, ground electrodes and surge
  arresters; an unprotected transformer at the end of a struck span can fail.
- Low-NOx burner kit for the water-tube boiler (07 §7.8.4).
- Dense medium separator for coal (08 §8.3).
- HCl synthesis furnace and hydrochloric acid (10 §10.3.4).

### Fixes
- Real-scale kinetic machines (electrode extruder, coal jig, molding station, shakeout grate, shaft generator) stood
  still on a turning shaft.
- The winding engine could be walked through along its drum; drawn ladders on vessels could not be climbed.
- Site drawings showed the missing-texture icon.
- An overblown converter no longer burns the whole bath and taps metal at tens of thousands of kelvin; the Overblown
  lamp stays lit at zero carbon.
- Two different site stage texts shared one key; a clash now fails at load.
- GameTests no longer share one fake player or depend on where the runner places them.

### Known gaps
- Checked in survival only partly: I0 (site build, crucible furnace, lathe), I1 real-scale machines, the I2 carbon
  line and vent stack. Other eras are covered by GameTests only.
- Not done from the GDD audit: 35/110/220 kV switchyard, condenser fouling, soot blowers, spray pond, coal mill,
  process heaters, coal tar distillation, continuous casting, mine fires, and most of 10 petrochemistry.
- Steam turbine trips do not reach the annunciator alarm list (the turbine has no panel).
- The alternator's voltage switch works only at standstill.

## 1.0.0-rc.1 (2026-11-19)

Release candidate of the first full release: every era of the design up to the closed fuel cycle is in the game,
together with the plant I&C, operations and accident layers. Requires Minecraft 1.21.1, NeoForge 21.1.259+,
Create 6.0.10+; CC: Tweaked 1.120.2+ is optional. Balance values marked [BALANCE] in the code and the config are
not final. Back up worlds before updating: see *Breaking changes*.

### Breaking changes
- A drilling rig no longer assembles without a BOP accumulator unit next to it: existing rigs need one placed.
- Grid consumers follow a daily load curve instead of a flat contracted load
  (`grid.load_profile_days_per_cycle`, 0 restores the flat load).
- The `pc_rtu` gateway refuses panel buttons that are hand-only (bypasses, protection keys, unloading, drills and all
  SIS buttons); Lua can do what a player at the panel can do, and no more.
- The graphite pile's graphite temperature now runs on the SLOW clock (×72), so it heats and cools much faster in
  real time; steady temperatures are unchanged.
- PLC analog outputs grew from 2 to 8 `%QW` words; old programs keep working.

### Operations and occupational safety (13)
- Lockout/tagout: padlocks, tags, hasps and a voltage tester, a lockout box, breaker cells and disconnectors that take
  locks, `/pc lockout`, and an isolation plan built from the electrical and shaft networks.
- Work permits by hazard (cold, hot work, radiation) through the permit state machine, with a portable gas detector,
  calibration gas, a fire watch, 4 h validity and 2 h re-tests, gas alarms suspending the permit and ignition at the LEL;
  a permit board running a turnaround from shutdown to the pre-start check; crew collective dose on radiation permits.
- Fire fighting: class B fires on oil, bitumen and tanks of crude and products, foam proportioners, foam chambers,
  hydrants, deluge and monitors fed by a grid-powered fire water pump; sprinklers and gaseous suppression.
- Personal protective equipment wired into the gas, smog, dose and noise models: a hard hat with respirator or ear
  defender inserts, gas masks with EN 14387 filter classes and sorbent life, a self-contained breathing apparatus, a fire
  suit and a harness with a lanyard.
- Confined spaces: a manway that finds its volume, tracks O₂, H₂S and CO, purges with nitrogen or process gas,
  ventilates through the open manway or a fan, needs a confined-space permit and an attendant, and a rescue tripod.
- Equipment rounds with tags, a reader and per-tag cards (bearing noise, imbalance, locks, open permits, bad air).
- The flare: three pilots, steam assist, smoke and combustion efficiency, API 521 radiation, a knock-out drum and a
  water seal.
- Safety signs that remind players without the required PPE, NFPA diamonds and ADR plates read from the vessel's product.

### Plant I&C and SCADA (03b–03d)
- PLC programs in LD, FBD and SFC as well as IL and ST, a graphical ladder editor, the `PC_ANTISURGE` and `PID` blocks.
- SIS: periodic proof tests with PFD growing between them, overdue alarms, a test procedure on the panel and the cause
  matrix of a cabinet.
- Historian server, digital twin with reference plants and acceptance tests, training simulator with lessons
  (including `stuck_porv`), instructor station and operator licences; tuner tasks with a server leaderboard.
- ISA-18.2 alarm engine with the annunciator panel and horn, the main control room core and desk, the watchdog relay.
- CC: Tweaked device layer: every panel machine is a peripheral (`pc_reactor`, `pc_pump`, `pc_plc`, `pc_sis`,
  `pc_well`, `pc_drilling`, `pc_tank`, ...), with events, command tickets, call budgets, `help` pages and examples in
  the ROM, and the `pcui` operator screen library with ISA-101 colours.

### Grid and dispatch (07)
- Grid dispatch center and `pc_grid`, the daily load curve of 07 §7.6.4 with heating and noise, emergency diesel
  generators with jacket heaters and a load sequencer, black starts.

### Economy (14)
- Weekly settlement of CO₂ allowances, fines and pollution fees, contracts and quotas, the MES closed loop.

### Oil and gas (09)
- Refinery: electrostatic desalter, vacuum column on steam ejectors, fluid catalytic cracking with catalyst state and a
  centrifugal compressor air blower, amine treating into the Claus unit, a quality laboratory with batch certificates,
  an in-world gasoline blending station with an on-line analyser, and blending as a linear program.
- Centrifugal compressor with a Moore–Greitzer map, surge with flow reversal, vibration and failure.
- Wells: logging (GR, neutron, resistivity) and coring, water injection for pressure maintenance, gas lift, a
  sucker-rod pump model with dynamometer cards behind the pumpjack, stuck pipe and lost circulation, BOP ram stacks
  with an accumulator to API 53.
- Accidents and pipelines: jet fires, vapour cloud explosions and flash fires, BLEVE of LPG bullets, H₂S releases,
  thermal radiation, oil spills with booms, skimmers and sorbents; tank gauges with overfill, pigging, cathodic
  protection and breathing losses.

### Non-ferrous metallurgy (08 §8.8)
- Bayer alumina, Hall–Héroult reduction pots on the rectifier with anode effects and PFC emissions, copper matte
  smelting and Peirce–Smith converting with SO₂ to the acid plant, electrorefining of copper and nickel, cobalt, tin
  and the platinum group metals.

### Nuclear (01, 04c, 12)
- Accident layers for AM-1, CANDU and the sodium fast reactor; LWR severe accidents: rod ejection with fuel enthalpy
  limits, steam generator tube rupture, core melt to corium with vessel failure and concrete attack, hydrogen
  combustion regimes up to detonation.
- Turning gear for hot rotors (a stopped hot rotor bows in 30 min and starts with a large imbalance), reactor hall
  exhaust ventilation through HEPA and charcoal filters to the stack, and a water intake with a fouling screen.
- Operational reactivity margin with an ORM lamp on the T1 panel, Wigner energy and annealing in the graphite pile,
  the TRIGA pulse reactor, americium, curium and Pu-241 in spent fuel with Am-241 extraction, an RTG powering a remote
  well gateway, pump cavitation of the mine drainage pump.

### World and progression (11)
- World structures (survey camps, old mines, asphalt lakes, burning gas craters, an orphan source lighthouse, weather
  stations, institute ruins, railway halts, natural reactors) with loot, an assay station, and the full achievement
  catalogue of 11 §11.2.2 — every achievement now has the mechanic behind it.

### Fixes in this release
- Eleven hand-drawn block models (T1 reactor, hoist, headframe, cages, mine gas cabinet, coal jig, lathe, wind turbine)
  were dropped by the client because elements left the vanilla −16…32 box; they rendered as missing-model cubes. A
  `checkModelBounds` task now fails the build on such models.
- The strong-base anion exchange resin had no item model.
- Twin, simulator and licence messages never reached the language files.
- The PLC programmer and the 14-row SIS panel did not fit the smallest GUI (854×480 at scale 2); a game test now
  checks every panel against 427×240.
- An active alarm could not get an annunciator window while quiet alarms held them all.


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
- Neptunium-237 recovered in PUREX: about 0.47 kg per tonne at 33 MWd/kg, a nitrite valence adjustment so Np(VI)
  goes with uranium, separation in the second uranium cycle and NpO₂ bags sized for one Pu-238 target; without the
  adjustment most of it ends up in the glass.
- MOX blending model: reprocessed PuO₂ with tails uranium to 5–8 % Pu (12 % at most), a blending vessel held below
  the safe plutonium mass, and fissile plutonium worth 0.75 of U-235 in a PWR lattice.
- MOX through the fuel shop: PuO₂ lots go into a second hopper on the pellet press and are blended into each uranium
  bag at 7 % Pu; pellets, sludge, rods and assemblies carry the plutonium, the sintering furnace also holds it below
  the safe mass, and the PWR loads MOX assemblies at their U-235 equivalent enrichment.

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
### Air pollution (14 §14.1–14.4, R21)
- Boilers report SO₂, NOx, dust, CO₂, CH₄ and CO to an emission ledger and to a chunk pollution field: Briggs plume
  rise, a Gaussian plume over 17×17 chunks with distances compressed twenty times, deposition, rain washout,
  self-cleaning and downwind spread saved per dimension.
- Effects by WHO and UNECE thresholds: haze, smog, acid load on plants, cough and weakness without a respirator or gas
  mask, acid rain. Electrostatic precipitator on grid power, limestone scrubber on calcite and a CEMS analyzer that
  switches reporting from calculated to measured. Handbook chapter and a game test not yet run.

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

### Sodium-cooled fast reactor (T4)
- Fast reactor `sfr_reactor` (3×6×3, BN-350/BN-600 type at 100 MW(t)): one-group point model of a MOX core
  in a U-238 blanket, breeding ratio ≈ 1.2 at 18 % Pu with Pu-239 bred in the blanket; plutonium kinetics
  (β_eff ≈ 0.0035, Λ = 0.4 µs); Doppler, axial fuel and radial support plate expansion and a sodium void worth
  that is negative for the whole small core and positive in its centre.
- Pool-type primary sodium at 0.1 MPa (380 → 550 °C), intermediate sodium loop and a sodium–water steam
  generator; sodium fire with Na₂O smoke and the sodium–water reaction with a hydrogen scram and a rupture disc,
  both fed to the damage budget. Desk, ru/en texts, recipe and a T4 handbook chapter.

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

### Boiling water reactor (T2b)
- A 100 MWt direct-cycle boiling water unit on the same light water core as the VVER: the water boils in the core at
  7 MPa, drift-flux voids along the power profile feed back through the core water density, and recirculation pumps
  set the power along a rod line (70 % flow gives about 73 % power). A PI turbine pressure regulator, feedwater level
  control, hydraulic scram in 3 s, natural circulation without pumps and a vessel safety valve.
- The steam carries N-16: steam turbines near the reactor become radiation sources at power. Steam reaches the
  turbines through a water-tube boiler drum next to a steam generator block, which stands for the vessel steam space.
- A 3×6×3 vessel with its own segments, a control desk, recipe and a handbook chapter. The core is small for a boiling
  reactor, so its void coefficient is several times the large-plant −80…−150 pcm/%, like the PWR coolant coefficient.
- The PWR now keeps only its loop and protection; assemblies, neutronics, poisons and depletion moved into a shared
  light water core.

### Heavy water reactor (T3)
- A 100 MWt CANDU-type unit: 97 horizontal channels of 12 natural uranium 37-element bundles (about 19 kg of uranium
  each) in a cold D2O calandria with a D2O reflector, D2O coolant at 10 MPa. The light water core is generalized by a
  spec, so the CANDU shares its kinetics, poisons and depletion; neutron generation time near a millisecond.
- Positive coolant void reactivity, about +16 mk on full voiding of fresh fuel; shutoff rods drop in 2 s.
- On-power refuelling: a fuelling machine magazine takes bundles from the desk slot, fills empty channels on a
  shut-down reactor and at power pushes 8 fresh bundles into the most burnt channel, discharging 8 as one canister.
- Tritium builds up in the moderator from D(n,g)T and is counted on the desk; it is a pure beta emitter with no
  external dose. Control desk, recipe and a T3 handbook chapter.
- Fixes a shadowed position count in the shared core step that let only the first PWR or BWR assembly deplete.
- Heavy water is a real fluid in three grades: 15 % D enriched water, 90 % D downgraded heavy water and 99.75 %
  reactor grade, which also comes in a bucket and pours into the world. Machines track the deuterium fraction
  inside and never hand out a higher grade than they hold.
- Girdler sulfide plant (3x6x3) on a lake shore: dual-temperature H2O/H2S exchange at 30 and 130 °C, at most
  1 - 1.80/2.32 = 22 % of the deuterium recovered, a tonne of lake water a second, 15 % D product into pipes.
- Vacuum finishing column (1x3x1): 400 plates at alpha 1.05 take the GS product or downgraded heavy water up to
  reactor grade and refuse natural water.
- The CANDU now starts dry and holds real D2O inventories: about 112 t in the calandria (any side) and 3 t in the
  heat transport circuit (from below). The moderator level sets the moderated core height, a half-empty circuit
  acts as voided channels, and isotopic purity enters the cell absorption and moderation: about 12 mk per atom
  per cent of protium in the moderator. Light water ingress dilutes it; a drain button sends a cubic metre of
  moderator, tritium included, into a tank of its grade. The desk shows level, purity and both lamps.

### Telemetry and SCADA (A4)
- Telemetry gateway (RTU): scans the local panels of every machine within 8 blocks once a second and serves the
  snapshot to a CC: Tweaked computer as the `pc_rtu` peripheral — machines, named instruments with units, lamps as
  status points, dynamometer cards as curves, and panel buttons as telecontrol commands, with a request budget and
  one-at-a-time commands. Works without CC installed.
- The handbook renders code blocks; the telemetry chapter carries a Lua boiler house mimic with a trend archive.

### Plant server (A5)
- Plant server (MES): reads every telemetry gateway within 16 blocks, shows plant generation, load, fuel cost,
  sales, CO2 and dispatch efficiency on its panel, and splits the dispatch target between generators by equal
  incremental cost (lambda iteration, Wood & Wollenberg ch. 3). Optional CC: Tweaked peripheral `pc_mes`
  (`getKpi`, `getDispatch`, `setDispatchTarget`).
- `sim.mes`: economic dispatch with unit limits and a CO2 fee, and a production planner that commits units and
  loads them to the market price under a market cap.

### Vibration and noise (04a)
- Vibration diagnostics for any turning Create machine: a spectrum with 1x imbalance, 2x misalignment, blade pass,
  the outer race, inner race, ball and cage frequencies from bearing geometry, gear mesh on cogwheels and an HF
  envelope; overall RMS velocity in the ISO 10816 band, zones A-D by machine class from shaft power.
- Rolling bearings wear by ISO 281 life and Palmgren-Miner through four defect stages and seize at the end,
  wrecking the machine. Bearing kit, balancing weights and an alignment tool for maintenance.
- Vibration analyser with a spectrum screen, bearing housing temperature and the noise level where you stand;
  a vibration transmitter that puts the overall level on redstone.
- Machine noise adds up by energy with distance and wall attenuation; a daily noise dose by the 3 dB rule,
  fatigue past the dose, deafening above 120 dB(A), ear muffs take off 25 dB. Handbook chapter in I1.

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

First public alpha. Requires Minecraft 1.21.1, NeoForge 21.1.259+, Create 6.0.10+;
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
