# In-game playtest

The automated game tests (`./gradlew --no-daemon :pc-mod:runGameTestServer`, headless) check the simulation and the
block logic. They do not look at the screen. This list is for a person, or an agent driving the client, who checks what
only a running client shows: models and their orientation, panels and screens, particles, sounds, the handbook and the
integrations. Each check says what to do and what should be seen. Record every deviation with a screenshot, the block id,
the facing and the log line, if there is one.

## Setup

1. `./gradlew --no-daemon :pc-mod:runClient` (the client run has `neoforge.enableGameTest=true`). For an agent, put the
   mcpfabric jar into `pc-mod/run/mods` and align its token in `pc-mod/run/config/mcpfabric.config.json`.
2. Create a creative superflat world with cheats on. Optional mods to test the integrations: CC: Tweaked 1.120.2+,
   Serene Seasons.
3. In the world run `/test runall`. Every test structure is built next to the player in a grid and runs once; green
   and red beacons mark the result. Then fly along the rows. `/test runthese` re-runs the tests near the player, and
   `/test run <class>.<method>` runs one test, for example `/test run powerreactorgametests.twopumpsgivenominalflow`.
4. Keep the game log open: any `ERROR` from `promptcritical`, a missing model (purple-black cube) or a missing texture
   is a finding even when the test is green.

## Checks not covered by any game test

| Area | What to do | Expected |
|---|---|---|
| Multiblock orientation | Place every multiblock facing north, east, south and west: boiler, steam engine, blast furnace, hot blast stove, arc furnace, gas turbine, wind turbine, `power_reactor` (T1), `pwr_reactor` (T2a), `hrsg`, `ldpe_reactor`, `pvc_reactor`, rectifier, uranium plant (`leach_tank`, `rotary_calciner`, `hf_kiln`), `mixer_settler`, `pellet_press`, `primary_pump`, `czochralski_puller` | Parts line up with the controller in all four facings; partial models (agitators, rams, impellers, shells, rotors) turn about the right axis and sit inside the body; breaking any part breaks the whole and drops one item |
| Two-segment blocks | `power_reactor`, `pwr_reactor`, `hrsg` | The upper segment shows its own model (`control_rod_drive` on the T2a head, `hrsg_top`), not a copy of the lower one, where the art exists; note where it still repeats |
| PLC | Place `plc`, open it, type the start/stop program from handbook chapter I2 "PLC", load, turn the key to RUN | Editor accepts text, compile errors are shown with a line number, RUN lamp lights, outputs follow inputs |
| SIS | Place `sis_logic_solver` with three `gas_detector`s on 4–20 mA, open its panel | Voting, setpoint, PFDavg and SIL are shown; trip latches; Reset works only with clear channels; Bypass lights the alarm and is announced in chat with the player's name |
| CC: Tweaked bridge | With CC installed: `rtu_station` next to panels, a computer on its side, in Lua `local r = peripheral.find("pc_rtu"); print(textutils.serialise(r.machines()))` | Machines, instruments with units, lamps; a panel button pressed through the peripheral acts in the world |
| CC recipes | With CC installed: `/recipe` view or JEI for `computercraft:computer_normal`, `computer_advanced`, `monitor_normal`, `monitor_advanced` | They need the mod's microprocessor, memory, board and CRT (`content.cc_recipe_override = true`); with the key false, CC's own recipes return |
| Panels | Open the A0 panel of each machine type at least once | No overlapping widgets, every gauge has a unit, lamps change colour with state, Russian and English both fit (switch the language) |
| Handbook | Open the handbook, walk every category I0–I4, T1, T2, automation | Formula images render, links open the right chapter, code blocks render, no raw `key.name` strings |
| Dosimeter | Hold `dosimeter_radiometer` near a running T0 pile, an RTG, the radiography camera with the source out and a spent assembly | Dose rate and total above the hotbar, clicks get denser near the source, overload message above 10 Sv/h; lead blocks and water between lower the reading |
| Radiation effects | Stand near a broken RTG or an unloaded hot assembly for a while | Acute dose climbs; effects appear by ARS stage; death message is the radiation damage type |
| Body | New world: the body screen on first join | Sex, height, mass and age are chosen once; height changes the model scale and step; an operator can reset |
| Weather and seasons | `/pc env` in different biomes, day and night, with and without Serene Seasons | Temperature, wind and gusts, season and river state change plausibly; the wind turbine's rotor follows the wind |
| Tides | Tide gauge on a coast, wait a few game days | Level follows the moon phase, spring tides at full and new moon |
| Cap lamp | Wear a charged cap lamp in a dark mine, let it run down, break its glass in a methane drift | Light follows the player and dims at the end of the shift; broken glass of a lit lamp ignites methane |
| Sounds | Run each machine with sound on | A sound loops while running and stops when it stops; no machine is silent where `PcSfx` names a sound; Geiger clicks |
| Effects | Run boilers, furnaces, flare, cooling tower, chimney, reactors | Smoke, steam, flame and sparks appear at the right places of the model, not inside the floor |
| Clean room | Build a room of `cleanroom_panel`/`cleanroom_window` with `hepa_filter`s behind `cleanroom_airlock`, open and close the door, walk in | Class on the airlock panel improves with filters, gets worse with an open door and with people inside |
| Toxic clouds | Release chlorine (chlor-alkali liquefier without caustic) and HF (fluorination without a cylinder) outdoors in wind | The cloud drifts downwind; players downwind take the toxic load, upwind do not |
| PLC Structured Text | Program the same start/stop latch in ST (`PROGRAM … END_PROGRAM`, `IF … THEN`), load it | The screen shows the detected language; ST and IL versions behave the same; a syntax error names its line |
| Reactors T2b, T3, T4 | Build `bwr_reactor`, `candu_reactor`, `sfr_reactor` with pumps and a steam generator; load fuel; start up | Panels show power, pressure, void/sodium temperatures; BWR power follows recirculation flow; CANDU on-power refuelling shifts bundles; SFR sodium leak on overheat gives fire and smoke |
| Containment | Wall a `pwr_reactor` in with `containment_wall` (floor and roof too) and a `containment_hatch`; open and close the hatch | Closed shell is detected within 5 s; with the hatch open a vessel rupture releases a plume downwind, with it shut it does not |
| MOX | Put PuO₂ from PUREX into the pellet press's second hopper, run the line to an assembly, load it into the PWR | The lot tooltip shows Pu at 7 %; the PWR accepts it |
| Plant server (A5) | `mes_server` next to an `rtu_station` among running generators | KPI panel shows generation, load, cost and revenue; dispatch setpoints appear; with CC, `peripheral.find("pc_mes").getKpi()` |
| Air quality | Run several coal boilers without cleaning for a few game days, then add an electrostatic precipitator and an FGD absorber | Haze and smog messages, cough and slowness without a respirator; cleaning lowers the field; `cems_analyzer` switches the ledger to measured |
| Market | Sell goods at `market_terminal` repeatedly | Price falls with volume and recovers over two days |
| Electric gear | Charge `powered_respirator` and `heated_suit` on the lamp rack; walk into a chlorine cloud and into a snowy biome at night | Respirator stops the chlorine while the blower runs, not CO in a mine; without the suit the body cools by stages, with it the battery drains instead |
| Refinery | Hydrotreater → Claus → acid plant; catalytic reformer; delayed coker | Sulphur items reach the acid plant; reformer panel shows RON and hydrogen; coker drums cycle and drop petroleum coke; PCBs now need phenolic resin |
| Redrawn machines (2026-11-18 evening) | Look at `pwr_reactor`, `bwr_reactor`, `candu_reactor`, `sfr_reactor`, `power_reactor`, `tube_furnace`, `pyrolysis_furnace`, `steam_reformer`, `claus_unit`, `edc_cracker`, `brine_treater`, `pvc_reactor`, `ldpe_reactor`, `gas_compressor`, `nitric_acid_unit`, `chlor_alkali_cell`, `purex_cell`, `uo2_conversion`, `mes_server`, `delayed_coker`, `low_temperature_separator`, `pressurizer` cold and running, in four facings | Own models (no more `reactor_vessel_wall` bodies); moving parts (PVC agitator, brine rake, compressor and LDPE cranks, pump coupling, CANDU fuelling bridge and head) turn about the right axis inside the body; particles and sounds come from the right spot |
| CANDU panel | Open the CANDU reactor | The panel is 200 px tall with the heavy water rows; its painted background (made for 176 px) was dropped at merge, so it is plain for now |
| Heavy water | GS plant by a lake → finishing column → bucket of `heavy_water` into a CANDU; drain it | Graded fluids go through Create pipes; the CANDU fills, goes critical only with D2O, and drains into downgraded water |
| Accidents | VVER or BWR at power: break a primary pump or the pressurizer, or press LOCA on the panel; then cut all AC with no diesel | ECCS pumps and accumulators hold a small break; a blackout without diesel uncovers the core, makes hydrogen and pressurises the containment |
| Severe accidents | VVER at hot zero power: press Rod ejection; at power: press SGTR, then Isolate SG1 and PORV; a VVER with no ECCS pump: LOCA twice and wait, once with a dry cavity and once with water in the 3×3 under the vessel; a small closed containment with a hydrogen burn | A prompt burst trips the reactor and opens a small break; N-16 alarm within seconds, the isolated SG fills; the core melts, a dry cavity gets a glowing `corium` block under the vessel that sinks through the floor, a flooded one keeps the melt in the vessel; panels show the burst, the SG line, the melt stage and the hydrogen regime |
| Acoustics | Run a Create fan or motor, use the vibration analyzer, stand near loud machines without and with ear muffs | Spectrum shows 1x and bearing tones, ISO zone A–D; noise dose builds, ear muffs stop it; replacing the bearing resets wear |
| Contamination | Spill from a dry pool rack, walk through it, use the hand-foot monitor, shower, ultrasonic bath, sprayer and strippable coating | Shoes carry it, the monitor alarms, each decon step lowers Bq/cm2 and leaves contaminated decon solution |
| Waste | Feed liquid waste and decon solution to the LRW evaporator, cement or bitumen the concentrate, vitrify HLW, compact filters, fill both repositories | Drums are classified, HLW goes only to glass, repositories refuse packages over their limits |
| Emergency diesel | Place `diesel_generator` in four facings with a `station_battery` touching it, pipe in `diesel_fuel`, cable the generator end to a bus with ECCS pump motors, fit `jacket_heater` and `load_sequencer`, turn Auto on, open the incomer | Engine and alternator segments line up, the flywheel turns while running; the unit cranks, fires and closes onto the dead bus within 15 s; the sequencer brings loads back every 5 s, largest first; the panel shows fuel, air starts and the fault cause; no start without the battery |
| Lockout and fire | Hang padlocks with tags on an open switchgear cell, a disengaged mechanical disconnect and a blind line blind; fit a hasp; use the lockout box; start a fire under a sprinkler and between two smoke detectors with a fire panel and a deluge; try each extinguisher on a plain fire, at the SFR sodium leak and next to a live cell | Locked blocks refuse every action with the owners' names; models of the disconnect, box and detectors change state; the sprinkler bursts and the deluge opens; water at the sodium fire flashes, water at the live cell shocks |
| Chunk save | Save and quit with machines running (reactor at power, cascade spinning, PUREX line busy), reload | Everything resumes in the same state; the T1/T2a reactors do not trip on the first tick after loading |

## Player journey I0 (survival, real-scale machines)

Play these as a survival player, not with `/setblock`. An agent drives the client through mcpfabric (`players.teleport`,
`control.lookAt`, `interact.placeBlock`, `interact.breakBlock` with `survival`, `interact.useItem`, `vision.screenshot`)
and keeps the window behind all others (`mc-background.ps1`, `pauseOnLostFocus:false`).

| Step | What to do | Expected |
|---|---|---|
| Handbook | First join in survival | The engineering handbook is in the inventory; the body screen is chosen once (seen in the I0 run: the screen is modal, Esc does nothing until "Confirm", and "Confirm" closes it; agent clients start with `runClient -PskipBodyScreen`, which submits the reference body at login) |
| Drawing | Hold each `*_drawing` in the hand and look at it in the creative tab | The design drawing icon, never the purple-black cube; every drawing is in the tab |
| Site plan | Place `construction_site`, right-click it with a drawing on ground that is not cleared | The message names the first block in the way with its position; the drawing is not used up |
| Pit | Dig what the message names (the crucible melting hole needs a 2 × 2 pit one block deep) and right-click again | "Drawing accepted: the site is marked out", the drawing is used up, the ghost of the machine shows its volume |
| Delivery | Right-click the site with the stage materials, then with an empty hand | It takes only what the current and the remaining stages still lack; the chat shows the stage name, what is missing and, while erecting, the seconds left |
| Finish | Wait for the last stage | The machine stands in the site's place with the site's facing |
| Route credit | Open the handbook route after the machine is finished | The I0 route task of its old-series twin (crucible, boiler, hoist, fan, cupola, moulding, shakeout, the shop machines) is done, machines whose twin has an operating procedure (boiler, hoist, fan) also give that procedure |
| Break | Break any cell of a real-scale machine in survival | The whole machine goes at once and drops one item |
| Facings | Place every I0 real-scale machine facing north, east, south and west and look from its front-left | All four views are the same picture: parts and partials turn with the facing |
| Cage | Walk into the mine cage at the top landing, close the door, ride down and up, use the shaft signal | Bells ring three, two and one strokes; the action bar shows the depth; the player walks out at each landing |
| Engines | Feed the Lancashire stokers, light the boiler, start the mill engine and the winding engine from the driver's stand | Pressure rises on the gauge; crank, crosshead, rod and drum move with the shaft; the hoist cage moves |
| Ports | Insert and extract with a hopper and a pipe at every declared port of each real-scale machine, then at a cell without a port | Ports take and give; other cells of the volume take nothing |
| Collision | Walk around and under each big model (boiler, headframe, Guibal fan, winding engine) | The player is not stuck in invisible cells and does not walk through drawn parts |

### I0 run 2026-11-23 (integrate 0dd1c125, survival, `runClient -PskipBodyScreen`)

| Check | Result |
|---|---|
| Site plan on grass | "The site is not free: Grass Block at 3030 -61 3000", drawing kept; four messages walk the 2 × 2 pit cell by cell |
| Pit dug | "Drawing accepted: the site is marked out", drawing used up, ghost shown |
| Delivery | 10 bricks and 10 steel ingots: the site took 3 and 4, the need of both stages; status line in the chat |
| Finish | Crucible melting hole stands with the site's facing (east) |
| Route | "A crucible furnace" (+20 XP) and "Choose a melting furnace" (+10 XP) done on completion |
| Break | One survival hit on a corner part removes all 8 cells, one `crucible_furnace_real` item drops |
| Collision | Lancashire boiler and Guibal fan stop the player at the edge of the volume; headframe lets the player walk between the legs. **Winding engine: the player walks straight through it along its centre line (x = controller, the whole length, about 10 blocks), standing inside the drum between the flanges** (`shots/winding_engine_inside.png` in the session scratchpad). Cause: the drum is a second whole model and the slicer only cuts the engine; fixed in `WholeShapes` (drum and the mill engine flywheel join the cell shapes), GameTests `windingEngineDrumIsSolidOnTheCentreLine`, `everyDrawnCellOfI0WholeModelsBlocksThePlayer` |
| Ports (lathe) | A hopper at the controller front gives a cutting tool to the lathe; hoppers at the other two front cells keep it. Create motor at the west face of the countershaft port: the port runs at 16 rpm with stress on the network |
| Not checked | Cage ride and shaft signal, steam engines from the stand, machining a casting in the client, panels, the other machines' ports |

### I2 carbon line and vent stack run 2026-11-23 (integrate 64a28b07, creative, mechanics only, `runClient -PdevXmx=1536M -PskipBodyScreen`)

| Check | Result |
|---|---|
| Place | `acheson_furnace_real` 72 cells, `electrode_extruder_real` 28, `baking_furnace_real` 80, `grid_consumer` 27: every footprint cell filled, controller plus `multiblock_part` |
| Item ports | Hoppers: baked electrode into the Acheson bed top (3,1,1) taken, a cracked one kept, the end wall (5,0,1) east keeps it; pitch coke into the extruder hopper (-1,1,0) taken, the pump unit top (2,1,0) keeps it; a green electrode into the baking cover (0,3,1) taken and loaded into the charge at once, the cover edge (2,3,1) keeps it |
| Extruder shaft | A creative motor on the controller's north face joins the press to its network (press at 16 and 64 rpm). **The real press did not run: no pitch drawn, no electrode in 25 s, while the old press beside it made a green electrode.** Cause: `KineticMachineBlockEntity.deviceType()` gave the block id with `_real`, so the simulation made no twin for it; fixed with `RealScaleBlockEntity.simType` like the other device bases, GameTest `realExtruderTakesItsShaftAtTheController` checks the sim type. After the fix the real press reads `running=true` at 64 rpm and makes a green electrode (352 cm) from 3 pitch coke and pitch in 30 s, like the old one |
| Baking furnace | Coke oven gas in the buffer: LIT on, gas drawn, two green electrodes loaded into the charge. A full bake (a simulated day of soak) is not run in the client |
| Vent stack | Three segments 3 blocks apart read base, middle, top; removing the middle leaves two single stacks (both `base`) with no part cells left; putting it back gives base, middle, top again. Filter range: GameTest `ventStackCountsFiltersOnlyWithinRange` (3 blocks counts, 4 does not) |
| Break | A destroyed corner part removes the whole machine (no cells left) and drops one machine item each (Acheson, extruder, baking, consumer); the inventory spills beside it |
| Not checked | Fluid ports in the client (a lone Create pipe is straight with open ends either way, so it shows nothing; the capability checks are in `CarbonRealScaleGameTests` and `pipesConnectOnlyAtPorts`), the Acheson bed under 6 kV (needs a running generator; no-power behaviour is in `achesonFurnaceNeedsTheGrid`), the site build of the carbon machines, C2 with a real hall release (covered by `ventStackFiltersHallReleases`) |

## Game test structures to look at

`/test runall` builds all of these. The headless run passes all of them; in the client look at the models, the panels
and the effects of each structure, not only the beacon colour.

189 tests in 52 classes.

| Class | Tests |
|---|---|
| `AccidentGameTests` | `smallLocaHeldByOneEccsTrain`, `stationBlackoutLosesEccsUntilTheDieselTurns`, `tornCoolantPumpIsAGuillotineBreak`, `bwrSmallLocaHeldByCoreSpray` |
| `AcousticsGameTests` | `fanSpectrumAndBearingWear`, `transmitterSignalsTheFanLevel`, `noiseAddsUpAndConcreteBlocksIt`, `noiseDoseAndEarMuffs` |
| `AirQualityGameTests` | `scrubberAndCemsChangeTheLedger` |
| `ArcFurnaceGameTests` | `scrapChargesButNoBusMeansNoArc` |
| `ArtStateGameTests` | `coldBwrShowsDarkDeskAndSeparatorsSegment`, `emptyHlwVaultStaysCold`, `containmentHatchTogglesOpen` |
| `AutomationGameTests` | `startStopLadderSealsInAndStops`, `plcLatchesStartStopFromItsProgram`, `analogPiLoopSettlesOnSetpoint`, `controlKeyClosesAndTripsTheBreakerThroughItsTerminals`, `redstoneInterfacePowersTheWallBehindIt` |
| `BindingGameTests` | `simulationTicksWithTheLevel`, `deviceRegistersInTopologyAndLeavesAfterBreak`, `footprintPlacesPartsAndBreaksAsWhole`, `boilerPlacesBothSegmentsAlongFacing`, `headframeStackFormsAndMarksTop`, `damageBudgetDestroysAroundOriginButNotImmuneBlocks`, `worldStateSurvivesNbtRoundTrip`, `everyBlockHasLootAndPlacesWithoutError` |
| `BodyGameTests` | `bodyIsChosenOnceAndShapesThePlayer` |
| `BoilerGameTests` | `engineDrivesCreateShaftAndGaugeReadsPressure`, `reliefValveLiftsWithoutSteamDemand`, `lowWaterMeltsFusiblePlug`, `lowWaterWithoutPlugBlowsBoilerUp`, `chainGrateStokerFeedsTheFurnace` |
| `BootstrapGameTests` | `apiIsInstalled`, `serverConfigIsLoadedWithDefaults`, `createIsLoaded` |
| `ChlorAlkaliGameTests` | `rectifierPowersCellsThatMakeChlorine`, `cellsWithoutRectifierStayDead`, `brineIsTreatedOnlyWithReagents`, `liquefierMakesLiquidChlorineAndVentsTailGasWithoutCaustic`, `chlorineCloudDriftsDownwind`, `edcCrackerAndPvcAutoclave` |
| `CombinedCycleGameTests` | `hrsgTurnsGasTurbineExhaustIntoBoilerSteam` |
| `CompatGameTests` | `processCatalogIsComplete`, `productsAreExportedToCommonTags`, `modOresStayOutOfPerMetalTagsUnlessOpenCompatIsEnabled`, `coldSweatHeatSourcesMatchBlockStates` |
| `ContaminationGameTests` | `spillLandsOnTheFloorAboveTheLimit`, `shoesCarryContaminationAndTheShowerWashesHands`, `handFootMonitorCatchesDirtyHands`, `ultrasonicBathCleansPartsIntoLiquidWaste`, `strippableCoatingPeelsLooseContamination`, `sprayerNeedsPermanganateBeforeAcidForFixedContamination`, `contaminationGivesCommittedNotAcuteDose`, `betaContaminatedFloorShinesGamma` |
| `ContentGameTests` | `handbookIsGivenOnceOnFirstJoin`, `chapterOpensWhenItemObtained`, `handbookIsConsistent`, `markdownLinksAndEmphasis`, `firstSteamGoesToTheShiftNearTheEngine`, `canaryInAMineAndRewardsAreOnlyExperience`, `procedureGoesOnTheLecternAndBack`, `geologicalMapPointsToTheNearestBody`, `decorLightSwitchesAndLadderClimbs`, `ironAndPowerChaptersAndProcedures`, `ironAndPowerAdvancements`, `ponderScenesHaveSchematicsAndTexts`, `ironAndPowerDecor`, `routeIsConsistent`, `handbookTranslationKeys`, `routeReachNeedsTheWholeHold`, `routeObtainBuildAndChoice` |
| `DrillingGameTests` | `drillIntoGasTakeKickAndKillIt`, `seismicRecorderNeedsASpread` |
| `ElectricGearGameTests` | `respiratorFiltersTheCloudOnceTheBlowerRuns` |
| `ElectronicsGameTests` | `airlockSeesTheRoomAndItsFilters`, `czochralskiPullsASingleCrystal` |
| `EnrichmentGameTests` | `cylindersMixAndRefuseOverfill`, `cascadeEnrichesNaturalUranium`, `overspeedWrecksTheSections` |
| `FoundryGameTests` | `everyMachineGetsItsDomainTwin`, `bronzeIsCraftedFromCopperAndCassiteriteAndFitsTheCrucible`, `latheTurnsACastingOnACreateShaft`, `cupolaPoursAMoldThatTheGrateKnocksOut`, `crucibleMeltsCopperAndPoursTheMoldInFront`, `moldingMachineRamsASandMold`, `coalJigWashesRunOfMineCoal` |
| `FuelFabricationGameTests` | `conversionMakesEnrichedPowder`, `pressAndFurnaceMakePelletTrays`, `rodStationSealsRodsWithHelium`, `jigAssemblesAVver440Assembly` |
| `GasProcessingGameTests` | `ltsMakesSalesGasAndNglWithMethanol`, `uninhibitedColdChokePlugsWithHydrate`, `fractionatorSplitsNgl` |
| `GasTurbineGameTests` | `gasTurbineStartsOnMineMethaneAndDrivesTheGearbox` |
| `GridGameTests` | `closingAt120DegreesWithoutSyncCheckOverstressesTheCreateShaft`, `syncCheckRelayRefusesClosingAt120Degrees`, `generatorIsSynchronizedByTheSynchroscope`, `underfrequencyLoadSheddingDropsTheLoadOnDeficit`, `lowVoltageMotorOnSixKilovoltBusBreaksDownAndOvercurrentTrips`, `lightingBoardFeedsItsLampsAndTheEmergencyLightRunsOnBattery` |
| `HeavyWaterGameTests` | `gsPlantMakesEnrichedWaterFromALake`, `gsPlantWithoutALakeStands`, `finishingColumnMakesReactorGrade`, `canduIsFilledDowngradedAndDrained`, `heavyWaterBucketPoursAndEmptiesIntoTanks` |
| `HeliumActivityGameTests` | `heliumNeedsLiquidNitrogen`, `poolWaterShieldsSpentFuel` |
| `HoistGameTests` | `cageRidesToLevelAndLandsAsBlocks`, `hoistRefusesWithoutRope`, `panelButtonsDoNotOverlapLandings`, `excessWaterFloodsDriftAndRecedes`, `drainagePumpLowersSump` |
| `HydroGameTests` | `pondAboveTheTurbineDrivesIt` |
| `IronworksGameTests` | `cokeBatteryFormsChargesAndPushesIntoAChest`, `cokeBatteryReportsAMissingWall`, `blastFurnaceFormsTakesChargeAndRefusesToDrillCold`, `blastFurnaceNeedsATaphole`, `ladleChillsHotMetalIntoASkull`, `magneticSeparatorConcentratesMagnetite`, `sinterStrandSintersConcentrateWithBreezeAndFlux` |
| `IsotopeGameTests` | `cobaltCapsuleBreedsCobalt60InTheFlux`, `overheatedCapsuleFailsAndContaminatesTheChannel`, `craftingFillsCapsuleAndMakesHeatSource`, `strontiumRtgLightsALampOffGrid` |
| `LampRoomGameTests` | `lampChargesOnTheRackFromTheGrid`, `wornLampRunsDownAtTheEndOfTheShiftAndItsLightFollows`, `orphanLightBlockRemovesItself` |
| `LongwallGameTests` | `pistonAdvancesOnlyLoweredSupport`, `setSupportsHoldWideRoof`, `shearerCutsFaceOnGantry` |
| `MachineShopGameTests` | `millingMachineCutsAGearFromATurnedBlank`, `shapingMachinePlanesABasePlateCasting`, `surfaceGrinderGrindsAPlanedPlate`, `overspedGrindingWheelBursts` |
| `MarketGameTests` | `terminalSellsSteelForCredits`, `terminalRefusesVanillaGoods` |
| `MethanolGameTests` | `methanolUnitAndColumnMakeGradeAa`, `withoutAColumnCrudeStaysInTheSeparator` |
| `MineGameTests` | `methaneExplosionStopsAtBarrier`, `fanVentilatesAndDoorThrottles`, `regulatorWindowSetsAirflow`, `canaryFaintsInCarbonMonoxide`, `everyVeinRoleHasABlock`, `minedOreCarriesAssay`, `anchorStartsMineAndRemovalEndsIt` |
| `MoxReactorGameTests` | `bwrLoadsMoxAssembly`, `am1ChannelTakesMox`, `canduChannelTakesMoxBundles`, `vverKeepsPlutoniumAsPlutonium` |
| `NitrogenGameTests` | `reformerMakesAmmoniaSyngas`, `reformerWithoutNickelMakesNothing`, `synthesisLoopMakesLiquidAmmonia`, `ostwaldUnitMakesNitricAcid`, `richAmmoniaMixtureFlashes`, `magnesiumNitrateColumnConcentratesAcid` |
| `OpsGameTests` | `stuckReliefValveBurstsUnacceptedBoiler`, `reversedPumpLosesHeadAndBumpFindsIt`, `replacingReliefValveResetsOnlyItsCheck`, `enforcedModeRefusesIgnition`, `bearingWearShowsOnPanelAndShellFixesIt`, `scaleShowsOnPanelAndScraperRemovesIt`, `tallowAndCoalDustNeedNoNether` |
| `PaperworkGameTests` | `disconnectorStaysOpenUnderAPermitAndClosesAfterIt`, `shiftLogWritesPanelReadingsAndSignsTheHandover` |
| `PowerhouseGameTests` | `boilerFormsAndRefusesFuelWithoutPurge`, `igniterTrialWithoutFlameTrips`, `bypassedFlameDetectorsBlowUpFurnace`, `deaeratorMakesFeedwaterFromDrumSteam` |
| `PowerReactorGameTests` | `freshFuelLoadsAndUnloads`, `twoPumpsGiveNominalFlow`, `dryoutWithoutProtectionBreaksChannelsAndDosesTheHall` |
| `ProductionGameTests` | `wellSeparatorAndColumnMakeProducts`, `deadWellRunsOnAPumpjack` |
| `PurexGameTests` | `lineMakesReprocessedUraniumAndGlass`, `stalledOxalateGoesCritical` |
| `PwrReactorGameTests` | `vverAssemblyLoadsAndUnloads`, `pressurizerLinksAndWithoutItTheReactorStaysTripped`, `twoPumpsSpinBothLoops`, `losingPumpDriveAtPowerTrips` |
| `PyrolysisGameTests` | `furnaceCracksPropaneIntoOlefins`, `furnaceWithoutSeparationStaysCold`, `ldpePolymerisesEthylene`, `ldpeOverheatDecomposesEthylene` |
| `RadiationGameTests` | `cobaltSourceThroughAirLeadAndConcrete`, `sourceExpiresWhenNotRenewed` |
| `ReactorGameTests` | `deskFindsThePileRodsAndCounter`, `removingAFuelBlockReformsThePile`, `scramButtonDropsTheZipRod`, `scannerRefusesOnlyNonPileBlocks`, `fuelChannelTakesOnlyEightSlugCassettes`, `achesonFurnaceNeedsTheGrid` |
| `RefineryUpgradingGameTests` | `hydrotreaterFeedsClausWithHydrogenSulfide`, `reformerMakesReformateAndHydrogen`, `sulfurFeedsAcidPlant` |
| `ShockGameTests` | `liveBusbarElectrocutesAnEarthedPlayer` |
| `StationBatteryGameTests` | `batteryCarriesControlPowerThroughABlackout` |
| `SteelworksGameTests` | `steelAndPipesGoIntoRecipes`, `hotPlateCoolsAndStacksWithColdOnes`, `millStandReducesABilletOnACreateShaft`, `hydrotestPassesASoundPipeAndBurstsACrackedOne`, `airSeparatorFormsFromCompressorSieveAndExpander` |
| `StorageGameTests` | `electrodeBoilerDrawsFromTheGridIntoTheBoilerHouse`, `electrodeBoilerWithoutABoilerKeepsItsElectrodesDry`, `pumpedStorageUnitWithoutAnUpperBasinStandsStill` |
| `TelemetryGameTests` | `gatewayReadsPanelsAndPressesButtons` |
| `TideGameTests` | `tideGaugeShowsTheMoonPhaseAndTheModelSeaLevel`, `tidalUnitWithoutAClosedBasinStandsStill` |
| `TurbineGameTests` | `turbineWithoutBoilerStandsStill`, `condenserByWaterGivesVacuum`, `condenserOnACoolingTowerGivesVacuum` |
| `UraniumFrontEndGameTests` | `acidPlantAndHfKiln`, `leachingDissolvesUraniumWithPyrolusite`, `resinSorbsAndAmmoniaPrecipitatesYellowcake`, `dryConversionMakesGreenSalt`, `fluorinationFillsACylinderWithNaturalUf6`, `fluorineCellsRunOnTheRectifier` |
| `WasteGameTests` | `evaporatorCementsConcentrateIntoAcceptableDrums`, `highLevelConcentrateGoesOnlyToGlass`, `supercompactorPressesFiltersIntoAnOverpack`, `repositoriesApplyAcceptanceCriteria`, `cleanupLoadsResinAndSluicesItOut` |
| `WindGameTests` | `windTurbineExportsPowerIntoTheGrid` |

## Known gaps (do not report as bugs)

- Art is postponed: many new machines use placeholder or borrowed models (for example the uranium ion exchange column
  uses the water treatment column's model, the T1 reactor both segments use `reactor_vessel_wall`). Report only missing
  models, wrong orientation and parts outside the body.
- Water never moves with the tide (design decision R32).
- `GridGameTests.lowVoltageMotorOnSixKilovoltBusBreaksDownAndOvercurrentTrips` is timing-sensitive under a loaded
  server (breakdown seen at 7 ticks against a limit of 5); it passed on the rerun.
- The MES server closes the loop only on units with raise/lower panel buttons (shaft generator trim, diesel, pumped storage). LOCA and blackout are wired to the VVER, BWR, CANDU and AM-1, not to the SFR
  (its sodium pool has only the sodium fires). The steel-clad AM-1 core does not burst its cladding in the accident
  layer; it loses channels to dryout in its own channel model. Liquid waste in pipes has a fixed nominal
  activity per fluid. MOX rod-worth loss and the CANDU moderator purity coefficient are estimates.
- About half of today's machines are redrawn; the rest still borrow models (waste blocks, GS tower, heavy water
  column and others). No new machine has a painted panel background yet.
