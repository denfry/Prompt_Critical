package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.api.ApiEvent;
import dev.denfry.promptcritical.api.PromptCriticalApi;
import dev.denfry.promptcritical.api.SimulationAccess;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.api.DeviceStations;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.ironworks.IronworksComponents;
import dev.denfry.promptcritical.mod.reactor.AchesonFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.reactor.FuelChannelBlockEntity;
import dev.denfry.promptcritical.mod.reactor.PileControllerBlockEntity;
import dev.denfry.promptcritical.mod.reactor.PileScanner;
import dev.denfry.promptcritical.mod.reactor.ReactorPanels;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.ElectrodeState;
import dev.denfry.promptcritical.sim.reactor.GraphitePile;
import dev.denfry.promptcritical.sim.reactor.PileLayout;
import dev.denfry.promptcritical.sim.reactor.PileTrip;
import dev.denfry.promptcritical.sim.reactor.PileUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Графитовый котёл T0 в мире (I1): кладка из блоков находится обходом от пульта, блочки грузятся в каналы, стержни
 * и счётчики узнаются по приводам и граням, кнопка в зале сбрасывает защиту, печь Ачесона без напряжения стоит.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReactorGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Кладка 6×6×6: топливо 4³ в метровом графитовом отражателе; угол — (1, 1, 1). */
    private static final int N = 6, X0 = 1, Y0 = 1, Z0 = 1;
    /** Центральная колонна — аварийный стержень, соседняя — регулирующий. */
    private static final int ZIP_X = X0 + 3, CONTROL_X = X0 + 2, ROD_Z = Z0 + 3;
    private static final BlockPos DESK = new BlockPos(X0 - 1, Y0 + 2, Z0 + 2);
    private static final BlockPos COUNTER = new BlockPos(X0 + N, Y0 + 3, Z0 + 3);

    /** Все события API за прогон: подписка одна на сервер, отписки в API нет. */
    private static final List<ApiEvent> API_EVENTS = new CopyOnWriteArrayList<>();
    private static volatile boolean apiSubscribed;

    private ReactorGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    /** Кладка с регулирующим и аварийным стержнями, источником в нижнем слое и счётчиком у грани. */
    private static void buildPile(GameTestHelper h, boolean loadFuel) {
        for (int y = 0; y < N; y++) {
            for (int z = 0; z < N; z++) {
                for (int x = 0; x < N; x++) {
                    BlockPos p = new BlockPos(X0 + x, Y0 + y, Z0 + z);
                    boolean fuel = x >= 1 && x <= 4 && y >= 1 && y <= 4 && z >= 1 && z <= 4;
                    BlockState s;
                    if ((p.getX() == ZIP_X || p.getX() == CONTROL_X) && p.getZ() == ROD_Z) s = PcBlocks.CONTROL_ROD_CHANNEL.get().defaultBlockState();
                    else if (y == 0 && x == 3 && z == 2) s = PcBlocks.STARTUP_SOURCE.get().defaultBlockState();
                    else s = (fuel ? PcBlocks.FUEL_CHANNEL : PcBlocks.MODERATOR_BLOCK_GRAPHITE).get().defaultBlockState();
                    h.setBlock(p, s);
                }
            }
        }
        h.setBlock(new BlockPos(ZIP_X, Y0 + N, ROD_Z), PcBlocks.ZIP_ROD_WINCH.get().defaultBlockState());
        h.setBlock(new BlockPos(CONTROL_X, Y0 + N, ROD_Z), PcBlocks.CONTROL_ROD_DRIVE.get().defaultBlockState());
        h.setBlock(COUNTER, PcBlocks.NEUTRON_DETECTOR.get().defaultBlockState());
        h.setBlock(DESK, PcBlocks.REACTOR_CONTROLLER.get().defaultBlockState());
        if (!loadFuel) return;
        for (int y = 1; y <= 4; y++) {
            for (int z = 1; z <= 4; z++) {
                for (int x = 1; x <= 4; x++) {
                    BlockPos p = new BlockPos(X0 + x, Y0 + y, Z0 + z);
                    if (h.getLevel().getBlockEntity(h.absolutePos(p)) instanceof FuelChannelBlockEntity f) {
                        for (int i = 0; i < PileLayout.FULL_LOAD; i++) {
                            f.items(null).insertItem(i, new ItemStack(PcItems.URANIUM_SLUG.get()), false);
                        }
                    }
                }
            }
        }
    }

    private static PileUnit unit(GameTestHelper h) {
        PileControllerBlockEntity c = (PileControllerBlockEntity) h.getBlockEntity(DESK);
        PileUnit u = c.unit();
        if (u == null) throw new IllegalStateException("pile desk has no unit");
        return u;
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void deskFindsThePileRodsAndCounter(GameTestHelper h) {
        buildPile(h, true);
        h.runAfterDelay(30, () -> {
            binding(h);
            GraphitePile p = unit(h).pile();
            PileLayout l = p.layout();
            h.assertTrue(l.nodeCount() == N * N * N, "all 216 blocks are pile nodes, got " + l.nodeCount());
            h.assertTrue(l.sources() == 1, "one source");
            h.assertTrue(p.zipCount() == 1 && p.controlCount() == 1, "zip and control rods found");
            h.assertTrue(l.detectorCount() == 1, "counter on the face found");
            h.assertTrue(l.totalLoad() == (64 - 8) * PileLayout.FULL_LOAD, "fuel blocks loaded, got " + l.totalLoad());
            h.assertTrue(p.keff() > 0 && p.keff() < 1, "rods in: subcritical, k = " + p.keff());
            h.assertTrue(p.expectedCountRate(0) > 1, "source neutrons reach the counter");
            int[] out = new int[ReactorPanels.P_CHANNELS];
            ((PileControllerBlockEntity) h.getBlockEntity(DESK)).readPanel(out);
            h.assertTrue((out[ReactorPanels.P_FLAGS] & 1 << ReactorPanels.PF_FORMED) != 0, "panel shows a formed pile");
            h.succeed();
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void removingAFuelBlockReformsThePile(GameTestHelper h) {
        buildPile(h, true);
        h.runAfterDelay(30, () -> {
            int before = unit(h).pile().layout().totalLoad();
            h.setBlock(new BlockPos(X0 + 1, Y0 + 1, Z0 + 1), Blocks.AIR.defaultBlockState());
            h.runAfterDelay(30, () -> {
                PileLayout l = unit(h).pile().layout();
                h.assertTrue(l.nodeCount() == N * N * N - 1, "one node fewer");
                h.assertTrue(l.totalLoad() == before - PileLayout.FULL_LOAD, "its slugs are gone from the load");
                h.succeed();
            });
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void scramButtonDropsTheZipRod(GameTestHelper h) {
        buildPile(h, true);
        BlockPos button = new BlockPos(X0 + N + 2, Y0, Z0);
        h.setBlock(button, PcBlocks.SCRAM_BUTTON.get().defaultBlockState());
        h.runAfterDelay(30, () -> {
            GraphitePile p = unit(h).pile();
            p.setRodInsertion(new BlockPos(h.absolutePos(new BlockPos(ZIP_X, Y0 + N, ROD_Z))).asLong(), 0);
            h.assertTrue(p.zipCocked(), "zip rod cocked by hand");
            // редстоун на кнопку — фронт сигнала
            h.setBlock(button.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.runAfterDelay(60, () -> {
                h.assertTrue(p.trip() == PileTrip.MANUAL, "button scram, got " + p.trip());
                h.assertTrue(p.zipInsertion() == 1.0, "zip rod fell in");
                h.assertTrue(!p.windZip(), "no winding before reset");
                h.succeed();
            });
        });
    }

    /**
     * API аддонов (05 §5.8.1): АЗ котла с кнопки приходит подписчику {@link SimulationAccess} событием
     * {@link ApiEvent.Trip} со станцией пульта, и тот же пульт читается через API тегами Lua.
     */
    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void scramReachesTheAddonApiAsATrip(GameTestHelper h) {
        String dim = h.getLevel().dimension().location().toString();
        SimulationAccess api = PromptCriticalApi.get().simulation(dim);
        synchronized (API_EVENTS) {
            if (!apiSubscribed) {
                api.subscribeEvents(API_EVENTS::add);
                apiSubscribed = true;
            }
        }
        buildPile(h, true);
        UUID station = DeviceStations.id(dim, h.absolutePos(DESK));
        BlockPos button = new BlockPos(X0 + N + 2, Y0, Z0);
        h.setBlock(button, PcBlocks.SCRAM_BUTTON.get().defaultBlockState());
        h.runAfterDelay(30, () -> {
            GraphitePile p = unit(h).pile();
            p.setRodInsertion(new BlockPos(h.absolutePos(new BlockPos(ZIP_X, Y0 + N, ROD_Z))).asLong(), 0);
            h.assertTrue(p.zipCocked(), "zip rod cocked by hand");
            h.assertTrue(api.stations().contains(station), "the pile desk is a station of the API");
            String trippedTag = api.tags(station).stream().filter(t -> t.endsWith("tripped")).findFirst().orElse(null);
            h.assertTrue(trippedTag != null, "the desk shows its scram lamp as a tag, got " + api.tags(station));
            h.setBlock(button.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.runAfterDelay(60, () -> {
                h.assertTrue(p.trip() == PileTrip.MANUAL, "button scram, got " + p.trip());
                ApiEvent.Trip trip = API_EVENTS.stream().filter(e -> e instanceof ApiEvent.Trip t && t.tripped() && t.stationId().equals(station))
                        .map(e -> (ApiEvent.Trip) e).findFirst().orElse(null);
                h.assertTrue(trip != null, "a Trip event for the desk's station, got " + API_EVENTS);
                h.assertTrue(trip.dimension().equals(dim), "event dimension " + trip.dimension());
                h.assertTrue(trip.device().startsWith("reactor_controller@"), "device id " + trip.device());
                h.assertTrue(api.read(station, trippedTag).orElse(0.0) > 0.5, "the scram lamp reads lit through the API");
                h.succeed();
            });
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void scannerRefusesOnlyNonPileBlocks(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 1, 2), PcBlocks.MODERATOR_BLOCK_GRAPHITE.get().defaultBlockState());
        h.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        h.setBlock(new BlockPos(4, 1, 2), PcBlocks.MODERATOR_BLOCK_GRAPHITE.get().defaultBlockState());
        h.setBlock(new BlockPos(1, 1, 2), PcBlocks.REACTOR_CONTROLLER.get().defaultBlockState());
        h.runAfterDelay(5, () -> {
            PileScanner.Scan s = PileScanner.scan(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 2)));
            h.assertTrue(!s.truncated(), "small pile is not truncated");
            h.assertTrue(s.layout().nodeCount() == 1, "stone breaks the pile, got " + s.layout().nodeCount());
            h.succeed();
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void fuelChannelTakesOnlyEightSlugCassettes(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 1, 3);
        h.setBlock(at, PcBlocks.FUEL_CHANNEL.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            FuelChannelBlockEntity f = (FuelChannelBlockEntity) h.getBlockEntity(at);
            var items = f.items(null);
            h.assertTrue(!items.insertItem(0, new ItemStack(PcItems.STEEL_INGOT.get()), true).isEmpty(), "steel refused");
            ItemStack rest = new ItemStack(PcItems.URANIUM_SLUG.get(), 12);
            for (int i = 0; i < items.getSlots(); i++) rest = items.insertItem(i, rest, false);
            h.assertTrue(f.load() == PileLayout.FULL_LOAD, "eight cassettes, got " + f.load());
            h.assertTrue(rest.getCount() == 4, "four left over");
            h.succeed();
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void achesonFurnaceNeedsTheGrid(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 1, 5);
        h.setBlock(at, PcBlocks.ACHESON_FURNACE.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            AchesonFurnaceBlockEntity f = (AchesonFurnaceBlockEntity) h.getBlockEntity(at);
            ItemStack baked = new ItemStack(PcItems.GREEN_ELECTRODE.get());
            baked.set(IronworksComponents.ELECTRODE_STATE.get(),
                    IronworksComponents.Electrode.of(new ElectrodeState(1.5, 0.3, false, ElectrodeState.BAKED)));
            ItemStack cracked = baked.copy();
            cracked.set(IronworksComponents.ELECTRODE_STATE.get(),
                    IronworksComponents.Electrode.of(new ElectrodeState(1.5, 0.3, true, ElectrodeState.BAKED)));
            h.assertTrue(AchesonFurnaceBlockEntity.isBaked(baked), "sound baked electrode accepted");
            h.assertTrue(!AchesonFurnaceBlockEntity.isBaked(cracked), "cracked electrode refused");
            h.assertTrue(f.items(null).insertItem(0, baked, false).isEmpty(), "loaded");
        });
        h.runAfterDelay(120, () -> {
            AchesonFurnaceBlockEntity f = (AchesonFurnaceBlockEntity) h.getBlockEntity(at);
            h.assertTrue(f.items(null).getStackInSlot(0).getCount() == 1, "no power, no graphite");
            h.assertTrue(f.items(null).getStackInSlot(1).isEmpty(), "nothing out");
            h.succeed();
        });
    }

    // --- энергия Вигнера (04c §4c.11.1) ---

    private static int[] panel(GameTestHelper h) {
        int[] out = new int[ReactorPanels.P_CHANNELS];
        ((PileControllerBlockEntity) h.getBlockEntity(DESK)).readPanel(out);
        return out;
    }

    /** Малый запас в прогретой кладке под закрытыми заслонками уходит порциями: отжиг засчитан, графит не горит. */
    @GameTest(template = LARGE, timeoutTicks = 3000)
    public static void smallWignerStoreAnnealsInAWarmPile(GameTestHelper h) {
        buildPile(h, false);
        h.runAfterDelay(30, () -> {
            GraphitePile p = unit(h).pile();
            p.wigner().preload(100, 293.15);
            p.setGraphiteTempK(470);
            p.setDampersClosed(true);
            int[] before = panel(h);
            h.assertTrue(before[ReactorPanels.P_WIGNER_JG10] > 900, "desk shows the store, got " + before[ReactorPanels.P_WIGNER_JG10]);
            h.assertTrue((before[ReactorPanels.P_FLAGS] & 1 << ReactorPanels.PF_DAMPERS) != 0, "dampers lamp lit");
            h.succeedWhen(() -> {
                h.assertTrue(p.wignerStoredJg() < 50, "store still " + p.wignerStoredJg() + " J/g at " + p.graphiteTempK() + " K");
                h.assertTrue(!p.graphiteFire(), "graphite caught fire");
                h.assertTrue(p.wigner().peakJg() < 50, "anneal counted (peak reset), peak " + p.wigner().peakJg());
            });
        });
    }

    /** Большой запас: самонагрев на пульте и пожар графита, как на Windscale. */
    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void largeWignerStoreSetsTheGraphiteOnFire(GameTestHelper h) {
        buildPile(h, false);
        boolean[] lamp = {false}, ready = {false};
        h.onEachTick(() -> {
            if (ready[0]) lamp[0] |= (panel(h)[ReactorPanels.P_FLAGS] & 1 << ReactorPanels.PF_WIGNER_RUNAWAY) != 0;
        });
        h.runAfterDelay(30, () -> {
            GraphitePile p = unit(h).pile();
            p.wigner().preload(500, 293.15);
            p.setGraphiteTempK(420);
            ready[0] = true;
            h.succeedWhen(() -> {
                h.assertTrue(lamp[0], "self-heating lamp never lit");
                h.assertTrue(p.graphiteFire(), "no fire yet at " + p.graphiteTempK() + " K");
            });
        });
    }
}
