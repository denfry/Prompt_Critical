package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.AirSeparatorBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.HydrotestBenchBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.MillStandBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.mod.steelworks.Steelworks;
import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.DefectKind;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.PipeDefect;
import dev.denfry.promptcritical.sim.metallurgy.PipeKind;
import dev.denfry.promptcritical.sim.metallurgy.PipeSpec;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import dev.denfry.promptcritical.sim.metallurgy.StockShape;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Сталь, прокат, трубы и ВРУ в мире (этап 10б M3, 08 §8.6–8.7, §8.11): сталь и трубы идут в рецепты (критерий 06:22),
 * горячий прокат остывает в инвентаре, клеть обжимает раскат на валу Create, гидропресс бракует трубу с трещиной,
 * ВРУ собирается из компрессора, блока осушки и детандера.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteelworksGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private SteelworksGameTests() {
    }

    private static final SteelGrade ST3 = grade();

    private static SteelGrade grade() {
        Map<AlloyElement, Double> chem = new EnumMap<>(AlloyElement.class);
        chem.put(AlloyElement.C, 0.0018);
        chem.put(AlloyElement.MN, 0.005);
        chem.put(AlloyElement.SI, 0.002);
        return SteelGrade.classify(chem, HeatTreatState.AS_ROLLED);
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    private static void motor(GameTestHelper h, BlockPos rel, Direction toward, int rpm) {
        h.setBlock(rel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toward));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(rel)).generatedSpeed.setValue(rpm));
    }

    private static int count(IItemHandler inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(item)) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    private static boolean uses(GameTestHelper h, Item result, Item ingredient) {
        for (RecipeHolder<?> r : h.getLevel().getRecipeManager().getRecipes()) {
            if (!r.value().getResultItem(h.getLevel().registryAccess()).is(result)) continue;
            for (Ingredient in : r.value().getIngredients()) if (in.test(new ItemStack(ingredient))) return true;
        }
        return false;
    }

    @GameTest(template = LARGE)
    public static void steelAndPipesGoIntoRecipes(GameTestHelper h) {
        h.assertTrue(uses(h, PcBlocks.SHAFT_GENERATOR.get().asItem(), PcItems.STEEL_PLATE.get()), "generator from steel plate");
        h.assertTrue(uses(h, PcBlocks.SWITCHGEAR_CELL.get().asItem(), PcItems.STEEL_BEAM.get()), "switchgear cell from steel beam");
        h.assertTrue(uses(h, PcBlocks.RELAY_RACK.get().asItem(), PcItems.STEEL_PLATE.get()), "relay rack from steel plate");
        h.assertTrue(uses(h, AllBlocks.FLUID_PIPE.get().asItem(), PcItems.SEAMLESS_PIPE.get()), "Create pipe from seamless pipe");
        h.assertTrue(uses(h, PcBlocks.CRYOGENIC_TANK.get().asItem(), PcItems.SEAMLESS_PIPE.get()), "cryogenic tank from seamless pipe");
        h.assertTrue(uses(h, PcItems.STEEL_BEAM.get(), PcItems.STEEL_PLATE.get()), "beam welded from plates");
        h.assertFalse(uses(h, PcBlocks.BOF_VESSEL.get().asItem(), PcItems.STEEL_PLATE.get()), "converter is made before steel");
        h.assertFalse(uses(h, PcBlocks.AIR_SEPARATOR.get().asItem(), PcItems.STEEL_PLATE.get()), "oxygen for the first heat needs no steel");
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void hotPlateCoolsAndStacksWithColdOnes(GameTestHelper h) {
        long now = h.getLevel().getGameTime();
        StockState hot = new StockState(StockShape.PLATE, 0.01, 1.0, 6, 1173.15, ST3, false, false);
        ItemStack plate = Steelworks.piece(hot, 1, now);
        h.assertTrue(Steelworks.tempK(plate, now) > 1100, "fresh plate is red hot");
        // 10-мм лист на воздухе за час остывает ниже 60 °C
        ItemStack old = Steelworks.piece(hot, 1, now - 72_000);
        h.assertTrue(Steelworks.tempK(old, now) < SteelworksComponents.COLD_K, "an hour later the plate is cold");
        // остывший нормализуется на ближайшем секундном тике инвентаря
        h.succeedWhen(() -> {
            old.getItem().inventoryTick(old, h.getLevel(), null, 0, false);
            h.assertFalse(Steelworks.stock(old).hot(), "cooled plate drops its hot fields");
            ItemStack fresh = Steelworks.piece(hot.withTemp(293.15), 1, h.getLevel().getGameTime());
            h.assertTrue(ItemStack.isSameItemSameComponents(old, fresh), "cold plate stacks with a plate rolled cold");
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void millStandReducesABilletOnACreateShaft(GameTestHelper h) {
        BlockPos stand = new BlockPos(5, 2, 5);
        h.setBlock(stand, facing(PcBlocks.ROLLING_MILL_STAND.get().defaultBlockState(), Direction.NORTH));
        motor(h, stand.west(2), Direction.EAST, 64);
        StockState billet = new StockState(StockShape.BILLET, 0.15, 0.15, 6, 1373.15, ST3, false, false);
        h.startSequence()
                .thenWaitUntil(() -> h.assertBlockProperty(stand, PcProperties.RUNNING, true))
                .thenExecute(() -> {
                    MillStandBlockEntity m = (MillStandBlockEntity) h.getBlockEntity(stand);
                    m.setGapMm(120);
                    ItemStack in = Steelworks.piece(billet, 7, h.getLevel().getGameTime());
                    h.assertFalse(m.acceptPiece(in.copy(), Direction.EAST), "a piece across the stand is refused");
                    h.assertTrue(m.acceptPiece(in, Direction.SOUTH), "billet bitten at 120 mm, outcome " + m.lastOutcome());
                    SteelworksComponents.SteelStock out = Steelworks.stock(m.piece());
                    h.assertTrue(out != null && out.thicknessMm() <= 125 && out.widthMm() > 150,
                            "reduced and spread, got " + out);
                    h.assertTrue(out.heatNo() == 7, "heat number kept");
                    h.assertTrue(m.lastTorqueNm() > 0 && m.lastTorqueNm() < MillStandBlockEntity.MAX_TORQUE_NM, "torque " + m.lastTorqueNm());
                })
                .thenSucceed();
    }

    private static ItemStack pipe(PipeDefect d) {
        PipeSpec p = new PipeSpec("X52", 0.219, 0.008, 359e6, 23.6e6, true, false, false, 0, PipeKind.LINE, 455e6, 0, d, 1);
        ItemStack s = new ItemStack(PcItems.SEAMLESS_PIPE.get());
        s.set(SteelworksComponents.PIPE_SPEC.get(), SteelworksComponents.PipeData.of(p));
        return s;
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void hydrotestPassesASoundPipeAndBurstsACrackedOne(GameTestHelper h) {
        BlockPos bench = new BlockPos(5, 1, 5);
        h.setBlock(bench, facing(PcBlocks.HYDROTEST_BENCH.get().defaultBlockState(), Direction.NORTH));
        ItemStack cracked = pipe(new PipeDefect(DefectKind.HOOK_CRACK, 0.8, 0.2));
        h.assertTrue(cracked.get(SteelworksComponents.PIPE_SPEC.get()).spec().hydrotest().burst(), "precondition: the crack fails the test");
        h.runAfterDelay(2, () -> {
            HydrotestBenchBlockEntity b = (HydrotestBenchBlockEntity) h.getBlockEntity(bench);
            IFluidHandler water = b.fluids(null);
            h.assertTrue(water.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "water");
            IItemHandler inv = b.items(null);
            h.assertTrue(inv.insertItem(0, pipe(PipeDefect.NONE), false).isEmpty(), "sound pipe in");
        });
        h.runAfterDelay(2 + HydrotestBenchBlockEntity.TEST_TICKS + 5, () -> {
            IItemHandler inv = ((HydrotestBenchBlockEntity) h.getBlockEntity(bench)).items(null);
            ItemStack ok = ItemStack.EMPTY;
            for (int i = 1; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(PcItems.SEAMLESS_PIPE.get())) ok = inv.getStackInSlot(i);
            h.assertTrue(!ok.isEmpty(), "sound pipe passed");
            h.assertTrue((ok.get(SteelworksComponents.PIPE_SPEC.get()).inspectedMask() & PipeSpec.HYDRO) != 0, "marked HYDRO");
            h.assertTrue(inv.insertItem(0, cracked, false).isEmpty(), "cracked pipe in");
        });
        h.succeedWhen(() -> h.assertBlockProperty(bench, PcProperties.BURST, true));
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void airSeparatorFormsFromCompressorSieveAndExpander(GameTestHelper h) {
        BlockPos column = new BlockPos(2, 1, 2);
        h.setBlock(column, facing(PcBlocks.AIR_SEPARATOR.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(new BlockPos(8, 1, 2), facing(PcBlocks.ASU_EXPANDER.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenIdle(AirSeparatorBlockEntity.SCAN_TICKS + 5)
                .thenExecute(() -> h.assertFalse(((AirSeparatorBlockEntity) h.getBlockEntity(column)).formed(), "no compressor, no sieve"))
                .thenExecute(() -> {
                    h.setBlock(new BlockPos(2, 2, 7), facing(PcBlocks.ASU_MOLSIEVE.get().defaultBlockState(), Direction.NORTH));
                    h.setBlock(new BlockPos(6, 1, 6), facing(PcBlocks.ASU_MAIN_COMPRESSOR.get().defaultBlockState(), Direction.NORTH));
                })
                .thenWaitUntil(() -> {
                    AirSeparatorBlockEntity a = (AirSeparatorBlockEntity) h.getBlockEntity(column);
                    h.assertTrue(a.formed() && a.unit() != null && a.unit().asu() != null, "ASU formed with its twin");
                })
                .thenSucceed();
    }
}
