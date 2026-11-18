package dev.denfry.promptcritical.mod.heavywater;

import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Сорт воды ({@link HeavyWaterGrade}) ↔ жидкость в трубах: природная — ванильная вода, остальные — свои жидкости с
 * номинальной долей дейтерия.
 */
public final class HeavyWaterFluids {
    private HeavyWaterFluids() {
    }

    /** Сорт жидкости; {@code null} — не вода. */
    @Nullable
    public static HeavyWaterGrade gradeOf(Fluid f) {
        if (f.isSame(Fluids.WATER)) return HeavyWaterGrade.LIGHT;
        if (f.isSame(PcFluids.HEAVY_WATER.source.get())) return HeavyWaterGrade.REACTOR;
        if (f.isSame(PcFluids.DOWNGRADED_HEAVY_WATER.source.get())) return HeavyWaterGrade.DOWNGRADED;
        if (f.isSame(PcFluids.DEUTERIUM_ENRICHED_WATER.source.get())) return HeavyWaterGrade.ENRICHED;
        return null;
    }

    public static Fluid fluid(HeavyWaterGrade g) {
        return switch (g) {
            case LIGHT -> Fluids.WATER;
            case ENRICHED -> PcFluids.DEUTERIUM_ENRICHED_WATER.source.get();
            case DOWNGRADED -> PcFluids.DOWNGRADED_HEAVY_WATER.source.get();
            case REACTOR -> PcFluids.HEAVY_WATER.source.get();
        };
    }
}
