package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.waste.LrwEvaporatorBlockEntity;
import dev.denfry.promptcritical.mod.waste.ReactorWaterCleanupBlockEntity;
import dev.denfry.promptcritical.mod.waste.RepositoryBlockEntity;
import dev.denfry.promptcritical.mod.waste.WasteCompactorBlockEntity;
import dev.denfry.promptcritical.mod.waste.WastePackage;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.waste.LrwEvaporator;
import dev.denfry.promptcritical.sim.waste.Repository;
import dev.denfry.promptcritical.sim.waste.ScalingFactors;
import dev.denfry.promptcritical.sim.waste.WasteClassification;
import dev.denfry.promptcritical.sim.waste.WasteForm;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Обращение с РАО в мире: выпарка ЖРО с цементированием кубового остатка в бочки, запрет цемента для высокоактивного
 * куба (только стекло), суперпресс фильтров, критерии приёма пунктов захоронения (класс, медный контейнер) и очистка
 * теплоносителя с выгрузкой отработанного ионита. Долгие часы выпарки ускоряются вызовом модели напрямую, как в
 * {@link PurexGameTests}.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WasteGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos EVAP = new BlockPos(6, 2, 6);

    private WasteGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos p, Block b) {
        var st = b.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        h.setBlock(p, st);
    }

    /** Упарить содержимое сборника за модельные часы (в мире это ~1 л за тик). */
    private static void boilDown(LrwEvaporatorBlockEntity e) {
        for (int i = 0; i < 100 && e.pool().litres() > 1; i++) {
            LrwEvaporator.Step s = e.evaporator().step(3600, e.pool().feed(), e.pool().litres(), 1e9, true);
            e.pool().take(s.feedL());
        }
    }

    private static ItemStack contaminated(GameTestHelper h, net.minecraft.world.item.Item item, double co60Bq, double massKg) {
        ItemStack s = new ItemStack(item);
        s.set(ActivityData.ACTIVITY.get(), ActivityData.of(new ActivityInventory(Map.of(Emitter.CO60, co60Bq), massKg, 1), h.getLevel().getGameTime()));
        return s;
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void evaporatorCementsConcentrateIntoAcceptableDrums(GameTestHelper h) {
        place(h, EVAP, PcBlocks.LRW_EVAPORATOR.get());
        place(h, new BlockPos(4, 2, 6), PcBlocks.CEMENTATION_MIXER.get());
        place(h, new BlockPos(8, 2, 6), PcBlocks.NEAR_SURFACE_REPOSITORY.get());
        h.startSequence()
                .thenIdle(45) // аппараты находятся при обходе раз в 40 тиков
                .thenExecute(() -> {
                    LrwEvaporatorBlockEntity e = h.getBlockEntity(EVAP);
                    e.setBenchPower(true);
                    int n = e.fluids(Direction.NORTH).fill(new FluidStack(PcFluids.LIQUID_RADIOACTIVE_WASTE.source.get(), 16_000),
                            IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(n == 16_000, "LRW accepted: " + n);
                    boilDown(e);
                    h.assertTrue(e.evaporator().ready(), "sump at " + e.evaporator().saltGL() + " g/l");
                    e.input().insertItem(0, new ItemStack(PcItems.PORTLAND_CEMENT.get(), 20), false);
                    e.input().insertItem(1, new ItemStack(PcItems.WASTE_DRUM.get(), 4), false);
                })
                .thenWaitUntil(() -> {
                    LrwEvaporatorBlockEntity e = h.getBlockEntity(EVAP);
                    h.assertTrue(e.drums() >= 1, "cemented drums: " + e.drums());
                })
                .thenExecute(() -> {
                    LrwEvaporatorBlockEntity e = h.getBlockEntity(EVAP);
                    ItemStack d = e.output().getStackInSlot(0);
                    h.assertTrue(d.is(PcItems.CEMENTED_WASTE_DRUM.get()), "product " + d);
                    WastePackage p = WastePackage.of(d);
                    h.assertTrue(p != null && p.compliant() && p.wasteForm() == WasteForm.CEMENT, "cement passport " + p);
                    long now = h.getLevel().getGameTime();
                    var cls = WasteClassification.classify(WastePackage.inventory(d, now), false);
                    h.assertTrue(cls.route() == WasteClassification.Route.NEAR_SURFACE, "drum goes near-surface: " + cls);
                    RepositoryBlockEntity r = h.getBlockEntity(new BlockPos(8, 2, 6));
                    h.assertTrue(r.offer(d, now) == Repository.Verdict.ACCEPTED, "vault accepts the drum: " + r.lastVerdict());
                    // конденсат почти чистый: около 1/DF от упаренного раствора
                    h.assertTrue(e.evaporator().condensateBqPerL() < 1e3, "condensate " + e.evaporator().condensateBqPerL() + " Bq/l");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void highLevelConcentrateGoesOnlyToGlass(GameTestHelper h) {
        place(h, EVAP, PcBlocks.LRW_EVAPORATOR.get());
        place(h, new BlockPos(4, 2, 6), PcBlocks.CEMENTATION_MIXER.get());
        h.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    LrwEvaporatorBlockEntity e = h.getBlockEntity(EVAP);
                    e.setBenchPower(true);
                    // раствор после дезактивации поражённого контура: 10¹¹ Бк/л Cs-137
                    LrwEvaporator.Feed hot = new LrwEvaporator.Feed(5, Map.of(Emitter.CS137, 1e11), 303.15);
                    // четыре часа выпарки: ≈ 66 л куба, на стеклоблок нужно ≈ 33 л
                    for (int i = 0; i < 4; i++) e.evaporator().step(3600, hot, 1000, 1e9, true);
                    e.input().insertItem(0, new ItemStack(PcItems.PORTLAND_CEMENT.get(), 20), false);
                    e.input().insertItem(1, new ItemStack(PcItems.WASTE_DRUM.get(), 4), false);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    LrwEvaporatorBlockEntity e = h.getBlockEntity(EVAP);
                    h.assertTrue(e.category() == WasteClassification.Category.HLW, "concentrate category " + e.category());
                    h.assertTrue(e.drums() == 0, "no cement for HLW: " + e.drums());
                    place(h, new BlockPos(8, 2, 6), PcBlocks.VITRIFICATION_MELTER.get());
                })
                .thenWaitUntil(() -> {
                    LrwEvaporatorBlockEntity e = h.getBlockEntity(EVAP);
                    ItemStack c = e.output().getStackInSlot(0);
                    h.assertTrue(c.is(PcItems.HLW_GLASS_CANISTER.get()), "glass canister " + c);
                    WastePackage p = WastePackage.of(c);
                    h.assertTrue(p != null && p.wasteForm() == WasteForm.PHOSPHATE_GLASS, "phosphate glass " + p);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void supercompactorPressesFiltersIntoAnOverpack(GameTestHelper h) {
        BlockPos at = new BlockPos(6, 2, 6);
        place(h, at, PcBlocks.WASTE_COMPACTOR.get());
        h.startSequence()
                .thenExecute(() -> {
                    WasteCompactorBlockEntity c = h.getBlockEntity(at);
                    c.input().insertItem(0, contaminated(h, PcItems.FILTER_CARTRIDGE.get(), 1e8, 10).copyWithCount(10), false);
                    c.input().insertItem(1, new ItemStack(PcItems.WASTE_DRUM.get(), 3), false);
                })
                .thenWaitUntil(() -> h.assertTrue(h.<WasteCompactorBlockEntity>getBlockEntity(at).pucks() == 1, "one puck pressed"))
                .thenExecute(() -> {
                    WasteCompactorBlockEntity c = h.getBlockEntity(at);
                    // фильтры: Хеккель при 77 МПа даёт сокращение около 3,4
                    h.assertTrue(c.lastVolumeReduction() > 2.5 && c.lastVolumeReduction() < 5, "reduction " + c.lastVolumeReduction());
                    c.close();
                })
                .thenWaitUntil(() -> {
                    WasteCompactorBlockEntity c = h.getBlockEntity(at);
                    ItemStack d = c.output().getStackInSlot(0);
                    h.assertTrue(d.is(PcItems.COMPACTED_WASTE_DRUM.get()), "overpack " + d);
                    ActivityData a = d.get(ActivityData.ACTIVITY.get());
                    double bq = a.inventory(h.getLevel().getGameTime()).bq(Emitter.CO60);
                    h.assertTrue(bq > 0.99e9 && bq <= 1e9 + 1, "ten cartridges of Co-60 in the overpack: " + bq);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void repositoriesApplyAcceptanceCriteria(GameTestHelper h) {
        BlockPos ns = new BlockPos(4, 2, 6), deep = new BlockPos(8, 2, 6);
        place(h, ns, PcBlocks.NEAR_SURFACE_REPOSITORY.get());
        place(h, deep, PcBlocks.DEEP_GEOLOGICAL_REPOSITORY.get());
        h.startSequence()
                .thenExecute(() -> {
                    long now = h.getLevel().getGameTime();
                    RepositoryBlockEntity n = h.getBlockEntity(ns), d = h.getBlockEntity(deep);
                    ItemStack drum = WastePackage.make(PcItems.CEMENTED_WASTE_DRUM.get(),
                            new ActivityInventory(Map.of(Emitter.CS137, 1e9, Emitter.CO60, 1e9), 340, 1.9), now, WasteForm.CEMENT,
                            ScalingFactors.Stream.REACTOR_OPERATIONAL, true, 180);
                    n.slots().insertItem(RepositoryBlockEntity.INTAKE, drum, false);
                    ItemStack glass = new ItemStack(PcItems.HLW_GLASS_CANISTER.get());
                    glass.set(ActivityData.ACTIVITY.get(), ActivityData.of(
                            new ActivityInventory(Map.of(Emitter.CS137, 1e15, Emitter.SR90, 1e15), 50, 2.75), now));
                    h.assertTrue(n.offer(glass, now) == Repository.Verdict.WRONG_CLASS, "glass is not for near-surface: " + n.lastVerdict());
                    h.assertTrue(d.offer(glass, now) == Repository.Verdict.NEEDS_OVERPACK, "glass needs copper: " + d.lastVerdict());
                    d.slots().insertItem(RepositoryBlockEntity.OVERPACK, new ItemStack(PcItems.COPPER_CANISTER.get(), 2), false);
                    h.assertTrue(d.offer(glass, now) == Repository.Verdict.ACCEPTED, "glass in copper accepted: " + d.lastVerdict());
                    h.assertTrue(d.slots().getStackInSlot(RepositoryBlockEntity.OVERPACK).getCount() == 1, "one copper canister used");
                })
                .thenWaitUntil(() -> h.assertTrue(h.<RepositoryBlockEntity>getBlockEntity(ns).repository().packages() == 1, "drum taken from the intake"))
                .thenWaitUntil(() -> {
                    RepositoryBlockEntity n = h.getBlockEntity(ns);
                    h.assertTrue(n.dilutionM3Y() > 0, "river dilution known");
                    Repository.Peak p = n.peak();
                    h.assertTrue(p.svPerY() > 0 && p.svPerY() < Repository.DOSE_CONSTRAINT_SV_Y, "peak dose " + p);
                    h.assertTrue(h.<RepositoryBlockEntity>getBlockEntity(deep).repository().heatW() > 50, "glass heat in the deep drift");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void cleanupLoadsResinAndSluicesItOut(GameTestHelper h) {
        BlockPos at = new BlockPos(6, 2, 6);
        place(h, at, PcBlocks.REACTOR_WATER_CLEANUP.get());
        h.startSequence()
                .thenExecute(() -> {
                    ReactorWaterCleanupBlockEntity c = h.getBlockEntity(at);
                    c.fresh().insertItem(ReactorWaterCleanupBlockEntity.RESIN_SLOT, new ItemStack(PcItems.ION_EXCHANGE_RESIN_SBA.get(), 2), false);
                    c.fresh().insertItem(ReactorWaterCleanupBlockEntity.FILTER_SLOT, new ItemStack(PcItems.FILTER_CARTRIDGE.get(), 2), false);
                })
                .thenIdle(25) // заправка гнёзд — раз в 20 тиков
                .thenExecute(() -> {
                    ReactorWaterCleanupBlockEntity c = h.getBlockEntity(at);
                    // 100 МВт тепловых, одна негерметичная ТВС, 0,1 года
                    c.capture(100e6, 1, 0.1 * 365.25 * 86_400);
                    h.assertTrue(c.resinBed().totalBq() > 5e10, "resin bed " + c.resinBed().totalBq());
                    h.assertTrue(c.lrw().amount() > 0, "drain water to LRW");
                    h.assertTrue(c.changeResin(), "resin sluiced out");
                    ItemStack spent = c.spent().getStackInSlot(0);
                    h.assertTrue(spent.is(PcItems.SPENT_ION_EXCHANGE_RESIN.get()), "spent resin " + spent);
                    ActivityData a = spent.get(ActivityData.ACTIVITY.get());
                    h.assertTrue(a != null && a.inventory(h.getLevel().getGameTime()).bq(Emitter.CO60) > 1e10, "Co-60 on the resin");
                    h.assertTrue(c.fresh().getStackInSlot(ReactorWaterCleanupBlockEntity.RESIN_SLOT).getCount() == 0, "second bag loaded");
                })
                .thenSucceed();
    }
}
