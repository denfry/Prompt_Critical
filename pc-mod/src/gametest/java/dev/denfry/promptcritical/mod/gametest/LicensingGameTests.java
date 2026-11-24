package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.ops.HydrotestPumpBlockEntity;
import dev.denfry.promptcritical.mod.ops.OpsActions;
import dev.denfry.promptcritical.mod.ops.OpsItem;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.world.item.ItemStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridPanels;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.reactor.AchesonFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.reactor.PileControllerBlockEntity;
import dev.denfry.promptcritical.mod.reactor.ReactorPanels;
import dev.denfry.promptcritical.sim.grid.BreakerResponse;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceControllerBlockEntity;
import dev.denfry.promptcritical.sim.ops.CommissioningMode;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import net.minecraft.world.level.block.state.BlockState;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.content.HandbookManager;
import dev.denfry.promptcritical.mod.content.RouteKeyTasks;
import dev.denfry.promptcritical.mod.ops.LicenceCommand;
import dev.denfry.promptcritical.mod.ops.LicensingWorldData;
import dev.denfry.promptcritical.mod.ops.TeamResolver;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.CheckInput;
import dev.denfry.promptcritical.sim.ops.LicenceVerdict;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PlantEra;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Профиль licensing в мире (спецификация профиля, план HP1): отказ в пуске непроверенного котла, владелец по команде
 * игрока без FTB Teams, выдача лицензии I1 по выполненной I0 и неизменность флага мира. Свой батч: профиль общий для
 * уровня, каждый тест выключает его в конце.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LicensingGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos BOILER = new BlockPos(2, 2, 2);

    private LicensingGameTests() {}

    private static OpsSubsystem ops(GameTestHelper h) {
        return SimBindings.get(h.getLevel()).world().subsystem(OpsSubsystem.class);
    }

    private static FireTubeBoilerBlockEntity placeBoiler(GameTestHelper h) {
        h.setBlock(BOILER, PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        return h.getBlockEntity(BOILER);
    }

    private static boolean ready(GameTestHelper h) {
        FireTubeBoilerBlockEntity b = h.getBlockEntity(BOILER);
        return !b.simId().isNone() && ops(h).unit(b.simId()) != null;
    }

    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing")
    public static void licensingRefusesUncheckedBoiler(GameTestHelper h) {
        placeBoiler(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(ready(h), "boiler and its ops unit"))
                .thenExecute(() -> {
                    OpsSubsystem ops = ops(h);
                    ops.setLicensing(true);
                    try {
                        SimId id = ((FireTubeBoilerBlockEntity) h.getBlockEntity(BOILER)).simId();
                        h.assertTrue(!ops.canStart(id).allowed(), "unchecked boiler refused under licensing");
                    } finally {
                        ops.setLicensing(false);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing")
    public static void soloPlayerOwnsWhatTheyCheck(GameTestHelper h) {
        placeBoiler(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(ready(h), "boiler and its ops unit"))
                .thenExecute(() -> {
                    OpsSubsystem ops = ops(h);
                    Player p = TestPlayers.of(h);
                    UUID team = TeamResolver.team(p);
                    h.assertValueEqual(team, p.getUUID(), "without FTB Teams a player is their own team");
                    SimId id = ((FireTubeBoilerBlockEntity) h.getBlockEntity(BOILER)).simId();
                    ops.perform(id, new CheckInput.InstrumentCheck(), p.getUUID(), team);
                    h.assertValueEqual(ops.licences().owner(id).orElse(null), p.getUUID(), "checker's team owns the unit");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100, batch = "licensing")
    public static void applyI1WhenI0Done(GameTestHelper h) {
        OpsSubsystem ops = ops(h);
        UUID team = UUID.randomUUID(); // мир GameTest сохраняется между запусками: команда каждый раз новая
        Set<String> keys = RouteKeyTasks.of(HandbookManager.data(), "I0");
        h.assertTrue(!keys.isEmpty(), "I0 has key route tasks");
        LicenceVerdict before = LicenceCommand.apply(h.getLevel().getServer(), team, PlantEra.I1, 0);
        h.assertTrue(!before.granted(), "nothing done yet");
        for (String k : keys) ops.licences().taskDone(team, k);
        for (UnitKind k : UnitKind.values()) if (k.era() == PlantEra.I0) ops.licences().accepted(team, k);
        LicenceVerdict after = LicenceCommand.apply(h.getLevel().getServer(), team, PlantEra.I1, 0);
        h.assertTrue(after.granted(), "I1 granted: " + after);
        h.assertTrue(ops.licences().licensed(team, PlantEra.I1), "licence recorded");
        h.succeed();
    }

    /** Ревью HP1: мир, существовавший до профиля (время уже идёт), не получает licensing из конфига. */
    @GameTest(template = LARGE, timeoutTicks = 100, batch = "licensing")
    public static void onlyNewWorldsTakeTheConfig(GameTestHelper h) {
        h.assertTrue(LicensingWorldData.decide(true, 0), "a fresh world takes licensing from the config");
        h.assertTrue(!LicensingWorldData.decide(true, 72_000), "an existing world stays without licensing");
        h.assertTrue(!LicensingWorldData.decide(false, 0), "config off stays off");
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100, batch = "licensing")
    public static void worldFlagIsFixed(GameTestHelper h) {
        var server = h.getLevel().getServer();
        boolean stored = LicensingWorldData.resolve(server, false);
        h.assertValueEqual(LicensingWorldData.resolve(server, !stored), stored, "config change does not flip the world");
        h.succeed();
    }

    // --- HP2.1: генератор и домна ---

    private static final BlockPos GEN = new BlockPos(2, 2, 2);
    private static final String TALL = "gametest/empty_12x24x12";

    private static void placeGenerator(GameTestHelper h, int rpm) {
        h.setBlock(GEN, PcBlocks.SHAFT_GENERATOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(GEN.above(), PcBlocks.GENERATOR_EXCITER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        if (rpm > 0) {
            BlockPos motor = GEN.south();
            h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
            h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(rpm));
        }
    }

    private static ShaftGeneratorBlockEntity generator(GameTestHelper h) {
        return h.getBlockEntity(GEN);
    }

    private static boolean generatorReady(GameTestHelper h) {
        ShaftGeneratorBlockEntity g = generator(h);
        return g.unit() != null && !g.simId().isNone() && ops(h).unit(g.simId()) != null;
    }

    /** Возбуждение генератора I1: без лицензии отказ даже после проверок (нажатие как с CC), с лицензией — включается. */
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing_gen_field")
    public static void generatorFieldNeedsTheI1Licence(GameTestHelper h) {
        placeGenerator(h, 0);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(generatorReady(h), "generator and its ops unit"))
                .thenExecute(() -> {
                    OpsSubsystem ops = ops(h);
                    ops.setLicensing(true);
                    try {
                        var fake = TestPlayers.of(h);
                        UUID team = UUID.randomUUID(); // мир GameTest сохраняется: лицензии прошлых запусков не мешают
                        SimId id = generator(h).simId();
                        h.assertTrue(ops.perform(id, new CheckInput.InsulationTest(true), fake.getUUID(), team).passed(), "insulation");
                        h.assertTrue(ops.perform(id, new CheckInput.Alignment(true), fake.getUUID(), team).passed(), "alignment");
                        generator(h).onPanelButton(fake, GridPanels.BUTTON_FIELD);
                        h.assertTrue(!generator(h).unit().fieldOn(), "field refused without the I1 licence");
                        h.assertValueEqual(ops.canStart(id).unlicensedEra(), PlantEra.I1, "missing licence named");
                        boolean journaled = false;
                        for (OpsLogEntry e : ops.log(id)) journaled |= e.kind() == OpsLogEntry.Kind.START_REFUSED;
                        h.assertTrue(journaled, "automation refusal journaled");
                        ops.licences().grant(team, PlantEra.I1, 0);
                        generator(h).onPanelButton(fake, GridPanels.BUTTON_FIELD);
                        h.assertTrue(generator(h).unit().fieldOn(), "field on with the I1 licence");
                    } finally {
                        ops.setLicensing(false);
                    }
                })
                .thenSucceed();
    }

    /** Вал крутит генератор без возбуждения — это не пуск: отказов в журнале нет. */
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing_gen_roll")
    public static void generatorRollsWithoutField(GameTestHelper h) {
        placeGenerator(h, 64);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(generatorReady(h), "generator and its ops unit"))
                .thenExecute(() -> ops(h).setLicensing(true))
                .thenWaitUntil(() -> h.assertTrue(generator(h).unit().speedPu() > 0.05, "shaft turns the generator"))
                .thenExecute(() -> {
                    OpsSubsystem ops = ops(h);
                    try {
                        boolean refused = false;
                        for (OpsLogEntry e : ops.log(generator(h).simId())) refused |= e.kind() == OpsLogEntry.Kind.START_REFUSED;
                        h.assertTrue(!refused, "rolling is not a start");
                    } finally {
                        ops.setLicensing(false);
                    }
                })
                .thenSucceed();
    }

    private static void buildFurnace(GameTestHelper h) {
        BlockState shell = PcBlocks.BLAST_FURNACE_SHELL.get().defaultBlockState();
        for (int y = 1; y <= 12; y++)
            for (int x = 3; x <= 7; x++)
                for (int z = 3; z <= 7; z++) {
                    boolean ring = x == 3 || x == 7 || z == 3 || z == 7;
                    if (y == 1 || ring) h.setBlock(new BlockPos(x, y, z), shell);
                }
        h.setBlock(new BlockPos(5, 2, 3), PcBlocks.BLAST_FURNACE_TAPHOLE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockState tuyere = PcBlocks.BLAST_FURNACE_TUYERE.get().defaultBlockState();
        h.setBlock(new BlockPos(5, 3, 3), tuyere.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(new BlockPos(5, 3, 7), tuyere.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        h.setBlock(new BlockPos(3, 3, 5), tuyere.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        h.setBlock(new BlockPos(7, 3, 5), tuyere.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(new BlockPos(5, 13, 5), PcBlocks.BLAST_FURNACE_TOP.get().defaultBlockState());
        h.setBlock(new BlockPos(2, 4, 5), PcBlocks.BLAST_FURNACE_CONTROLLER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
    }

    /**
     * Разрешение на процесс домны следует за профилем: под licensing непринятая печь его не получает (задувка в модели —
     * BlastFurnacePermissiveTest); без licensing в ADVISORY и OFF печь работает, как прежде.
     */
    @GameTest(template = TALL, timeoutTicks = 300, batch = "licensing_bf_permit")
    public static void furnacePermissiveFollowsLicensing(GameTestHelper h) {
        buildFurnace(h);
        BlockPos controller = new BlockPos(2, 4, 5);
        CommissioningMode[] before = new CommissioningMode[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    h.assertTrue(c.unit() != null && c.unit().furnace() != null && ops(h).unit(c.simId()) != null, "furnace formed and registered");
                })
                .thenExecute(() -> {
                    before[0] = ops(h).config().commissioning();
                    ops(h).setLicensing(true);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    boolean permitted = c.unit().furnace().permitted();
                    ops(h).setLicensing(false);
                    h.assertTrue(!permitted, "unaccepted furnace has no process permissive under licensing");
                    ops(h).setConfig(ops(h).config().withCommissioning(CommissioningMode.ADVISORY));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    h.assertTrue(c.unit().furnace().permitted(), "ADVISORY furnace still runs");
                    ops(h).setConfig(ops(h).config().withCommissioning(CommissioningMode.OFF));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    boolean permitted = c.unit().furnace().permitted();
                    ops(h).setConfig(ops(h).config().withCommissioning(before[0]));
                    h.assertTrue(permitted, "OFF furnace still runs");
                })
                .thenSucceed();
    }

    /**
     * Ревью HP2.1: домну можно принять — насос гидроиспытания видит её пульт, течь шва заделывается кувалдой у холодной
     * печи, после КИП и пробной работы печь принята.
     */
    @GameTest(template = TALL, timeoutTicks = 300, batch = "licensing_bf_accept")
    public static void furnaceCanBeAccepted(GameTestHelper h) {
        buildFurnace(h);
        BlockPos controller = new BlockPos(2, 4, 5);
        BlockPos pump = new BlockPos(1, 4, 5);
        h.setBlock(pump, PcBlocks.HYDROTEST_PUMP.get().defaultBlockState());
        h.startSequence()
                .thenWaitUntil(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    h.assertTrue(c.unit() != null && c.unit().furnace() != null && ops(h).unit(c.simId()) != null, "furnace formed and registered");
                })
                .thenExecute(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    HydrotestPumpBlockEntity p = (HydrotestPumpBlockEntity) h.getBlockEntity(pump);
                    h.assertTrue(p.target() == c, "the hydrotest pump sees the furnace controller");
                    OpsSubsystem ops = ops(h);
                    SimId id = c.simId();
                    var cs = ops.commissioning();
                    var asm = cs.assembly(id).orElseThrow();
                    cs.unregister(id);
                    cs.register(asm, java.util.List.of(dev.denfry.promptcritical.sim.ops.CommissioningDefect.of(
                            dev.denfry.promptcritical.sim.ops.CommissioningDefectKind.SEAM_WEEP,
                            asm.components().get(dev.denfry.promptcritical.sim.ops.UnitComponent.SHELL), 1.1)), 0);
                    var fake = TestPlayers.of(h);
                    OpsActions.instant(fake, new ItemStack(PcItems.CAULKING_HAMMER.get()), OpsItem.Kind.CAULKING_HAMMER, ops, ops.unit(id), c);
                    // чеканка не убирает дефект, а снижает течь: без неё опрессовка ниже провалилась бы
                    double pw = BlastFurnaceControllerBlockEntity.STAVE_WATER_PA;
                    h.assertTrue(ops.perform(id, new CheckInput.PressureTest(true, 290, ops.config().testPressurePa(pw),
                            ops.config().hydrotestHoldPhysS()), null).passed(), "hydrotest passes after caulking");
                    h.assertTrue(ops.perform(id, new CheckInput.InstrumentCheck(), null).passed(), "instruments");
                    ops.perform(id, new CheckInput.TrialRun(ops.config().trialRunS(), 1, false, true,
                            dev.denfry.promptcritical.sim.ops.Baseline.NONE), null);
                    h.assertTrue(cs.state(id).orElseThrow() == dev.denfry.promptcritical.sim.ops.CommissioningState.ACCEPTED, "furnace accepted");
                })
                .thenSucceed();
    }

    // --- HP2.2: ячейка выключателя, графитовый котёл, печь Ачесона ---

    private static final BlockPos UNIT_POS = new BlockPos(2, 1, 2);

    private static boolean refusalJournaled(OpsSubsystem ops, SimId id) {
        for (OpsLogEntry e : ops.log(id)) if (e.kind() == OpsLogEntry.Kind.START_REFUSED) return true;
        return false;
    }

    /** Включение ячейки I1: без лицензии отказ даже после проверок, с лицензией — включается (план HP2.2). */
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing_breaker")
    public static void breakerCloseNeedsTheI1Licence(GameTestHelper h) {
        h.setBlock(UNIT_POS, PcBlocks.SWITCHGEAR_CELL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> {
                    BreakerCellBlockEntity c = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(c.unit() != null && !c.simId().isNone() && ops(h).unit(c.simId()) != null, "cell and its ops unit");
                })
                .thenExecute(() -> {
                    OpsSubsystem ops = ops(h);
                    BreakerCellBlockEntity c = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(!c.unit().closed(), "a new cell is open");
                    ops.setLicensing(true);
                    try {
                        var fake = TestPlayers.of(h);
                        UUID team = UUID.randomUUID(); // мир GameTest сохраняется: лицензии прошлых запусков не мешают
                        SimId id = c.simId();
                        h.assertTrue(ops.perform(id, new CheckInput.InsulationTest(c.opsStopped()), fake.getUUID(), team).passed(), "insulation");
                        h.assertTrue(ops.perform(id, new CheckInput.SisProofTest(c.opsStopped()), fake.getUUID(), team).passed(), "trip test");
                        h.assertTrue(c.operate(fake, true) != BreakerResponse.ACCEPTED, "close refused without the I1 licence");
                        h.assertTrue(!c.unit().closed(), "cell stays open");
                        h.assertValueEqual(ops.canStart(id).unlicensedEra(), PlantEra.I1, "missing licence named");
                        h.assertTrue(refusalJournaled(ops, id), "refusal journaled");
                        ops.licences().grant(team, PlantEra.I1, 0);
                        c.unit().setPermitted(OpsActions.processAllowed(h.getLevel(), c)); // как на следующем тике
                        h.assertValueEqual(c.operate(fake, true), BreakerResponse.ACCEPTED, "close with the I1 licence");
                    } finally {
                        ops.setLicensing(false);
                    }
                })
                .thenSucceed();
    }

    /** Без профиля licensing (ADVISORY) непринятая ячейка включается, как до HP2.2. */
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing_breaker_advisory")
    public static void advisoryBreakerStillCloses(GameTestHelper h) {
        h.setBlock(UNIT_POS, PcBlocks.SWITCHGEAR_CELL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> {
                    BreakerCellBlockEntity c = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(c.unit() != null && ops(h).unit(c.simId()) != null, "cell and its ops unit");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    BreakerCellBlockEntity c = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(!ops(h).licensing(), "licensing off");
                    h.assertValueEqual(c.operate(null, true), BreakerResponse.ACCEPTED, "unaccepted cell closes in ADVISORY");
                })
                .thenSucceed();
    }

    /** Графитовый котёл I1 без лицензии: модель не поднимает стержни, кнопка «вверх» с CC — отказ в журнал. */
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing_pile")
    public static void pileRodsStayInWithoutTheI1Licence(GameTestHelper h) {
        h.setBlock(UNIT_POS, PcBlocks.REACTOR_CONTROLLER.get().defaultBlockState());
        h.startSequence()
                .thenWaitUntil(() -> {
                    PileControllerBlockEntity c = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(c.unit() != null && ops(h).unit(c.simId()) != null, "pile desk and its ops unit");
                    h.assertTrue(c.unit().pile().permitted(), "permitted without licensing");
                })
                .thenExecute(() -> ops(h).setLicensing(true))
                .thenExecuteAfter(2, () -> {
                    OpsSubsystem ops = ops(h);
                    try {
                        PileControllerBlockEntity c = h.getBlockEntity(UNIT_POS);
                        h.assertTrue(!c.unit().pile().permitted(), "unaccepted pile is not permitted under licensing");
                        c.onPanelButton(TestPlayers.of(h), ReactorPanels.BTN_UP);
                        h.assertValueEqual(c.unit().pile().controlDrive(), 0, "rods not driven up");
                        h.assertTrue(refusalJournaled(ops, c.simId()), "refusal journaled");
                    } finally {
                        ops.setLicensing(false);
                    }
                })
                .thenSucceed();
    }

    /** Печь Ачесона I1 без лицензии: блокировка отключает рубильник и не даёт включить его снова. */
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing_acheson")
    public static void achesonSwitchedOffWithoutTheI1Licence(GameTestHelper h) {
        h.setBlock(UNIT_POS, PcBlocks.ACHESON_FURNACE.get().defaultBlockState());
        h.startSequence()
                .thenWaitUntil(() -> {
                    AchesonFurnaceBlockEntity f = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(f.unit() != null && ops(h).unit(f.simId()) != null, "furnace and its ops unit");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    AchesonFurnaceBlockEntity f = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(f.unit().switchedOn(), "ADVISORY keeps the switch on");
                    ops(h).setLicensing(true);
                })
                .thenExecuteAfter(2, () -> {
                    OpsSubsystem ops = ops(h);
                    try {
                        AchesonFurnaceBlockEntity f = h.getBlockEntity(UNIT_POS);
                        h.assertTrue(!f.unit().switchedOn(), "interlock opened the switch");
                        f.onPanelButton(TestPlayers.of(h), ReactorPanels.BTN_SWITCH);
                        h.assertTrue(!f.unit().switchedOn(), "switch-on refused");
                        h.assertTrue(refusalJournaled(ops, f.simId()), "refusal journaled");
                    } finally {
                        ops.setLicensing(false);
                    }
                })
                .thenSucceed();
    }

    // --- HP2.3: установки без события пуска ---

    /**
     * Нагревательная печь I1 (план HP2.3): ставится на учёт пусконаладки с первого тика; без профиля licensing процесс
     * разрешён, в профиле непринятая печь — нет, а с лицензией I1 после пневмоиспытания и КИП — разрешена на пробу.
     */
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing_reheat")
    public static void reheatingFurnacePermitFollowsTheLicence(GameTestHelper h) {
        h.setBlock(UNIT_POS, PcBlocks.REHEATING_FURNACE.get().defaultBlockState());
        h.startSequence()
                .thenWaitUntil(() -> {
                    dev.denfry.promptcritical.mod.steelworks.ReheatingFurnaceBlockEntity f = h.getBlockEntity(UNIT_POS);
                    h.assertTrue(f.unit() != null && ops(h).unit(f.simId()) != null, "furnace and its ops unit");
                })
                .thenExecute(() -> {
                    OpsSubsystem ops = ops(h);
                    dev.denfry.promptcritical.mod.steelworks.ReheatingFurnaceBlockEntity f = h.getBlockEntity(UNIT_POS);
                    SimId id = f.simId();
                    h.assertValueEqual(ops.unit(id).kind(), UnitKind.REHEATING_FURNACE, "registered as a reheating furnace");
                    h.assertTrue(OpsActions.processAllowed(h.getLevel(), f), "ADVISORY permits the process");
                    ops.setLicensing(true);
                    try {
                        h.assertTrue(!OpsActions.processAllowed(h.getLevel(), f), "unaccepted furnace not permitted under licensing");
                        var fake = TestPlayers.of(h);
                        UUID team = UUID.randomUUID();
                        h.assertTrue(ops.perform(id, new CheckInput.InertLeakTest(f.opsStopped()), fake.getUUID(), team).passed(), "leak test");
                        h.assertTrue(ops.perform(id, new CheckInput.InstrumentCheck(), fake.getUUID(), team).passed(), "instruments");
                        h.assertTrue(!OpsActions.processAllowed(h.getLevel(), f), "still no I1 licence");
                        ops.licences().grant(team, PlantEra.I1, 0);
                        h.assertTrue(OpsActions.processAllowed(h.getLevel(), f), "trial permitted with the I1 licence");
                    } finally {
                        ops.setLicensing(false);
                    }
                })
                .thenSucceed();
    }
}
