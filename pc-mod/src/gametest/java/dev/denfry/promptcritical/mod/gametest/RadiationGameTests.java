package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.ShieldBlocks;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Излучение в мире: точечный источник Co-60 даёт мощность дозы по формуле точечного источника в воздухе, свинцовая
 * стена между источником и точкой ослабляет её по таблицам, бетон — меньше свинца той же толщины; трассировка видит
 * блоки на прямой и не видит соседние.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private RadiationGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 100, batch = "radiation_alone")
    public static void cobaltSourceThroughAirLeadAndConcrete(GameTestHelper h) {
        Vec3 src = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 3, 6)));
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 6)));
        PointSource cobalt = Emitter.CO60.source(1e12); // 1 ТБк
        h.startSequence()
                .thenExecute(() -> {
                    RadiationService.source(h.getLevel(), src, cobalt);
                    double air = RadiationService.rateSvH(h.getLevel(), at);
                    double expected = DoseRate.totalSvH(cobalt, 6, List.of());
                    h.assertTrue(Math.abs(air - expected) < expected * 1e-6, "air: " + air + " Sv/h, expected " + expected);
                    // 1 ТБк Co-60 на 6 м: 0,306·1000/36 ≈ 8,5 мГр/ч
                    h.assertTrue(air > 7e-3 && air < 10e-3, "dose rate at 6 m: " + air * 1e3 + " mSv/h");
                })
                .thenExecute(() -> h.setBlock(new BlockPos(5, 3, 6), PcBlocks.LEAD_BLOCK.get()))
                .thenIdle(1)
                .thenExecute(() -> {
                    RadiationService.source(h.getLevel(), src, cobalt);
                    var layers = ShieldBlocks.between(h.getLevel(), src, at);
                    h.assertTrue(layers.size() == 1 && layers.get(0).material() == ShieldMaterial.LEAD
                            && Math.abs(layers.get(0).cm() - 100) < 25, "one metre of lead on the line: " + layers);
                    double lead = RadiationService.rateSvH(h.getLevel(), at);
                    // больше 12 порядков ослабления; ниже порог ловит фон источников соседних тестов в общем мире (~1e-19 Зв/ч)
                    h.assertTrue(lead < DoseRate.totalSvH(cobalt, 6, List.of()) * 1e-12, "a metre of lead stops cobalt gamma: " + lead);
                })
                .thenExecute(() -> h.setBlock(new BlockPos(5, 3, 6), Blocks.WHITE_CONCRETE))
                .thenIdle(1)
                .thenExecute(() -> {
                    RadiationService.source(h.getLevel(), src, cobalt);
                    double concrete = RadiationService.rateSvH(h.getLevel(), at);
                    double air = DoseRate.totalSvH(cobalt, 6, List.of());
                    // метр бетона: ≈ 15 длин пробега, с накоплением — ослабление в 10⁴–10⁵ раз
                    h.assertTrue(concrete < air * 1e-3 && concrete > air * 1e-8, "a metre of concrete: " + concrete / air);
                    // блок сбоку от прямой не мешает
                    h.setBlock(new BlockPos(5, 3, 6), Blocks.AIR);
                    h.setBlock(new BlockPos(5, 5, 6), PcBlocks.LEAD_BLOCK.get());
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    RadiationService.source(h.getLevel(), src, cobalt);
                    double side = RadiationService.rateSvH(h.getLevel(), at);
                    double air = DoseRate.totalSvH(cobalt, 6, List.of());
                    h.assertTrue(Math.abs(side - air) < air * 1e-6, "lead off the line does not shield");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void sourceExpiresWhenNotRenewed(GameTestHelper h) {
        Vec3 src = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 3, 6)));
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 6)));
        h.startSequence()
                .thenExecute(() -> RadiationService.source(h.getLevel(), src, Emitter.CS137.source(1e11)))
                .thenExecute(() -> h.assertTrue(RadiationService.holds(h.getLevel(), src) && RadiationService.rateSvH(h.getLevel(), at) > 0,
                        "source registered"))
                .thenIdle(60)
                // мир общий с другими тестами (их источники в радиусе 200 м), поэтому проверяем именно этот источник
                .thenExecute(() -> h.assertTrue(!RadiationService.holds(h.getLevel(), src), "source forgotten after the hold time"))
                .thenSucceed();
    }
}
