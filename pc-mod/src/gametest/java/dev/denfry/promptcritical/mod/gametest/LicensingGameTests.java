package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridPanels;
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
                    Player p = FakePlayerFactory.getMinecraft(h.getLevel());
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
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing")
    public static void generatorFieldNeedsTheI1Licence(GameTestHelper h) {
        placeGenerator(h, 0);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(generatorReady(h), "generator and its ops unit"))
                .thenExecute(() -> {
                    OpsSubsystem ops = ops(h);
                    ops.setLicensing(true);
                    try {
                        var fake = FakePlayerFactory.getMinecraft(h.getLevel());
                        UUID team = TeamResolver.team(fake);
                        SimId id = generator(h).simId();
                        h.assertTrue(ops.perform(id, new CheckInput.InsulationTest(true), fake.getUUID(), team).passed(), "insulation");
                        h.assertTrue(ops.perform(id, new CheckInput.Alignment(true), fake.getUUID(), team).passed(), "alignment");
                        generator(h).onPanelButton(fake, GridPanels.BUTTON_FIELD);
                        h.assertTrue(!generator(h).unit().fieldOn(), "field refused without the I1 licence");
                        h.assertValueEqual(ops.canStart(id).unlicensedEra(), PlantEra.I1, "missing licence named");
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
    @GameTest(template = LARGE, timeoutTicks = 200, batch = "licensing")
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
    @GameTest(template = TALL, timeoutTicks = 300, batch = "licensing")
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
}
