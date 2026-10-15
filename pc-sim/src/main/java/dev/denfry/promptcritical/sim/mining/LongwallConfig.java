package dev.denfry.promptcritical.sim.mining;

/**
 * Ключи {@code mining.longwall_*} (08 §8.2.3, §8.19).
 *
 * @param shearerRatedW      мощность привода комбайна при номинальной скорости вала, Вт (2 × 110 кВт) [BALANCE]
 * @param nominalRpm         номинальная скорость вала gantry, об/мин
 * @param maxFeedMPerMin     наибольшая скорость подачи, м/мин (08 §8.2.3: 2–6)
 * @param cutHeightBlocks    вынимаемая мощность — высота полосы, блоков (ведущий шнек — кровля, отстающий — почва)
 * @param rockProtodyakonov  крепость породы, которую шнеки режут вне пласта, f [BALANCE]
 * @param sprayWaterLPerT    расход воды на орошение, л/т [BALANCE]
 * @param sprayDustFactor    доля пыли при орошении (08 §8.2.3: ÷5)
 * @param supportSetS        время распора или разгрузки секции мехкрепи, с [BALANCE]
 */
public record LongwallConfig(double shearerRatedW, double nominalRpm, double maxFeedMPerMin, int cutHeightBlocks,
        double rockProtodyakonov, double sprayWaterLPerT, double sprayDustFactor, double supportSetS) {
    public LongwallConfig {
        if (!(shearerRatedW > 0 && nominalRpm > 0 && maxFeedMPerMin > 0)) throw new IllegalArgumentException("shearer drive");
        if (cutHeightBlocks < 1) throw new IllegalArgumentException("cut height");
        if (!(rockProtodyakonov > 0 && sprayWaterLPerT >= 0 && sprayDustFactor >= 0 && sprayDustFactor <= 1 && supportSetS >= 0))
            throw new IllegalArgumentException("longwall");
    }

    public static LongwallConfig defaults() {
        return new LongwallConfig(220e3, 64, 6, 3, 4, 30, 0.2, 2);
    }
}
