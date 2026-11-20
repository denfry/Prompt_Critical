"""Later-era installations of Prompt Critical for the film set, ported 1:1 from the GameTests that assemble them.

Every `build_<name>(f, ox, oy, oz)` places a unit at the absolute origin (ox, oy, oz) = the GameTest structure-block
position: a GameTest relative position (x, y, z) lands at (ox + x, oy + y, oz + z). In a GameTest rel y = 0 is the
template floor, so on the superflat film world a unit whose lowest block is at rel y = 1 stands on the grass with
oy = G (-61); `GROUND[name]` is the lowest rel y of each unit, and `standing_oy(name)` gives the oy that puts it on
the grass (some tests build at rel y = 2 and would float one block otherwise).

`start_<name>(f, ox, oy, oz)` holds the commands that start a unit (fuel, charges, panel buttons). Run it from a
function scheduled a few seconds after the build: block entities find their twins on their first ticks, and the
panel commands are ignored until then. Things that the tests do through Java only (direct sim calls) are listed in
each start function's docstring together with the command substitute used.

Creative motors that the tests place next to a machine on the ground are hidden under a gearbox (filmset's
`motor_gearbox`); motors higher up are placed as in the test.

    python art/film/eras.py      # builds all units in a row and prints the command count per unit
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from filmset import Fn, PC, G, motor_gearbox  # noqa: E402


class _At:
    """Origin-relative placement into a Fn."""

    def __init__(self, f, ox, oy, oz):
        self.f, self.o = f, (ox, oy, oz)

    def p(self, x, y, z):
        return self.o[0] + x, self.o[1] + y, self.o[2] + z

    def xyz(self, x, y, z):
        return "%d %d %d" % self.p(x, y, z)

    def set(self, x, y, z, block):
        self.f.setb(*self.p(x, y, z), block)

    def fill(self, x1, y1, z1, x2, y2, z2, block, mode=""):
        self.f.fill(*self.p(x1, y1, z1), *self.p(x2, y2, z2), block, mode)

    def use(self, x, y, z, item="", count=1):
        """`pc use`: right click by a fake player with the item in hand (empty hand without an item)."""
        tail = "" if not item else " " + item + ("" if count == 1 else " %d" % count)
        self.f("pc use " + self.xyz(x, y, z) + tail)

    def panel(self, x, y, z, button, times=1):
        for _ in range(times):
            self.f("pc panel %s %d" % (self.xyz(x, y, z), button))

    def merge(self, x, y, z, nbt):
        self.f("data merge block %s %s" % (self.xyz(x, y, z), nbt))


def pc(block_id, **props):
    """`promptcritical:<id>[k=v,...]` (props in the given order)."""
    s = PC + block_id
    if props:
        s += "[" + ",".join("%s=%s" % (k, str(v).lower()) for k, v in props.items()) + "]"
    return s


def _motor(a, x, y, z, toward, rpm=64):
    """GameTest `motor(h, rel, toward, rpm)`: creative motor facing the machine. On the ground it becomes a gearbox
    over a sunk motor; the gearbox axis is perpendicular to the shaft it drives."""
    ax, ay, az = a.p(x, y, z)
    if ay == G + 1:
        motor_gearbox(a.f, ax, ay, az, "z" if toward in ("east", "west") else "x", rpm)
    else:
        a.f.setb(ax, ay, az, "create:creative_motor[facing=%s]{ScrollValue:%d}" % (toward, rpm))


def _hopper(a, x, y, z, items):
    """Vanilla hopper pointing down, loaded with [(item_id, count), ...]."""
    inv = ",".join('{Slot:%db,id:"%s",count:%d}' % (i, it, n) for i, (it, n) in enumerate(items))
    a.set(x, y, z, "hopper[facing=down]{Items:[%s]}" % inv)


# ------------------------------------------------------------------------------------------------ 1. coke battery

def build_coke_battery(f, ox, oy, oz):
    """IronworksGameTests.buildBattery + the door chest of cokeBatteryFormsChargesAndPushesIntoAChest: 3 ovens × 4
    along X (walls z = 1, 3, 5, 7, ovens z = 2, 4, 6, x 3…6, y 1…3), gas collector risers over each oven."""
    a = _At(f, ox, oy, oz)
    for z in range(1, 8):
        a.fill(3, 1, z, 6, 3, z, pc("coke_battery_wall") if z % 2 == 1 else pc("coke_oven"))
    for z in (2, 4, 6):
        a.set(3, 4, z, pc("gas_collector_main"))
    a.set(7, 1, 2, "chest[facing=west]")  # coke-side door of oven 0 (the test places it before the push)


def start_coke_battery(f, ox, oy, oz):
    """Test: 64 washed coal into the hatch (5, 3, 2) from above → hopper over the hatch (same side, same handler).
    Java only: cookUntilReady() heats the walls in the sim, ramPush() pushes the oven. Substitute: coal into the
    heating fireboxes (`pc use` on a cell → master firebox); the real heat-up takes in-game days, the pusher is a
    Create contraption and is not built here."""
    a = _At(f, ox, oy, oz)
    _hopper(a, 5, 4, 2, [(PC + "washed_coal", 64)])
    a.use(3, 1, 2, "minecraft:coal", 64)
    a.use(3, 1, 2, "minecraft:coal", 64)


# ------------------------------------------------------------------------------------------------ 2. blast furnace

def build_blast_furnace(f, ox, oy, oz):
    """IronworksGameTests.buildFurnace + the cast house machine of blastFurnaceFormsTakesChargeAndRefusesToDrillCold:
    5×5 shell around (5, 5), hearth y = 1, shell up to y = 12 (V_u 99 m³), taphole (5, 2, 3) north, four tuyeres at
    y = 3, top (5, 13, 5), controller (2, 4, 5) on the west wall, taphole drill (4, 1, 1)."""
    a = _At(f, ox, oy, oz)
    shell = pc("blast_furnace_shell")
    a.fill(3, 1, 3, 7, 12, 7, shell)
    a.fill(4, 2, 4, 6, 12, 6, "air")
    a.set(5, 2, 3, pc("blast_furnace_taphole", facing="north"))
    a.set(5, 3, 3, pc("blast_furnace_tuyere", facing="north"))
    a.set(5, 3, 7, pc("blast_furnace_tuyere", facing="south"))
    a.set(3, 3, 5, pc("blast_furnace_tuyere", facing="west"))
    a.set(7, 3, 5, pc("blast_furnace_tuyere", facing="east"))
    a.set(5, 13, 5, pc("blast_furnace_top"))
    a.set(2, 4, 5, pc("blast_furnace_controller", facing="west"))
    a.set(4, 1, 1, pc("cast_house_machine", facing="south"))  # drill kind; cells (4, 1, 1) and (3, 1, 1)
    # Not in the tests (no GameTest blows a furnace): the cast house and the blast, laid out from the controller's
    # rules. Runner: BlastFurnaceControllerBlockEntity.runnerPath starts one block outward of the taphole (5, 2, 2)
    # and follows iron_runner blocks; ladles() takes a ladle cell one below and beside a runner block, so the 2×2×2
    # ladle (5, 1, -1)…(6, 2, 0) catches from (5, 2, 1). Blower 2×2×2 (9, 1, 5)…(10, 2, 6), shaft on its north face,
    # within LINK_RANGE 12; 96 rpm → 1.5 × 1.5 = 2.25 nm³/s cold blast (0.89 of the 99 m³ furnace's rated 2.52).
    # No hot-blast stoves: the blast is cold (150 °C).
    for z in (2, 1):
        a.set(5, 1, z, "stone_bricks")
        a.set(5, 2, z, pc("iron_runner", facing="north"))
    a.set(5, 1, -1, pc("ladle", facing="north"))
    a.set(9, 1, 5, pc("blast_blower", facing="north"))
    _motor(a, 9, 1, 4, "south", 96)


def start_blast_furnace(f, ox, oy, oz):
    """Test: 5 coke + 3 sinter into the top's item handler from UP → hopper on the top's upper part (5, 15, 5); the
    part forwards the item capability to the top. Then `/filmset furnace <controller> hot` — not from a test (none
    gets past COLD; blow-in is 2 physical days): a sim-state preset through the furnace's save/load to RUNNING, hearth
    1600 °C with iron above the taphole and a full burden column; the blower keeps it blown. Tap on camera with an
    empty-hand right click on the drill: `pc use ox+4 oy+1 oz+1` (CastHouseMachineBlockEntity.act → drill). Re-run
    the furnace command to reheat and refill the hearth (cold blast cools it slowly)."""
    a = _At(f, ox, oy, oz)
    _hopper(a, 5, 15, 5, [(PC + "coke", 5), (PC + "sinter", 3)])
    f("filmset furnace %s hot" % a.xyz(2, 4, 5))


# ------------------------------------------------------------------------------------------------ 3. arc furnace

def build_arc_furnace(f, ox, oy, oz):
    """ArcFurnaceGameTests.scrapChargesButNoBusMeansNoArc: EAF controller (5, 2, 5), body x 4…6, y 1…5, z 4…6."""
    a = _At(f, ox, oy, oz)
    a.set(5, 2, 5, pc("electric_arc_furnace", facing="north"))


def start_arc_furnace(f, ox, oy, oz):
    """Test: useItem(16 steel scrap), use() lowers the electrodes — both are right clicks, ported 1:1. As in the test
    there is no 6 kV bus, so the arc draws no power and the charge stays cold (SteelworksGameTests has no
    converter/BOF rig at all)."""
    a = _At(f, ox, oy, oz)
    a.use(5, 2, 5, PC + "steel_scrap", 16)
    a.use(5, 2, 5)


# ------------------------------------------------------------------------------------------------ 4. steam turbine + generator

def build_steam_turbine(f, ox, oy, oz):
    """TurbineGameTests.condenserByWaterGivesVacuum (turbine (1, 2, 1) on a condenser, water east of it) and the
    generator set of DispatchGameTests.dispatchCenterSeesTheGeneratorsIsland (shaft generator (4, 1, 5), exciter on
    top, motor south of it, dispatch center (8, 1, 8)) in the same frame. No test couples a steam turbine to a
    generator. Not from the tests: three stone bricks that keep the water source from flowing over the flat."""
    a = _At(f, ox, oy, oz)
    a.set(1, 2, 1, pc("steam_turbine", facing="north"))
    a.set(1, 1, 1, pc("condenser"))
    for x, z in ((3, 1), (2, 0), (2, 2)):
        a.set(x, 1, z, "stone_bricks")
    a.set(2, 1, 1, "water")
    a.set(4, 1, 5, pc("shaft_generator", facing="north"))
    a.set(4, 2, 5, pc("generator_exciter", facing="north"))
    _motor(a, 4, 1, 6, "north")
    a.set(8, 1, 8, pc("grid_dispatch_center", facing="north"))


def start_steam_turbine(f, ox, oy, oz):
    """Test (Dispatch): setVoltageClass(MV_6_3), gear to 64 rpm, field on. Gear 64 is the default; the field is
    panel button 13 (GridPanels.BUTTON_FIELD). The class switch (button 19) is refused while the shaft turns and the
    motor spins from the build on, so the generator stays on 0.4 kV. The turbine has no boiler in any test: it
    stands still with its condenser under vacuum."""
    a = _At(f, ox, oy, oz)
    a.panel(4, 1, 5, 13)


# ------------------------------------------------------------------------------------------------ 5. drilling rig

def build_drilling_rig(f, ox, oy, oz):
    """DrillingGameTests.buildRig: console, rotary table on a motor, derrick, drawworks, mud pump on a motor, annular
    BOP, choke manifold, six mud tanks, mixing hopper, BOP accumulator (all at rel y = 2)."""
    a = _At(f, ox, oy, oz)
    a.set(1, 2, 1, pc("drillers_console", facing="north"))
    a.set(6, 2, 6, pc("rotary_table", facing="north"))
    _motor(a, 5, 2, 6, "east")
    a.set(6, 5, 6, pc("derrick_segment", facing="north"))
    a.set(6, 2, 9, pc("drawworks", facing="north"))
    a.set(3, 2, 9, pc("mud_pump", facing="north"))
    _motor(a, 1, 2, 10, "east")  # drive end (-1, 0, 1) of the pump model is (2, 2, 10)
    a.set(8, 2, 6, pc("bop_annular", facing="north"))
    a.set(8, 2, 3, pc("choke_manifold", facing="north"))
    for x in range(1, 7):
        a.set(x, 2, 4, pc("mud_tank", facing="north"))
    a.set(10, 2, 1, pc("mud_mixing_hopper", facing="north"))
    a.set(4, 2, 1, pc("bop_accumulator", facing="north"))


def start_drilling_rig(f, ox, oy, oz):
    """Test: c.spud(gas trap with 11.8 kPa/m at 800 m) and c.setDrilling(120 kN, 100 spm) — Java only. Substitute:
    the console spuds by itself into the world's prospect once the rig is complete (first scan, ≤ 2 s); WOB and pump
    rate are set from the panel: WOB+ ×6 (20 kN steps), SPM+ ×10 (10 spm steps). The kick/kill sequence of the
    test needs the artificial over-pressured trap: `/filmset kick ox+1 oy+2 oz+1 [depth]` spuds the test's gas trap
    (pay top 800 m) under the rotary table at `depth` (default 799.5 m) and drills with 120 kN, 100 spm. Then the
    console panel (`pc panel ox+1 oy+2 oz+1 <n>`): 4 BOP (close/open annular), 5 Kill (next Driller's method step);
    `/filmset barite ox+1 oy+2 oz+1` weights the pits between the circulations as the test does."""
    # the rig stays idle at build: WOB on the panel makes the console spud into the world's own prospect, and a gas
    # pay there blew the well out and wrecked the derrick long before the camera came; the kick is started on camera
    pass


# ------------------------------------------------------------------------------------------------ 6. oil field

def build_oil_field(f, ox, oy, oz):
    """ProductionGameTests.completeWell + pumpjackOnDeadWell (rodPumpCardCounterweightsAndEcho): wellhead (2, 2, 2)
    with the Christmas tree on it, three-phase separator (6, 2, 2), flare tip (10, 2, 2), pumpjack (5, 2, 5) driven
    from the west."""
    a = _At(f, ox, oy, oz)
    a.set(2, 2, 2, pc("wellhead", facing="north"))
    a.set(2, 3, 2, pc("christmas_tree", facing="north"))
    a.set(6, 2, 2, pc("three_phase_separator", facing="north"))
    a.set(10, 2, 2, pc("flare_tip"))
    a.set(5, 2, 5, pc("pumpjack", facing="north"))
    _motor(a, 3, 2, 5, "east")


def start_oil_field(f, ox, oy, oz):
    """Test: casing 64 + 64 seamless pipes, perforation with 64 seismic charges — right clicks, ported 1:1.
    Java only: DrilledWells.put(bore 930 m into an oil trap) registers the bore the wellhead completes. Without a
    bore at the wellhead's x/z the clicks answer `no_bore` and only the pumpjack turns. Substitute: drill there
    first — the drilling rig's console records its hole at the rotary table's x/z every 2 s — then build this unit
    with the wellhead on that x/z."""
    a = _At(f, ox, oy, oz)
    a.use(2, 2, 2, PC + "seamless_pipe", 64)
    a.use(2, 2, 2, PC + "seamless_pipe", 64)
    a.use(2, 2, 2, PC + "seismic_charge", 64)


# ------------------------------------------------------------------------------------------------ 7. refinery

def build_refinery(f, ox, oy, oz):
    """RefineryUpgradingGameTests.hydrotreaterFeedsClausWithHydrogenSulfide: hydrotreater (5, 2, 5), Claus unit
    touching it from the east."""
    a = _At(f, ox, oy, oz)
    a.set(5, 2, 5, pc("hydrotreater", facing="north"))
    a.set(6, 2, 5, pc("claus_unit", facing="north"))


def start_refinery(f, ox, oy, oz):
    """Test: CoMo catalyst into the input slot (right click, accepted by the unit), then diesel and hydrogen poured
    into the nozzles every tick. Substitute for the pouring: the feed tanks set full through NBT (`diesel`, `h2`,
    litres, capacity 8000) — repeat this function to keep it running."""
    a = _At(f, ox, oy, oz)
    a.use(5, 2, 5, PC + "catalyst_hydrotreating")
    a.merge(5, 2, 5, "{diesel:8000d,h2:8000d}")


# ------------------------------------------------------------------------------------------------ 8. pyrolysis

def build_pyrolysis(f, ox, oy, oz):
    """PyrolysisGameTests.placeFurnace(withSeparation) (furnaceCracksPropaneIntoOlefins): pyrolysis furnace
    (3, 3, 5) and the cracked gas unit (6, 3, 5) touching it, both 3×3×3."""
    a = _At(f, ox, oy, oz)
    a.set(3, 3, 5, pc("pyrolysis_furnace", facing="north"))
    a.set(6, 3, 5, pc("cracked_gas_unit", facing="north"))


def start_pyrolysis(f, ox, oy, oz):
    """Test: setFeed(PROPANE) (the default), setCotK(1103.15 K) and setMode(RUN) — Java; and propane, boiler
    feedwater and sales gas poured into the inlet. Substitute: COT− ×4 (panel 3, 5 K each from the default
    1123.15 K), RUN (panel 0); feed tanks set through NBT (`in0` propane, `in4` feedwater, `in5` fuel gas)."""
    a = _At(f, ox, oy, oz)
    a.merge(3, 3, 5, "{in0:8000d,in4:8000d,in5:4000d}")
    a.panel(3, 3, 5, 3, 4)
    a.panel(3, 3, 5, 0)


# ------------------------------------------------------------------------------------------------ 9. gas turbine + HRSG

def build_combined_cycle(f, ox, oy, oz):
    """CombinedCycleGameTests.hrsgTurnsGasTurbineExhaustIntoBoilerSteam: gas turbine (5, 2, 1) x 4…6, z 0…8, HRSG
    (5, 2, 10) behind it, fire-tube boiler (2, 2, 7) to the west. Added from GasTurbineGameTests: the turbine
    gearbox in front of the compressor (5, 2, -1), so the unit reaches z = -1."""
    a = _At(f, ox, oy, oz)
    a.set(5, 2, 1, pc("gas_turbine", facing="north"))
    a.set(5, 2, 10, pc("hrsg", facing="north"))
    a.set(2, 2, 7, pc("fire_tube_boiler", facing="north"))
    a.set(5, 2, -1, pc("turbine_gearbox", facing="north"))


def start_combined_cycle(f, ox, oy, oz):
    """Test: 4000 mB mine methane into the turbine, turbine().setRun(true), boiler preset to 2 bar with water
    (SteamPlant.presetWater — Java only). Substitute: the gas buffer set through NBT, then an empty-hand right click
    (GasTurbineBlockEntity.use starts it when it has fuel); the boiler gets 16 000 mB of feedwater the way filmset's
    refresh does — it builds pressure from the exhaust heat instead of being preset."""
    a = _At(f, ox, oy, oz)
    a.merge(5, 2, 1, '{gas:{Fluid:{id:"%smine_methane",amount:4000}}}' % PC)
    a.merge(2, 2, 7, '{feed:{Fluid:{id:"minecraft:water",amount:16000}}}')
    a.use(5, 2, 1)


# ------------------------------------------------------------------------------------------------ 10. centrifuge cascade

def build_enrichment(f, ox, oy, oz):
    """EnrichmentGameTests.placeHall(20) (cascadeEnrichesNaturalUranium): cascade panel (6, 2, 6) on a motor from the
    south, 20 centrifuge sections at y = 2 filling x 1…7, z 1…3 column by column."""
    a = _At(f, ox, oy, oz)
    a.set(6, 2, 6, pc("centrifuge_cascade", facing="north"))
    _motor(a, 6, 2, 7, "north")
    placed = 0
    for x in range(1, 11):
        for z in range(1, 4):
            if placed < 20:
                a.set(x, 2, z, pc("gas_centrifuge"))
                placed += 1


def _uf6(slot, item, kg=0.0):
    comp = "" if kg <= 0 else ',components:{"%suf6":{uf6_kg:%.1fd,u235:0.00711d,u234:0.000054d}}' % (PC, kg)
    return '{Slot:%d,id:"%s%s",count:1%s}' % (slot, PC, item, comp)


def start_enrichment(f, ox, oy, oz):
    """Test: cylinders set into the panel slots (feed 48Y with 2000 kg natural UF₆, empty 30B product, empty 48Y
    tails) — ported as the BE's `cylinders` NBT with the `promptcritical:uf6` component; hall.setDesign(CARBON),
    setSpeedFraction(0.8), setRunning(true) — Java, ported to the panel: DESIGN ×2 (aluminium → maraging → carbon),
    SPEED− ×5 (0.85 → 0.80), RUN."""
    a = _At(f, ox, oy, oz)
    cyl = ",".join((_uf6(0, "uf6_cylinder_48y", 2000), _uf6(1, "uf6_cylinder_30b"), _uf6(2, "uf6_cylinder_48y")))
    a.merge(6, 2, 6, "{cylinders:{Items:[%s],Size:3}}" % cyl)
    a.panel(6, 2, 6, 1, 2)
    a.panel(6, 2, 6, 6, 5)
    a.panel(6, 2, 6, 0)


# ------------------------------------------------------------------------------------------------ 11. PWR / VVER

def build_pwr(f, ox, oy, oz):
    """PwrReactorGameTests.losingPumpDriveAtPowerTrips layout: reactor (6, 3, 6) (two 3×3×3 segments, y 2…7),
    pressurizer (10, 2, 2), main coolant pumps (1, 2, 1) and (3, 2, 1), each on a motor from the south."""
    a = _At(f, ox, oy, oz)
    a.set(6, 3, 6, pc("pwr_reactor", facing="north"))
    a.set(10, 2, 2, pc("pressurizer", facing="north"))
    for x in (1, 3):
        a.set(x, 2, 1, pc("primary_pump", facing="north"))
        _motor(a, x, 2, 2, "north")


def start_pwr(f, ox, oy, oz):
    """Test: setRods(1, 1) and r.load() of 19 VVER-440 assemblies → `/filmset reactor <pos> load` (same calls).
    Power on camera: `/filmset reactor ox+6 oy+3 oz+6 power <pct>` — resetTrip, setThermalState, scram bank out,
    group at 30 %, boron bisected to criticality, equilibrate, as PwrReactorGameTests / SevereAccidentGameTests do;
    it is refused until both pumps are at speed and the reset hold has passed (the test waits 12 s after the pumps).
    SCRAM: panel button 0 (`pc panel ox+6 oy+3 oz+6 0`). No steam generator: the primary has no heat sink (as in
    the tests), so it heats up at power."""
    a = _At(f, ox, oy, oz)
    f("filmset reactor %s load" % a.xyz(6, 3, 6))


# ------------------------------------------------------------------------------------------------ 12. control room

def build_control_room(f, ox, oy, oz):
    """ControlRoomGameTests.smokeDegradesTheRoomAndTheDeskShowsTheAlarm: a stone box x 1…7, y 1…5, z 1…7 with 5×3×5
    air inside, control room core (2, 2, 2) facing south, desk (4, 2, 4), annunciator (6, 3, 4) facing west."""
    a = _At(f, ox, oy, oz)
    a.fill(1, 1, 1, 7, 5, 7, "stone", "hollow")
    a.set(2, 2, 2, pc("control_room_core", facing="south"))
    a.set(4, 2, 4, pc("control_desk", facing="north"))
    a.set(6, 3, 4, pc("annunciator_panel", facing="west"))


def start_control_room(f, ox, oy, oz):
    """Test: soul sand under (3, 2, 5) and soul fire on it — ported 1:1. The core sees the smoke, the room turns
    DEGRADED, the annunciator raises the amber alarm and the desk lists it (ACK ALL is a desk button, not pressed)."""
    a = _At(f, ox, oy, oz)
    a.set(3, 1, 5, "soul_sand")
    a.set(3, 2, 5, "soul_fire")


# ------------------------------------------------------------------------------------------------ registry

ERAS = {
    "coke_battery": (build_coke_battery, start_coke_battery, (8, 5, 8),
                     "a 3-oven coke battery forms; washed coal drops into oven 0 and the heating fireboxes burn coal"),
    "blast_furnace": (build_blast_furnace, start_blast_furnace, (11, 16, 8),
                      "a 99 m³ blast furnace on blast runs hot; the drill opens the taphole and iron runs down the runner into the ladle"),
    "arc_furnace": (build_arc_furnace, start_arc_furnace, (7, 6, 7),
                    "scrap goes into the arc furnace and the electrodes come down; no 6 kV bus, no arc"),
    "steam_turbine": (build_steam_turbine, start_steam_turbine, (9, 3, 9),
                      "a steam turbine on its condenser by the water, and a shaft generator on a shaft with its field on, seen by the dispatch center"),
    "drilling_rig": (build_drilling_rig, start_drilling_rig, (11, 7, 11),
                     "the rotary table and mud pump turn, the rig spuds and drills with 120 kN on the bit at 100 spm"),
    "oil_field": (build_oil_field, start_oil_field, (11, 5, 7),
                  "the pumpjack nods on its shaft beside the wellhead, separator and flare (it produces only on a drilled bore)"),
    "refinery": (build_refinery, start_refinery, (7, 3, 6),
                 "the hydrotreater desulphurises diesel with hydrogen and the Claus unit next to it makes sulphur"),
    "pyrolysis": (build_pyrolysis, start_pyrolysis, (8, 5, 7),
                  "the pyrolysis furnace lights and cracks propane, the separation unit draws ethylene and propylene"),
    "combined_cycle": (build_combined_cycle, start_combined_cycle, (7, 7, 12),
                       "the gas turbine lights on mine methane and turns its gearbox, the HRSG passes exhaust heat to the fire-tube boiler"),
    "enrichment": (build_enrichment, start_enrichment, (8, 3, 8),
                   "20 centrifuge sections spin up; the cascade draws natural UF6 and fills 4.5 % product and 0.25 % tails"),
    "pwr": (build_pwr, start_pwr, (11, 8, 8),
            "two main coolant pumps run up, the VVER takes 19 assemblies and the pressurizer links to it"),
    "control_room": (build_control_room, start_control_room, (8, 6, 8),
                     "a fire in the closed control room fills it with smoke, the annunciator and desk raise the degraded-room alarm"),
}

# lowest rel y of each unit (the tests build either on rel y = 1 or on rel y = 2)
GROUND = {"coke_battery": 1, "blast_furnace": 1, "arc_furnace": 1, "steam_turbine": 1, "drilling_rig": 2, "oil_field": 2,
          "refinery": 2, "pyrolysis": 2, "combined_cycle": 1, "enrichment": 2, "pwr": 2, "control_room": 1}


def standing_oy(name, ground=G):
    """Origin y that puts unit `name` on top of the floor layer `ground`."""
    return ground + 1 - GROUND[name]


if __name__ == "__main__":
    x, z = 0, 130
    total = 0
    for name, (build, start, (sx, sy, sz), _desc) in ERAS.items():
        f = Fn()
        oy = standing_oy(name)  # rel y = 1 or 2 at -60, the standing level of the superflat set
        build(f, x, oy, z)
        n_build = len(f.lines)
        if start:
            start(f, x, oy, z)
        print("%-15s at (%4d, %d, %d)  size %-12s build %3d  start %2d" % (name, x, oy, z, (sx, sy, sz), n_build,
                                                                          len(f.lines) - n_build))
        total += len(f.lines)
        x += sx + 6
    print("total", total)
