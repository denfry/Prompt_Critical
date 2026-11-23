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
