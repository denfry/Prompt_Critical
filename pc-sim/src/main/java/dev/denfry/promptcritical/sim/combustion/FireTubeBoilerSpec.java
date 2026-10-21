package dev.denfry.promptcritical.sim.combustion;

/**
 * Конструкция жаротрубно-дымогарного котла I0 (07 §7.9.4). Водяной объём — узел {@code SEPARATED} гидросети в виде
 * эквивалентной вертикальной призмы: сечение — зеркало испарения горизонтальной обечайки (от него зависит набухание
 * уровня при кипении), высота — объём обечайки над водой и под ней. Отметки — от низа этого узла.
 *
 * @param workingPressurePa     рабочее давление p_раб, Па; уставка ПК — 1,1·p_раб
 * @param burstPressureFactor   давление разрыва обечайки в долях p_раб [BALANCE]
 * @param nominalFuelKgS        номинальный расход угля на решётку, кг/с
 * @param bedBurnoutTimeS       постоянная выгорания слоя τ: скорость горения = M_слоя/τ
 * @param furnaceWallAreaM2     F_ст — поверхность жаровых труб, м²
 * @param furnaceEmissivity     a_т — степень черноты слоевой топки
 * @param screenEfficiency      ψ — коэффициент тепловой эффективности стен (закопчённая сталь)
 * @param convectiveUA          UA дымогарных труб при расчётном расходе газов, Вт/К
 * @param chimneyHeightM        высота трубы H, м
 * @param designAirKgS          расход воздуха при расчётной тяге и открытом шибере, кг/с
 * @param designDraftPa         расчётная тяга, Па
 * @param crownHeightM          отметка потолка жаровой трубы (и легкоплавкой пробки), м
 * @param crownWallM            толщина стенки жаровой трубы, м
 * @param shellLossWPerK        UA потери обечайкой в окружающую среду, Вт/К
 * @param waterNodeAreaM2       сечение эквивалентной призмы водяного узла (зеркало испарения), м²
 * @param waterNodeHeightM      высота призмы, м
 * @param normalLevelM          нормальный уровень воды, м
 */
public record FireTubeBoilerSpec(double workingPressurePa, double burstPressureFactor, double nominalFuelKgS,
        double bedBurnoutTimeS, double furnaceWallAreaM2, double furnaceEmissivity, double screenEfficiency,
        double convectiveUA, double chimneyHeightM, double designAirKgS, double designDraftPa, double crownHeightM,
        double crownWallM, double shellLossWPerK, double waterNodeAreaM2, double waterNodeHeightM,
        double normalLevelM) {
    public FireTubeBoilerSpec {
        if (!(workingPressurePa > 0 && burstPressureFactor > FireTubeBoiler.RELIEF_FACTOR))
            throw new IllegalArgumentException("pressure ratings");
        if (!(nominalFuelKgS > 0 && bedBurnoutTimeS > 0)) throw new IllegalArgumentException("grate");
        if (!(furnaceWallAreaM2 > 0 && furnaceEmissivity > 0 && furnaceEmissivity <= 1 && screenEfficiency > 0
                && screenEfficiency <= 1)) throw new IllegalArgumentException("furnace");
        if (!(convectiveUA >= 0 && chimneyHeightM > 0 && designAirKgS > 0 && designDraftPa > 0))
            throw new IllegalArgumentException("gas path");
        if (!(crownWallM > 0 && shellLossWPerK >= 0 && waterNodeAreaM2 > 0))
            throw new IllegalArgumentException("shell");
        if (!(crownHeightM > 0 && crownHeightM < normalLevelM && normalLevelM < waterNodeHeightM))
            throw new IllegalArgumentException("levels: crown < normal < height");
    }

    /**
     * Котёл по умолчанию: 5 МВт(т), 1,2 МПа насыщенный, уголь Г 0,3 кг/с на решётке, труба 20 м. Две жаровые
     * трубы ∅1 м × 5,5 м (F_ст ≈ 35 м²) и дымогарный пучок без экономайзера. В номинале α ≈ 1,45 (07 §7.8.2:
     * решётка 1,3–1,5), уходящие газы ≈ 335 °C и тяга ≈ 124 Па, q₄ = 5 %, q₅ = 5 % (верх диапазона I0, голая
     * обечайка) — КПД брутто ≈ 0,71 и 5,1 МВт в пар, как пример 07 §7.9.4 (0,70; 5,0 МВт).
     *
     * <p>Обечайка ∅3 × 6 м: зеркало ≈ 15 м², 10 м³ воды до нормального уровня 0,67 м, паровое пространство
     * ≈ 14 м³ (призма 1,6 м). Потолок жаровых труб на 0,22 м ниже нормального уровня: запас ≈ 3 т воды, без
     * подпитки в номинале — около 25 мин до оголения.
     */
    public static FireTubeBoilerSpec defaults() {
        return new FireTubeBoilerSpec(1.2e6, 4.0, 0.3, 600, 34.6, 0.6, 0.45, 8_000, 20, 3.53, 124, 0.45, 0.012,
                2_070, 15, 1.6, 0.67);
    }

    /** Та же конструкция с другим давлением разрыва: шов с монтажным дефектом (13 §13.6.6, {@code SEAM_WEEP}). */
    public FireTubeBoilerSpec withBurstPressureFactor(double factor) {
        return new FireTubeBoilerSpec(workingPressurePa, factor, nominalFuelKgS, bedBurnoutTimeS, furnaceWallAreaM2,
                furnaceEmissivity, screenEfficiency, convectiveUA, chimneyHeightM, designAirKgS, designDraftPa,
                crownHeightM, crownWallM, shellLossWPerK, waterNodeAreaM2, waterNodeHeightM, normalLevelM);
    }

    /** Уставка предохранительного клапана p_set = 1,1·p_раб, Па. */
    public double reliefSetPressure() {
        return FireTubeBoiler.RELIEF_FACTOR * workingPressurePa;
    }
}
