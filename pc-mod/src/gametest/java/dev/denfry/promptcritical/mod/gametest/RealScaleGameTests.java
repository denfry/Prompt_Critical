package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelMath;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SitePlans;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Машины реального масштаба (план real-scale pilot): раскладка объёма на сегменты, поворот цельной модели,
 * стройплощадка — отказ на занятом месте и возведение компенсатора давления по этапам.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private RealScaleGameTests() {}

    /** Объём 5×20×5 раскладывается на сегменты без пропусков и наложений, контроллер — сегмент 0. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void tiledFootprintCoversTheVolumeOnce(GameTestHelper h) {
        Footprint fp = Footprint.tiled(-2, 2, 0, 19, -2, 2);
        h.assertTrue(fp.segments().getFirst().ax() == 0 && fp.segments().getFirst().ay() == 0 && fp.segments().getFirst().az() == 0,
                "segment 0 is anchored at the controller");
        Set<BlockPos> seen = new HashSet<>();
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, Direction.NORTH)) h.assertTrue(seen.add(c.pos()), "cell twice: " + c.pos());
        h.assertTrue(seen.size() == 5 * 20 * 5, "volume covered, got " + seen.size());
        double[] r = WholeModelMath.rotate(new double[] {3, 0, 1}, Direction.EAST);
        h.assertTrue(Math.abs(r[0] - 0) < 1e-9 && Math.abs(r[2] - 3) < 1e-9, "east rotation of a model point");
        h.succeed();
    }

    private static ConstructionSiteBlockEntity site(GameTestHelper h, BlockPos p) {
        return (ConstructionSiteBlockEntity) h.getBlockEntity(p);
    }

    /** Площадка с основанием: каменная плита 5×5 под фундаментом. */
    private static void placeSite(GameTestHelper h, BlockPos p) {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Площадка не принимает проект, если в объёме машины стоит чужой блок, и называет его. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void siteRefusesAnObstructedFootprint(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        placeSite(h, p);
        h.setBlock(p.above(5), Blocks.STONE);
        var why = site(h, p).accept("vver_pressurizer");
        h.assertTrue(why != null, "obstructed site refused");
        h.assertTrue(site(h, p).plan() == null, "no plan accepted");
        h.setBlock(p.above(5), Blocks.AIR);
        var w = site(h, p).accept("vver_pressurizer");
        h.assertTrue(w == null, "free site accepted: " + (w == null ? "" : w.getString()));
        h.succeed();
    }

    /** Площадка ждёт материалы, по этапам возводит компенсатор давления и уступает место готовой машине. */
    @GameTest(template = TALL, timeoutTicks = 1400)
    public static void siteErectsTheUnitAfterAllStages(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        placeSite(h, p);
        h.startSequence()
                .thenExecute(() -> {
                    var w = site(h, p).accept("vver_pressurizer");
                    h.assertTrue(w == null, "plan accepted: " + (w == null ? "" : w.getString()));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(site(h, p).stage() == 0 && site(h, p).erectLeft() == 0, "waits for materials");
                    SitePlans.Plan plan = site(h, p).plan();
                    for (SitePlans.Stage st : plan.stages())
                        for (SitePlans.Need n : st.needs()) {
                            ItemStack s = new ItemStack(n.item().get(), n.count());
                            for (int i = 0; i < ConstructionSiteBlockEntity.SLOTS && !s.isEmpty(); i++) s = site(h, p).items().insertItem(i, s, false);
                        }
                })
                .thenWaitUntil(() -> h.assertBlockPresent(PcBlocks.VVER_PRESSURIZER.get(), p))
                .thenExecute(() -> h.assertBlockPresent(PcBlocks.VVER_PRESSURIZER.get(), p.above(3)))
                .thenSucceed();
    }

    /**
     * Кипящий реактор BWR-240 реального масштаба: площадка принимает его чертёж на свободном основании 5×5, а готовый
     * реактор — блок того же типа BE, что и старая серия, со своей цельной моделью; старый BWR по-прежнему работает.
     */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void bwr240SiteAcceptsAndLegacyBwrStillWorks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        placeSite(h, p);
        var w = site(h, p).accept("bwr240_reactor");
        h.assertTrue(w == null, "BWR-240 plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(site(h, p).plan().stages().size() == 4, "four erection stages");
        h.assertTrue(PcBlocks.FP_BWR240_REACTOR.cells(BlockPos.ZERO, Direction.NORTH).size() == 5 * 14 * 5, "5x14x5 footprint");
        h.assertTrue(PcBlocks.isWholeModel(PcBlocks.BWR240_REACTOR.get()) && PcBlocks.legacy().contains(PcBlocks.BWR_REACTOR),
                "BWR-240 is drawn whole, the old BWR is legacy");
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.BWR_REACTOR.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity b && b.wholeModelId() == null,
                "legacy BWR keeps its block entity and no whole model");
        h.succeed();
    }

    /**
     * CANDU 6 реального масштаба: у площадки свой проект в четыре этапа, сборка — блок того же типа BE, что и
     * старая серия; машина перегрузки на модели доходит до узла решётки 380 каналов и возвращается на стоянку.
     */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void candu6PlanAndFuellingMachineOnTheRealLattice(GameTestHelper h) {
        SitePlans.Plan plan = SitePlans.get("candu6_reactor");
        h.assertTrue(plan != null && plan.stages().size() == 4 && plan.unit().get() == PcBlocks.CANDU6_REACTOR.get(), "CANDU 6 site plan");
        h.assertTrue(PcBlocks.FP_CANDU6_REACTOR.cells(BlockPos.ZERO, Direction.NORTH).size() == 9 * 13 * 15, "9x13x15 footprint");
        h.assertTrue(PcBlocks.isWholeModel(PcBlocks.CANDU6_REACTOR.get()) && PcBlocks.legacy().contains(PcBlocks.CANDU_REACTOR),
                "CANDU 6 is drawn whole, the old CANDU is legacy");
        double[] home = dev.denfry.promptcritical.mod.npp.CanduFuelling.offsetReal(-1, 0);
        double[] at = dev.denfry.promptcritical.mod.npp.CanduFuelling.offsetReal(0, dev.denfry.promptcritical.mod.npp.CanduFuelling.TRAVEL + 40);
        double r = Math.hypot(at[0], at[1]) / dev.denfry.promptcritical.mod.npp.CanduFuelling.REAL_PITCH;
        h.assertTrue(home[0] == dev.denfry.promptcritical.mod.npp.CanduFuelling.REAL_HOME_X && at[2] > 0 && r < 11,
                "fuelling machine latched on a channel inside the 380 lattice, r = " + r);
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.CANDU_REACTOR.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity b && b.wholeModelId() == null,
                "legacy CANDU keeps its block entity and no whole model");
        h.succeed();
    }

    /** БН-600 реального масштаба: свой проект стройки, объём 15×19×15, старый натриевый реактор — в старой серии и работает. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void bn600PlanAndLegacySfrStillWorks(GameTestHelper h) {
        SitePlans.Plan plan = SitePlans.get("bn600_reactor");
        h.assertTrue(plan != null && plan.stages().size() == 4 && plan.unit().get() == PcBlocks.BN600_REACTOR.get(), "BN-600 site plan");
        h.assertTrue(PcBlocks.FP_BN600_REACTOR.cells(BlockPos.ZERO, Direction.NORTH).size() == 15 * 19 * 15, "15x19x15 footprint");
        h.assertTrue(PcBlocks.isWholeModel(PcBlocks.BN600_REACTOR.get()) && PcBlocks.legacy().contains(PcBlocks.SFR_REACTOR),
                "BN-600 is drawn whole, the old SFR is legacy");
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.SFR_REACTOR.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.npp.SfrReactorBlockEntity b && b.wholeModelId() == null,
                "legacy SFR keeps its block entity and no whole model");
        h.succeed();
    }

    /** Предмет старой серии по-прежнему ставится и работает: компенсатор давления — блок того же типа BE. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 40)
    public static void legacyPressurizerStillWorks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        h.setBlock(p, PcBlocks.PRESSURIZER.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity, "legacy pressurizer has its block entity");
        h.assertTrue(PcItems.STEEL_PLATE.get() != null, "items available");
        h.succeed();
    }
}
