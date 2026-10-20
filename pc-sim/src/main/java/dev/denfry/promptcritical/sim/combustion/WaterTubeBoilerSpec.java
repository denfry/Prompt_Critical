package dev.denfry.promptcritical.sim.combustion;

import java.util.Objects;

/**
 * Конструкция водотрубного барабанного котла I1 (07 §7.9.5). Площади и объём топки в мире берутся из мультиблока.
 *
 * @param burnerKind          тип горелок (по топливу)
 * @param burners             число горелок, 1–8
 * @param ratedFuelKgS        номинальный расход топлива на все горелки, кг/с
 * @param ratedAirKgS         номинальный расход воздуха дутьевого вентилятора, кг/с
 * @param furnaceVolumeM3     объём топки V_т, м³ (продувка, накопление топлива)
 * @param furnaceWallAreaM2   F_ст — площадь стен топки (экраны), м²
 * @param furnaceEmissivity   a_т — степень черноты топки (газ 0,3–0,4, пыль 0,5–0,7)
 * @param screenEfficiency    ψ — коэффициент тепловой эффективности экранов (газ 0,65, уголь 0,45)
 * @param superheaterUA       UA пароперегревателя при номинальном расходе газов, Вт/К
 * @param economizerUA        UA экономайзера, Вт/К
 * @param airPreheaterUA      UA воздухоподогревателя, Вт/К
 * @param screenTubeOuterM    наружный диаметр труб экрана, м
 * @param screenWallM         толщина стенки трубы экрана, м
 * @param screenCreep         длительная прочность металла экранов
 * @param screenStarveLevelM  уровень в барабане, ниже которого циркуляция срывается и экраны омываются паром, м
 * @param casingLossWPerK     UA потери обмуровкой q₅ по T_нас − T_нар, Вт/К
 */
public record WaterTubeBoilerSpec(BurnerKind burnerKind, int burners, double ratedFuelKgS, double ratedAirKgS,
        double furnaceVolumeM3, double furnaceWallAreaM2, double furnaceEmissivity, double screenEfficiency,
        double superheaterUA, double economizerUA, double airPreheaterUA, double screenTubeOuterM, double screenWallM,
        CreepModel screenCreep, double screenStarveLevelM, double casingLossWPerK) {
    public WaterTubeBoilerSpec {
        Objects.requireNonNull(burnerKind, "burnerKind");
        Objects.requireNonNull(screenCreep, "screenCreep");
        if (burners < 1 || burners > 8) throw new IllegalArgumentException("burners: " + burners);
        if (!(ratedFuelKgS > 0 && ratedAirKgS > 0)) throw new IllegalArgumentException("rating");
        if (!(furnaceVolumeM3 > 0 && furnaceWallAreaM2 > 0 && furnaceEmissivity > 0 && furnaceEmissivity <= 1
                && screenEfficiency > 0 && screenEfficiency <= 1)) throw new IllegalArgumentException("furnace");
        if (!(superheaterUA >= 0 && economizerUA >= 0 && airPreheaterUA >= 0)) throw new IllegalArgumentException("UA");
        if (!(screenTubeOuterM > 2 * screenWallM && screenWallM > 0)) throw new IllegalArgumentException("screen tube");
        if (!(casingLossWPerK >= 0)) throw new IllegalArgumentException("casingLossWPerK");
    }

    /**
     * Газовый котёл примера 07 §7.9.5: 100 МВт(т) по топливу (газ 2,0 кг/с, 4 горелки, α 1,1), пар 9,8 МПа/540 °C
     * из воды 230 °C, ≈ 37 кг/с (133 т/ч), КПД ≈ 0,92. Топка 8 × 8 × 14 м (F_ст 640 м²); экраны ∅60 × 5 мм из
     * стали 20. На природном газе (LHV ≈ 48 МДж/кг) при α 1,1: T''_т ≈ 1120 °C, перегрев ≈ 540 °C, вода за
     * экономайзером ≈ 300 °C, уходящие газы ≈ 135 °C, горячий воздух ≈ 190 °C, КПД ≈ 0,93, ≈ 36 кг/с пара.
     */
    public static WaterTubeBoilerSpec defaults() {
        return new WaterTubeBoilerSpec(BurnerKind.GAS, 4, 2.0, 36.5, 900, 640, 0.35, 0.65, 72e3, 90e3, 65e3,
                0.060, 0.005, LarsonMiller.CARBON, 0.3, 2_300);
    }

    /** Расход топлива одной горелки в номинале, кг/с. */
    public double burnerRatedFuelKgS() {
        return ratedFuelKgS / burners;
    }
}
