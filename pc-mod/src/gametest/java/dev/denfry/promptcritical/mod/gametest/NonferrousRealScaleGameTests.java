package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.nonferrous.AluminaPrecipitatorBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.BayerDigesterBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.ConverterBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.ElectrorefiningCellBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.MatteSmelterBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.ReductionPotBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Цветная металлургия реального масштаба (08 §8.8): ванна электрорафинирования, конвертер Пирса–Смита, электролизёр,
 * автоклав, декомпозёр и отражательная печь — цельные модели на тех же BE и юнитах, что старые блоки. Стройплощадка
 * принимает их планы, состояние блока выбирает вариант модели, трубы и воронки цепляются только к нарисованным
 * патрубкам, продукт забирают снизу.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NonferrousRealScaleGameTests {
    private static final String SMALL = "gametest/empty_12x12x12", TALL = "gametest/empty_12x24x12", LONG = "gametest/empty_40x10x10";

    private NonferrousRealScaleGameTests() {}

    /**
     * Ставит машину {@code id} на стройплощадку (план принят, основание x0…x1, z0…z1), затем сам блок фасадом на север;
     * проверяет число клеток объёма и что BE рисует цельную модель {@code id}.
     */
    private static BlockEntity place(GameTestHelper h, BlockPos p, String id, Block block, Footprint fp, int cells,
            int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(fp.cells(BlockPos.ZERO, Direction.NORTH).size() == cells, id + " has " + cells + " cells");
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockEntity be = h.getBlockEntity(p);
        h.assertTrue(be instanceof WholeModelHost m && id.equals(m.wholeModelId()), id + " draws its whole model");
        return be;
    }

    /** Модель при свойстве {@code prop} = true (состояние BE подменяется только для проверки). */
    private static String modelWith(BlockEntity be, BooleanProperty prop) {
        BlockState was = be.getBlockState();
        be.setBlockState(was.setValue(prop, true));
        String id = ((WholeModelHost) be).wholeModelId();
        be.setBlockState(was);
        return id;
    }

    private static boolean items(GameTestHelper h, BlockPos p, int x, int y, int z, Direction side) {
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(x, y, z)), side) != null;
    }

    private static boolean fluids(GameTestHelper h, BlockPos p, int x, int y, int z, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p.offset(x, y, z)), side) != null;
    }

    /** Ванна: 5 × 2 × 2, никелевая серия — своя модель; аноды сверху, катоды снизу, глухой торец закрыт; старый блок — без. */
    @GameTest(template = SMALL, timeoutTicks = 40)
    public static void realRefiningCellLoadsFromAboveAndSwitchesToTheNickelModel(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        BlockEntity be = place(h, p, "electrorefining_cell_real", PcBlocks.ELECTROREFINING_CELL_REAL.get(),
                PcBlocks.FP_ELECTROREFINING_CELL_REAL, 5 * 2 * 2, -2, 2, 0, 1);
        h.assertTrue(be instanceof ElectrorefiningCellBlockEntity, "the old refining block entity");
        h.assertTrue("electrorefining_cell_real_ni".equals(modelWith(be, PcProperties.NICKEL)), "nickel series draws the _ni model");
        h.assertTrue(items(h, p, 0, 1, 1, Direction.UP), "anodes go in from above");
        h.assertTrue(!items(h, p, -2, 0, 0, Direction.WEST), "the tank end is closed");
        BlockPos old = new BlockPos(9, 1, 9);
        h.setBlock(old, PcBlocks.ELECTROREFINING_CELL.get().defaultBlockState());
        h.assertTrue(((WholeModelHost) h.getBlockEntity(old)).wholeModelId() == null, "the old cell keeps its block model");
        h.succeed();
    }

    /** Конвертер: 9 × 5 × 5, дутьё — модель _running; штейн в горловину, воздух — во фланец коллектора, бочка глухая. */
    @GameTest(template = SMALL, timeoutTicks = 40)
    public static void realConverterTakesMatteAtTheMouthAndBlastAtTheHeader(GameTestHelper h) {
        BlockPos p = new BlockPos(6, 1, 6);
        BlockEntity be = place(h, p, "peirce_smith_converter_real", PcBlocks.PEIRCE_SMITH_CONVERTER_REAL.get(),
                PcBlocks.FP_PEIRCE_SMITH_CONVERTER_REAL, 9 * 5 * 5, -4, 4, -2, 2);
        h.assertTrue(be instanceof ConverterBlockEntity, "the old converter block entity");
        h.assertTrue("peirce_smith_converter_real_running".equals(modelWith(be, PcProperties.RUNNING)), "blowing draws the _running model");
        h.assertTrue(items(h, p, 0, 4, 0, Direction.UP), "matte and flux into the mouth");
        h.assertTrue(fluids(h, p, -4, 2, -2, Direction.WEST), "blast air into the header flange");
        h.assertTrue(!items(h, p, 2, 4, 0, Direction.UP), "the barrel crown beside the mouth is closed");
        h.succeed();
    }

    /** Электролизёр: 9 × 4 × 5, работа и анодный эффект — свои модели; глинозём сверху, торец закрыт. */
    @GameTest(template = SMALL, timeoutTicks = 40)
    public static void realReductionPotShowsTheAnodeEffectAndFeedsFromAbove(GameTestHelper h) {
        BlockPos p = new BlockPos(6, 1, 6);
        BlockEntity be = place(h, p, "reduction_pot_real", PcBlocks.REDUCTION_POT_REAL.get(),
                PcBlocks.FP_REDUCTION_POT_REAL, 9 * 4 * 5, -4, 4, -2, 2);
        h.assertTrue(be instanceof ReductionPotBlockEntity, "the old pot block entity");
        h.assertTrue("reduction_pot_real_running".equals(modelWith(be, PcProperties.RUNNING)), "a running pot draws the _running model");
        h.assertTrue("reduction_pot_real_alarm".equals(modelWith(be, PcProperties.ALARM)), "the anode effect draws the _alarm model");
        h.assertTrue(items(h, p, 0, 3, 0, Direction.UP), "alumina onto the crust");
        h.assertTrue(!items(h, p, 4, 0, 0, Direction.EAST), "the pot end is closed");
        h.succeed();
    }

    /** Автоклав: 3 × 10 × 3; пар — с севера, пульпа — сверху, глухой бок закрыт. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void realDigesterTakesSteamFromTheNorth(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        BlockEntity be = place(h, p, "bayer_digester_real", PcBlocks.BAYER_DIGESTER_REAL.get(),
                PcBlocks.FP_BAYER_DIGESTER_REAL, 3 * 10 * 3, -1, 1, -1, 1);
        h.assertTrue(be instanceof BayerDigesterBlockEntity, "the old digester block entity");
        h.assertTrue(fluids(h, p, 0, 1, -1, Direction.NORTH), "live steam from the north");
        h.assertTrue(fluids(h, p, 0, 9, 0, Direction.UP), "slurry into the top head");
        h.assertTrue(!fluids(h, p, 1, 1, 0, Direction.EAST), "the east side is closed");
        h.succeed();
    }

    /** Декомпозёр: 7 × 10 × 7; раствор сверху у моста, перелив — на востоке, глухой пояс закрыт. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void realPrecipitatorOverflowsToTheEast(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        BlockEntity be = place(h, p, "alumina_precipitator_real", PcBlocks.ALUMINA_PRECIPITATOR_REAL.get(),
                PcBlocks.FP_ALUMINA_PRECIPITATOR_REAL, 7 * 10 * 7, -3, 3, -3, 3);
        h.assertTrue(be instanceof AluminaPrecipitatorBlockEntity, "the old precipitator block entity");
        h.assertTrue(fluids(h, p, 3, 8, 0, Direction.EAST), "overflow out on the east");
        h.assertTrue(fluids(h, p, 0, 9, -2, Direction.UP), "liquor in at the bridge");
        h.assertTrue(!fluids(h, p, 3, 0, 0, Direction.EAST), "the foot of the east side is closed");
        h.succeed();
    }

    /** Отражательная печь: 16 × 4 × 6, горит — модель _lit; топливо с запада, шихта — в трубы свода, штейн — из-под лётки. */
    @GameTest(template = LONG, timeoutTicks = 40)
    public static void realMatteFurnaceChargesThroughTheCrownAndTapsBelowTheLaunder(GameTestHelper h) {
        BlockPos p = new BlockPos(12, 1, 5);
        BlockEntity be = place(h, p, "matte_smelter_real", PcBlocks.MATTE_SMELTER_REAL.get(),
                PcBlocks.FP_MATTE_SMELTER_REAL, 16 * 4 * 6, -8, 7, -3, 2);
        h.assertTrue(be instanceof MatteSmelterBlockEntity, "the old smelter block entity");
        h.assertTrue("matte_smelter_real_lit".equals(modelWith(be, BlockStateProperties.LIT)), "firing draws the _lit model");
        h.assertTrue(fluids(h, p, -8, 1, 0, Direction.WEST), "fuel and oxygen to the burners");
        h.assertTrue(items(h, p, 2, 3, 1, Direction.UP), "charge into a crown pipe");
        h.assertTrue(!items(h, p, 1, 3, 1, Direction.UP), "no charging between the pipes");
        h.assertTrue(items(h, p, 2, 0, -3, Direction.DOWN), "matte from under the tap launder");
        BlockPos old = new BlockPos(30, 2, 5);
        h.setBlock(old, PcBlocks.MATTE_SMELTER.get().defaultBlockState());
        h.assertTrue(((WholeModelHost) h.getBlockEntity(old)).wholeModelId() == null, "the old smelter keeps its block model");
        h.succeed();
    }
}
