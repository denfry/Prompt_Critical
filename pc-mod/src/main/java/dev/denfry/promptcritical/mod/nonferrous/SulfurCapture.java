package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.uranium.AcidPlantBlockEntity;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Сернистый газ плавки и конвертирования (08 §8.8.3: ≈ 2 т SO₂ на т Cu): газоход к сернокислотной установке
 * ({@code acid_plant}, контактный аппарат 02) в {@value #REACH} блоках — сера идёт в кислоту вместо колчедана; без
 * установки или сверх её буфера — в трубу (14), в учёт выбросов и в поле загрязнения. Выброс в трубу отдаётся раз в
 * секунду, как у топок.
 */
public final class SulfurCapture {
    public static final int REACH = 8;
    /** Дымовая труба цеха: высота, м, и температура газа в устье, К. */
    public static final double STACK_M = 40, STACK_K = 450;

    private double ventKg, capturedKg;
    private long lastEmitTick = -100, scanTick = -100;
    private final java.util.List<BlockPos> plants = new java.util.ArrayList<>();

    /** Сернистый газ за шаг, кг SO₂; возвращает кг, ушедшие в трубу. */
    public double offer(ServerLevel level, BlockPos pos, double so2Kg, String id) {
        if (so2Kg <= 0) {
            flush(level, pos, id);
            return 0;
        }
        double sKg = so2Kg * SulfideSmelting.M_S / SulfideSmelting.M_SO2;
        // установки ищем раз в секунду, между поисками — по запомненным позициям
        if (level.getGameTime() - scanTick >= 20) {
            scanTick = level.getGameTime();
            plants.clear();
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-REACH, -REACH, -REACH), pos.offset(REACH, REACH, REACH)))
                if (level.getBlockEntity(p) instanceof AcidPlantBlockEntity) plants.add(p.immutable());
        }
        double taken = 0;
        for (BlockPos p : plants) {
            if (taken >= sKg) break;
            if (level.getBlockEntity(p) instanceof AcidPlantBlockEntity acid) taken += acid.acceptSulfurKg(sKg - taken);
        }
        double vented = (sKg - taken) * SulfideSmelting.M_SO2 / SulfideSmelting.M_S;
        capturedKg += taken * SulfideSmelting.M_SO2 / SulfideSmelting.M_S;
        ventKg += vented;
        flush(level, pos, id);
        return vented;
    }

    private void flush(ServerLevel level, BlockPos pos, String id) {
        if (level.getGameTime() - lastEmitTick < 20) return;
        lastEmitTick = level.getGameTime();
        if (ventKg <= 0) return;
        // за секунду реального времени прошло 72 с физ.: средний расход — кг/72, учёт пишет его за 72 с
        AirQualityService.emit(level, pos.above(3), STACK_M, STACK_K, 50 * ventKg / 72, ventKg / 72, 0, 0, 0, 0, id,
                EmissionLedger.Category.PROCESS);
        ventKg = 0;
    }

    public double capturedKg() {
        return capturedKg;
    }
}
