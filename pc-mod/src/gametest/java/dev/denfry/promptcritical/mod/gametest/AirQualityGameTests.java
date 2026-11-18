package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.environment.FgdAbsorberBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.combustion.CombustionResult;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Загрязнение (14): сероочистка рядом с трубой снижает учтённый SO₂, а САКВ у устья переводит учёт в «измерено». */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirQualityGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private AirQualityGameTests() {
    }

    private static CombustionResult coalBoiler() {
        CombustionResult r = new CombustionResult();
        r.fluegasKgS = 60;
        r.so2KgS = 0.05;
        r.noxKgS = 0.02;
        r.pmKgS = 0.01;
        r.co2KgS = 10;
        return r;
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void scrubberAndCemsChangeTheLedger(GameTestHelper h) {
        BlockPos top = new BlockPos(5, 6, 5), fgd = new BlockPos(3, 1, 3);
        h.setBlock(fgd, PcBlocks.FGD_ABSORBER.get());
        h.setBlock(top.below(), PcBlocks.CEMS_ANALYZER.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> ((FgdAbsorberBlockEntity) h.getBlockEntity(fgd)).items(null).insertItem(0, new ItemStack(Items.CALCITE, 16), false))
                .thenIdle(2)
                .thenExecute(() -> AirQualityService.emit(h.getLevel(), h.absolutePos(top), 30, 420, coalBoiler(), "test_boiler",
                        EmissionLedger.Category.BOILER))
                .thenExecute(() -> {
                    EmissionLedger.Source s = AirQualityService.ledger(h.getLevel()).sources().get("test_boiler");
                    h.assertTrue(s != null, "source recorded");
                    double so2 = s.dayKg(EmissionLedger.Pollutant.SO2), raw = 0.05 * 72;
                    h.assertTrue(so2 < 0.1 * raw, "scrubber took most SO₂: " + so2 + " of " + raw);
                    h.assertTrue(s.method == EmissionLedger.Method.MEASURED, "CEMS at the stack: " + s.method);
                    h.assertTrue(AirQualityService.activeSources(h.getLevel()) >= 1, "plume source registered");
                })
                .thenSucceed();
    }
}
