package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.HeatedWater;
import dev.denfry.promptcritical.mod.greenhouse.DistrictHeatExchangerBlockEntity;
import dev.denfry.promptcritical.mod.greenhouse.FishCageBlockEntity;
import dev.denfry.promptcritical.mod.greenhouse.GreenhouseCoilBlockEntity;
import dev.denfry.promptcritical.mod.greenhouse.GreenhouseGrowth;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.thermal.CoolingPond;
import dev.denfry.promptcritical.sim.thermal.DistrictHeating;
import dev.denfry.promptcritical.sim.thermal.FishCage;
import dev.denfry.promptcritical.sim.thermal.Greenhouse;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Теплица 17 §17.3.6: регистр находит остеклённый объём заливкой и считает стекло и стены; без крыши теплицы нет;
 * холоднее 5 °C растения внутри не растут, а в тепле растут; парник над грядкой — тоже защищённое место.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GreenhouseGameTests {
    private static final String TEMPLATE = "gametest/empty_16x24x16";
    private static final BlockPos COIL = new BlockPos(4, 2, 4), WHEAT = new BlockPos(2, 2, 2);

    private GreenhouseGameTests() {
    }

    /** Стеклянный короб 7×5×7 на камне: внутри 5×3×5 воздуха, в нём регистр и пшеница на пашне. */
    private static void house(GameTestHelper h, boolean roof) {
        for (int x = 1; x <= 7; x++)
            for (int z = 1; z <= 7; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                if (roof) h.setBlock(new BlockPos(x, 5, z), Blocks.GLASS);
                for (int y = 2; y <= 4; y++)
                    if (x == 1 || x == 7 || z == 1 || z == 7) h.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
            }
        h.setBlock(WHEAT.below(), Blocks.FARMLAND);
        h.setBlock(WHEAT, Blocks.WHEAT);
        h.setBlock(COIL, PcBlocks.GREENHOUSE_COIL.get());
    }

    private static boolean grows(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        var e = new CropGrowEvent.Pre(h.getLevel(), abs, h.getLevel().getBlockState(abs));
        NeoForge.EVENT_BUS.post(e);
        return e.getResult() != CropGrowEvent.Pre.Result.DO_NOT_GROW;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void coilFindsTheGlazedVolumeAndHoldsGrowthBelowFiveDegrees(GameTestHelper h) {
        house(h, true);
        h.runAfterDelay(2, () -> {
            var coil = (GreenhouseCoilBlockEntity) h.getBlockEntity(COIL);
            coil.rescan(h.getLevel());
            h.assertTrue(coil.enclosed(), "the glazed box is a greenhouse");
            h.assertTrue(coil.volume() == 74, "75 blocks of air less the coil: " + coil.volume());
            // стены 4 × 5 × 3 и крыша 5 × 5 — стекло; пол без клетки под регистром — глухой
            h.assertTrue(coil.glassM2() == 85, "85 m2 of glass: " + coil.glassM2());
            h.assertTrue(coil.plants() == 1, "the wheat is a plant inside: " + coil.plants());
            h.assertTrue(GreenhouseGrowth.protectedTempC(h.getLevel(), h.absolutePos(WHEAT)) == coil.insideC(), "the wheat sits in the greenhouse air");
            coil.setInsideC(2);
            h.assertFalse(grows(h, WHEAT), "at 2 C nothing grows inside");
            coil.setInsideC(18);
            h.assertTrue(grows(h, WHEAT), "at 18 C the wheat grows");
            h.assertTrue(Math.abs(coil.growthMultiplier() - Greenhouse.growthMultiplier(18)) < 1e-9 && coil.growthMultiplier() > 1.4,
                    "growth x1.44 at 18 C: " + coil.growthMultiplier());
            h.assertTrue(coil.heatW() == 0, "no condenser, no heat");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void noRoofNoGreenhouse(GameTestHelper h) {
        house(h, false);
        h.runAfterDelay(2, () -> {
            var coil = (GreenhouseCoilBlockEntity) h.getBlockEntity(COIL);
            coil.rescan(h.getLevel());
            h.assertFalse(coil.enclosed(), "an open box leaks into the sky volume");
            h.assertTrue(Double.isNaN(GreenhouseGrowth.protectedTempC(h.getLevel(), h.absolutePos(WHEAT))), "the wheat is outside any greenhouse");
            h.assertTrue(grows(h, WHEAT), "vanilla growth is left alone outside");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void coldFrameCoversTheCropBelow(GameTestHelper h) {
        BlockPos crop = new BlockPos(8, 2, 8);
        h.setBlock(crop.below(), Blocks.FARMLAND);
        h.setBlock(crop, Blocks.WHEAT);
        h.setBlock(crop.above(), PcBlocks.COLD_FRAME.get());
        double t = GreenhouseGrowth.protectedTempC(h.getLevel(), h.absolutePos(crop));
        h.assertFalse(Double.isNaN(t), "the crop under a cold frame is protected");
        h.assertTrue(grows(h, crop) == (Greenhouse.growthMultiplier(t) > 0), "the frame grows or holds the crop by its temperature " + t);
        h.succeed();
    }

    /**
     * Пруд 5×5 в каменной чаше: сброс 15 кВт греет его на 15000/(30·25) = 20 К, садок видит эту воду; сброс 60 кВт
     * перегревает воду выше 36 °C — замор, рыба гибнет вся, зарыбление ведром трески возвращает её.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void fishCageSeesThePondWarmedByWasteHeatAndDiesAboveThirtySix(GameTestHelper h) {
        for (int x = 1; x <= 7; x++)
            for (int z = 1; z <= 7; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 2, z), x == 1 || x == 7 || z == 1 || z == 7 ? Blocks.STONE : Blocks.WATER);
            }
        BlockPos cage = new BlockPos(4, 3, 4), water = new BlockPos(4, 2, 4), condenser = new BlockPos(9, 2, 9);
        h.setBlock(cage, PcBlocks.FISH_CAGE.get());
        var sl = h.getLevel();
        BlockPos wAbs = h.absolutePos(water);
        double base = HeatedWater.waterTempC(sl, wAbs);
        HeatedWater.discharge(sl, h.absolutePos(condenser), wAbs, 15e3);
        double rise = HeatedWater.riseK(sl, wAbs);
        h.assertTrue(Math.abs(rise - CoolingPond.pondRiseK(15e3, 25)) < 1e-9 && Math.abs(rise - 20) < 1e-9, "25 m2 pond warms 20 K: " + rise);
        h.runAfterDelay(25, () -> {
            var be = (FishCageBlockEntity) h.getBlockEntity(cage);
            h.assertTrue(be.onWater(), "the cage floats on the pond");
            h.assertTrue(Math.abs(be.waterC() - (base + 20)) < 0.5, "the cage water is the warmed pond: " + be.waterC() + " vs " + (base + 20));
            h.assertFalse(be.cage().killed(), "the fish live at " + be.waterC());
            HeatedWater.discharge(sl, h.absolutePos(condenser), wAbs, 60e3);
            h.runAfterDelay(25, () -> {
                h.assertTrue(be.waterC() > FishCage.KILL_C, "80 K of waste heat overheats the pond: " + be.waterC());
                h.assertTrue(be.cage().killed() && be.cage().biomassKg() == 0, "fish kill above 36 C");
                h.succeed();
            });
        });
    }

    /** Сетевой подогреватель считает кровати поселения вокруг, нагрузку отопления по ним; без турбины над ним пара нет. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void districtHeaterCountsTheSettlementLoadAndGivesNothingWithoutSteam(GameTestHelper h) {
        BlockPos heater = new BlockPos(8, 2, 8);
        h.setBlock(heater, PcBlocks.DISTRICT_HEAT_EXCHANGER.get());
        // точка интереса «дом» — изголовье кровати, а не изножье (состояние по умолчанию)
        var head = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD);
        h.setBlock(new BlockPos(2, 2, 2), head);
        h.setBlock(new BlockPos(2, 2, 6), head);
        h.runAfterDelay(5, () -> {
            var be = (DistrictHeatExchangerBlockEntity) h.getBlockEntity(heater);
            be.rescan(h.getLevel());
            h.assertTrue(be.beds() >= 2, "the two beds are homes of the settlement: " + be.beds());
            be.sample(h.getLevel());
            double outdoor = dev.denfry.promptcritical.mod.environment.EnvironmentService.sample(h.getLevel(), h.absolutePos(heater), 2).airTempC();
            double load = DistrictHeating.heatingLoadW(be.beds() * (double) DistrictHeatExchangerBlockEntity.HOUSE_M3, outdoor);
            h.assertTrue(Math.abs(be.loadW() - load) < 1e-6, "heating load by beds: " + be.loadW() + " vs " + load);
            h.assertTrue(be.supplyW() == 0 && be.deliveredW() == 0, "no turbine, no steam heat");
            h.succeed();
        });
    }
}
