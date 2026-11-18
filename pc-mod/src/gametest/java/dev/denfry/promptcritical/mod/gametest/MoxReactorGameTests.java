package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.npp.BwrPanels;
import dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.CanduPanels;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.NppPanels;
import dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.PwrPanels;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.CanduReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * МОКС в тепловых реакторах: ТВС BWR (T2b), канал АМ-1 (T1) и пучки CANDU (T3) с реакторным плутонием (7 % Pu в ТМ,
 * у CANDU — 1,5 %) встают в заглушенную зону, плутоний идёт в неё по изотопам и возвращается с отработанной сборкой;
 * сборка с долей Pu выше 12 % не принимается. ВВЭР (T2a) — та же проверка: плутоний больше не пересчитывается в
 * эквивалентный U-235.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoxReactorGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos REACTOR = new BlockPos(6, 3, 6);
    /** Реакторный Pu выгрузки ЛВР: Pu-239, 240, 241, 242 (Pu-238 2,5 % — недостача до единицы; OECD/NEA, 1995). */
    private static final double F239 = 0.545, F240 = 0.238, F241 = 0.115, F242 = 0.077;

    private MoxReactorGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Свежая МОКС-сборка: {@code hmKg} тяжёлого металла, доля Pu {@code puFraction}, уран — отвал 0,25 %. */
    static FuelLot mox(FuelDesign d, double hmKg, double puFraction) {
        double pu = hmKg * puFraction;
        return new FuelLot(hmKg - pu, 0.0025, 0, 0.96, d.rods, d.name(), "", 0, 0, pu, F239, F240, F241, F242);
    }

    private static ItemStack stack(FuelLot l, int n) {
        return l.on(new ItemStack(PcItems.FUEL_ASSEMBLY.get(), n));
    }

    private static void near(GameTestHelper h, double expected, double actual, String what) {
        h.assertTrue(Math.abs(expected - actual) <= 1e-6 * Math.max(1, Math.abs(expected)), what + ": " + actual + " vs " + expected);
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void bwrLoadsMoxAssembly(GameTestHelper h) {
        FuelDesign d = FuelDesign.VVER_1000;
        FuelLot lot = mox(d, d.uKgPerAssembly(0.96, 0.04), 0.07);
        place(h, REACTOR, PcBlocks.BWR_REACTOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    BwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.fuelSlots().isItemValid(BwrPanels.SLOT_FRESH, stack(lot, 1)), "MOX assembly accepted");
                    h.assertFalse(r.fuelSlots().isItemValid(BwrPanels.SLOT_FRESH, stack(mox(d, lot.heavyMetalKg(), 0.2), 1)),
                            "20 % Pu refused in a thermal reactor");
                    r.fuelSlots().setStackInSlot(BwrPanels.SLOT_FRESH, stack(lot, 1));
                })
                .thenWaitUntil(() -> {
                    BwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().core().loadedAssemblies() == 1, "loaded: " + r.reactor().core().loadedAssemblies());
                    near(h, lot.pu(), r.reactor().core().fuel(0).plutoniumKg(), "plutonium in the core");
                    near(h, lot.pu() * F241, r.reactor().core().fuel(0).pu241Kg(), "Pu-241 kept as Pu-241");
                })
                .thenExecute(() -> ((BwrReactorBlockEntity) h.getBlockEntity(REACTOR))
                        .onPanelButton(FakePlayerFactory.getMinecraft(h.getLevel()), BwrPanels.B_UNLOAD))
                .thenWaitUntil(() -> {
                    ItemStack spent = ((BwrReactorBlockEntity) h.getBlockEntity(REACTOR)).fuelSlots().getStackInSlot(BwrPanels.SLOT_SPENT);
                    h.assertTrue(spent.is(PcItems.FUEL_ASSEMBLY.get()), "unloaded: " + spent);
                    FuelLot out = FuelLot.of(spent);
                    near(h, lot.pu(), out.pu(), "plutonium back out");
                    near(h, lot.uKg(), out.uKg(), "uranium back out");
                    near(h, F241, out.pu241(), "Pu-241 share");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void am1ChannelTakesMox(GameTestHelper h) {
        FuelDesign d = FuelDesign.AM_1;
        FuelLot lot = mox(d, d.uKgPerAssembly(0.955, 0.05), 0.07);
        place(h, REACTOR, PcBlocks.POWER_REACTOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertFalse(r.fuelSlots().isItemValid(NppPanels.SLOT_FRESH, stack(mox(d, lot.heavyMetalKg(), 0.2), 1)),
                            "20 % Pu refused");
                    r.fuelSlots().setStackInSlot(NppPanels.SLOT_FRESH, stack(lot, 1));
                })
                .thenWaitUntil(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().loadedChannels() == 1, "one channel: " + r.reactor().loadedChannels());
                    near(h, lot.pu(), r.reactor().fuel(0).plutoniumKg(), "plutonium in the channel");
                    near(h, lot.heavyMetalKg(), r.reactor().fuel(0).heavyMetalKg(), "heavy metal in the channel");
                })
                .thenExecute(() -> ((PowerReactorBlockEntity) h.getBlockEntity(REACTOR))
                        .onPanelButton(FakePlayerFactory.getMinecraft(h.getLevel()), NppPanels.B_UNLOAD))
                .thenWaitUntil(() -> {
                    ItemStack spent = ((PowerReactorBlockEntity) h.getBlockEntity(REACTOR)).fuelSlots().getStackInSlot(NppPanels.SLOT_SPENT);
                    h.assertTrue(spent.is(PcItems.FUEL_ASSEMBLY.get()) && FuelLot.of(spent).designOrNull() == d, "unloaded: " + spent);
                    near(h, lot.pu(), FuelLot.of(spent).pu(), "plutonium back out");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void canduChannelTakesMoxBundles(GameTestHelper h) {
        FuelDesign d = FuelDesign.CANDU_37;
        FuelLot bundle = mox(d, d.uKgPerAssembly(0.96, 0.00711), 0.015);
        place(h, REACTOR, PcBlocks.CANDU_REACTOR.get());
        GameTestSequence seq = h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> h.assertTrue(((CanduReactorBlockEntity) h.getBlockEntity(REACTOR)).panelItems()
                        .isItemValid(CanduPanels.SLOT_FRESH, stack(bundle, 1)), "MOX bundle accepted"));
        // пучки — по одному в слот, машина забирает каждый в магазин
        for (int i = 1; i <= CanduReactor.BUNDLES_PER_CHANNEL; i++) {
            int n = i;
            seq.thenExecute(() -> ((CanduReactorBlockEntity) h.getBlockEntity(REACTOR)).panelItems()
                            .setStackInSlot(CanduPanels.SLOT_FRESH, stack(bundle, 1)))
                    .thenWaitUntil(() -> {
                        CanduReactorBlockEntity r = h.getBlockEntity(REACTOR);
                        h.assertTrue(r.magazine() == n, "magazine: " + r.magazine());
                    });
        }
        seq.thenExecute(() -> ((CanduReactorBlockEntity) h.getBlockEntity(REACTOR))
                        .onPanelButton(FakePlayerFactory.getMinecraft(h.getLevel()), CanduPanels.B_REFUEL))
                .thenWaitUntil(() -> {
                    CanduReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().core().loaded(0), "channel 1 loaded");
                    near(h, CanduReactor.BUNDLES_PER_CHANNEL * bundle.pu(), r.reactor().core().fuel(0).plutoniumKg(), "plutonium in the channel");
                    h.assertTrue(r.magazine() == 0, "magazine spent: " + r.magazine());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void vverKeepsPlutoniumAsPlutonium(GameTestHelper h) {
        FuelDesign d = FuelDesign.VVER_440;
        FuelLot lot = mox(d, d.uKgPerAssembly(0.96, 0.04), 0.07);
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR)).fuelSlots().setStackInSlot(PwrPanels.SLOT_FRESH, stack(lot, 1)))
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().loadedAssemblies() == 1, "loaded: " + r.reactor().loadedAssemblies());
                    near(h, lot.pu(), r.reactor().fuel(0).plutoniumKg(), "plutonium in the vessel");
                    near(h, lot.uKg() * 0.0025, r.reactor().fuel(0).u235Kg(), "U-235 is only the tails uranium's");
                })
                .thenSucceed();
    }
}
