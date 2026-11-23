package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
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
        UUID team = new UUID(0x1ce, 1);
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
}
