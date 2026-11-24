package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.npp.Bor60Panels;
import dev.denfry.promptcritical.mod.npp.IhxBlockEntity;
import dev.denfry.promptcritical.mod.npp.SfrPanels;
import dev.denfry.promptcritical.mod.npp.SfrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.SteamGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.SodiumFastReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * БОР-60 реального масштаба: корпус берёт конструкцию {@link SodiumFastReactor#BOR60} и пульт {@link Bor60Panels#BOARD},
 * находит ПТО своих петель (их насосы второго контура питаются от щита площадки) и аргоновую подушку показывает на
 * пульте; учения «течь трубки ПГ» пускают воду в натрий — реакция натрий–вода, сброс защиты не проходит. Тепловой
 * баланс, аргон и разрыв мембраны — {@code Bor60Test} в pc-sim.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Bor60GameTests {
    private static final String TALL = "gametest/empty_16x24x16";
    private static final BlockPos REACTOR = new BlockPos(8, 1, 8), IHX = new BlockPos(2, 1, 2), SG = new BlockPos(13, 1, 13);

    private Bor60GameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static SfrReactorBlockEntity bor(GameTestHelper h) {
        return h.getBlockEntity(REACTOR);
    }

    /** Свежая МОКС-сборка быстрого реактора: 25 % Pu в тяжёлом металле. */
    private static ItemStack mox() {
        FuelLot l = MoxReactorGameTests.mox(FuelDesign.VVER_1000, 500, 0.25);
        return l.on(new ItemStack(PcItems.FUEL_ASSEMBLY.get()));
    }

    private static void loadCore(GameTestHelper h) {
        h.runAfterDelay(1, () -> h.assertTrue(bor(h).items(null).insertItem(SfrPanels.SLOT_FRESH, mox(), false).isEmpty(),
                "MOX assembly taken"));
    }

    @GameTest(template = TALL, timeoutTicks = 300)
    public static void bor60TakesItsDesignBoardAndIntermediateLoop(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.BOR60_REACTOR_REAL.get());
        place(h, IHX, PcBlocks.BOR60_IHX_REAL.get());
        loadCore(h);
        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    SfrReactorBlockEntity be = bor(h);
                    h.assertTrue(be.bor60() && be.wholeModelId() != null, "BOR-60 whole model");
                    h.assertTrue(be.panelLayout() == Bor60Panels.BOARD, "BOR-60 board");
                    SodiumFastReactor r = be.reactor();
                    h.assertTrue(r != null && r.design() == SodiumFastReactor.BOR60, "BOR-60 design");
                    h.assertTrue(h.<IhxBlockEntity>getBlockEntity(IHX).pumping(), "secondary sodium pump powered");
                    int[] v = new int[Bor60Panels.CHANNELS];
                    be.readPanel(v);
                    h.assertTrue(v[Bor60Panels.IHX_PUMPS] == 1, "one IHX pump on the board, got " + v[Bor60Panels.IHX_PUMPS]);
                    h.assertTrue(v[Bor60Panels.ARGON_KPA] > 50 && v[Bor60Panels.ARGON_KPA] < 300, "argon cushion " + v[Bor60Panels.ARGON_KPA] + " kPa");
                })
                .thenSucceed();
    }

    @GameTest(template = TALL, timeoutTicks = 400)
    public static void bor60SteamGeneratorTubeLeakDrillStartsTheSodiumWaterReaction(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.BOR60_REACTOR_REAL.get());
        place(h, SG, PcBlocks.BOR60_STEAM_GENERATOR_REAL.get());
        loadCore(h);
        h.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    SodiumFastReactor r = bor(h).reactor();
                    h.assertTrue(r != null, "core loaded");
                    h.assertTrue(r.resetTrip(), "cold reactor resets before the drill");
                    h.<SteamGeneratorBlockEntity>getBlockEntity(SG).setTubeLeakDrill(true);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    SodiumFastReactor r = bor(h).reactor();
                    h.assertTrue(r.sodiumWaterW() > 0, "water in the sodium");
                    h.assertTrue(r.trip() != SodiumFastReactor.Trip.NONE, "protection tripped, got " + r.trip());
                    h.assertFalse(r.resetTrip(), "protection cannot be reset while the tube leaks");
                    int[] v = new int[Bor60Panels.CHANNELS];
                    bor(h).readPanel(v);
                    h.assertTrue((v[SfrPanels.FLAGS] & 1 << Bor60Panels.F_NA_LEAK) != 0, "Na leak lamp lit");
                })
                .thenSucceed();
    }

    /** Пуск с пульта БОР-60 только кнопками: взвод АЗ, извлечение регулирующих стержней — мощность растёт. */
    @GameTest(template = TALL, timeoutTicks = 400)
    public static void bor60StartsFromItsBoardButtons(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.BOR60_REACTOR_REAL.get());
        loadCore(h);
        double[] before = new double[2];
        h.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    SfrReactorBlockEntity be = bor(h);
                    for (int id : new int[] {SfrPanels.B_RESET, SfrPanels.B_UP, SfrPanels.B_STOP, SfrPanels.B_DOWN})
                        h.assertTrue(be.panelLayout().elements.stream().anyMatch(e -> e instanceof dev.denfry.promptcritical.mod.panel.PanelLayout.Button b && b.id() == id), "BOR-60 board has button " + id);
                    var op = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
                    SodiumFastReactor r = be.reactor();
                    be.onPanelButton(op, SfrPanels.B_RESET);
                    h.assertTrue(r.trip() == SodiumFastReactor.Trip.NONE, "scram reset from the board, got " + r.trip());
                    before[0] = r.power();
                    before[1] = r.controlPosition();
                    be.onPanelButton(op, SfrPanels.B_UP);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    var op = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
                    bor(h).onPanelButton(op, SfrPanels.B_STOP);
                    SodiumFastReactor r = bor(h).reactor();
                    h.assertTrue(r.controlPosition() < before[1], "control rods withdrawn: " + before[1] + " -> " + r.controlPosition());
                    h.assertTrue(r.power() > before[0], "power rising: " + before[0] + " -> " + r.power());
                })
                .thenSucceed();
    }

}
