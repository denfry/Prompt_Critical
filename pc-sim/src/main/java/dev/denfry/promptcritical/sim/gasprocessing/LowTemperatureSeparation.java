package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Установка подготовки попутного газа: дожимной компрессор, аппарат воздушного охлаждения, осушка ТЭГ и
 * низкотемпературная сепарация (НТС) — дросселирование через штуцер с охлаждением по Джоулю–Томсону и отбор
 * сконденсировавшейся широкой фракции лёгких углеводородов (ШФЛУ) в низкотемпературном сепараторе (Бекиров,
 * «Первичная переработка природных газов», гл. 6; GPSA, разд. 16; Campbell, т. 2, гл. 16).
 *
 * <p>Цепочка на нормальный кубометр газа: насыщенный водой газ сепаратора ({@link WaterContent}) сжимается
 * ({@link GasCompressor}), охлаждается воздухом — вода выпадает до равновесия, осушается гликолем
 * ({@link GlycolDehydration}), доохлаждается в испарителе пропанового холодильного цикла до уставки, дросселируется до
 * давления НТС с учётом теплоты конденсации ({@link PengRobinson#throttleTwoPhase}) и разделяется флешем ({@link Flash}). Одного дросселирования жирному
 * попутному газу мало (с 7 до 3,5 МПа — около 25 K), поэтому НТС попутного газа работают с внешним холодом
 * («НТС с искусственным холодом», Бекиров, гл. 6). Холодильная мощность — явное тепло газа плюс теплота конденсации
 * выпавших углеводородов 350 кДж/кг; работа цикла — по холодильному коэффициенту 0,55 от цикла Карно между
 * испарением на 5 K ниже уставки и конденсацией при 45 °C (GPSA, разд. 14).
 *
 * <p>Если остаточная влага осушенного газа выше равновесной при температуре испарителя или НТС — выпадает свободная
 * вода, и при температуре ниже гидратной (с учётом метанола, {@link Hydrates}) в испарителе, штуцере и шлейфе
 * нарастает гидратная пробка. Гидрат содержит
 * около 87 % воды по массе (структура I, 5,75 молекул воды на молекулу газа; Sloan, Koh, «Clathrate Hydrates of
 * Natural Gases»). Пробка в {@link #PLUG_KG} кг гидрата перекрывает штуцер [BALANCE: малый диаметр штуцера и шлейфа];
 * выше гидратной температуры пробка тает за {@link #MELT_S} с.
 */
public final class LowTemperatureSeparation {
    public static final double PLUG_KG = 30, MELT_S = 600, HYDRATE_WATER_FRACTION = 0.87;
    public static final double INLET_ABS_PA = 0.7e6, INLET_K = 310, COOLER_K = 308, DISCHARGE_ABS_PA = 7.1e6;
    public static final double LTS_MIN_PA = 1.5e6, LTS_MAX_PA = 6.0e6;
    public static final double CHILL_MIN_K = 238.15, CHILL_MAX_K = COOLER_K, CONDENSING_K = 318.15, CARNOT_FRACTION = 0.55,
            NGL_LATENT_J_KG = 350e3;

    private final GlycolDehydration glycol;
    private double ltsAbsPa = 3.5e6;
    private double chillK = 263.15;
    private double plug;

    public LowTemperatureSeparation(GlycolDehydration glycol) {
        this.glycol = glycol;
    }

    public double ltsPressurePa() {
        return ltsAbsPa;
    }

    public void setLtsPressurePa(double pa) {
        ltsAbsPa = Math.clamp(pa, LTS_MIN_PA, LTS_MAX_PA);
    }

    /** Уставка испарителя пропанового холодильника, К. */
    public double chillK() {
        return chillK;
    }

    public void setChillK(double k) {
        chillK = Math.clamp(k, CHILL_MIN_K, CHILL_MAX_K);
    }

    /** Заполнение пробки, доли; 1 — штуцер перекрыт. */
    public double plug() {
        return plug;
    }

    public void setPlug(double v) {
        plug = Math.clamp(v, 0, 1);
    }

    public boolean blocked() {
        return plug >= 1;
    }

    /**
     * Шаг установки.
     *
     * @param qSm3s      поток газа на входе, нм³/с
     * @param methanolKgS метанол, впрыскиваемый перед штуцером, кг/с
     * @param dt         шаг, с
     */
    public Result process(double qSm3s, double methanolKgS, double dt) {
        double[] x = GasComponent.associatedGas();
        double wIn = WaterContent.saturatedMgPerSm3(INLET_K, INLET_ABS_PA);
        double work = GasCompressor.workJPerSm3(x, INLET_K, INLET_ABS_PA, DISCHARGE_ABS_PA);
        double wCooled = Math.min(wIn, WaterContent.saturatedMgPerSm3(COOLER_K, DISCHARGE_ABS_PA));
        GlycolDehydration.Result dry = glycol.dry(wCooled, COOLER_K, DISCHARGE_ABS_PA);
        double tChill = Math.min(chillK, COOLER_K);
        double tLts = PengRobinson.throttleTwoPhase(x, tChill, DISCHARGE_ABS_PA, ltsAbsPa);
        Flash f = Flash.of(x, tLts, ltsAbsPa);
        // холод: явное тепло газа и конденсация углеводородов в испарителе
        double cp = 0;
        for (GasComponent c : GasComponent.values()) cp += x[c.ordinal()] * c.cpIdealJMolK;
        Flash chilled = Flash.of(x, tChill, DISCHARGE_ABS_PA);
        double condensedKgPerSm3 = (1 - chilled.vapor()) * WaterContent.MOL_PER_SM3 * GasComponent.molarMass(chilled.x()) / 1000;
        double coldJPerSm3 = cp * WaterContent.MOL_PER_SM3 * (COOLER_K - tChill) + condensedKgPerSm3 * NGL_LATENT_J_KG;
        double tEvap = tChill - 5;
        double cop = CARNOT_FRACTION * tEvap / (CONDENSING_K - tEvap);
        // свободная вода и гидраты — в самой холодной и опасной точке: испаритель при p₁ или НТС при p₂
        double freeMg = Math.max(0, Math.max(dry.dryMgSm3() - WaterContent.saturatedMgPerSm3(tChill, DISCHARGE_ABS_PA),
                dry.dryMgSm3() - WaterContent.saturatedMgPerSm3(tLts, ltsAbsPa)));
        double gravity = GasComponent.gravity(x);
        double hydrateChill = Hydrates.formationK(DISCHARGE_ABS_PA, gravity), hydrateLts = Hydrates.formationK(ltsAbsPa, gravity);
        double margin = Math.max(hydrateChill - tChill, hydrateLts - tLts);
        double hydrate = tLts + margin;
        double q = blocked() ? 0 : qSm3s;
        double freeWaterKgS = q * freeMg * 1e-6;
        double wt = freeWaterKgS + methanolKgS > 0 ? 100 * methanolKgS / (freeWaterKgS + methanolKgS) : 0;
        double inhibited = hydrate - Hydrates.methanolDepressionK(wt);
        if (q > 0 && freeWaterKgS > 0 && tLts < inhibited) {
            plug = Math.min(1, plug + freeWaterKgS / HYDRATE_WATER_FRACTION * dt / PLUG_KG);
        } else if (tLts > inhibited || freeWaterKgS <= 0) {
            plug = Math.max(0, plug - dt / MELT_S);
        }
        double waterKgS = q * ((wIn - wCooled) * 1e-6 + dry.waterKgPerSm3()) + freeWaterKgS;
        double nglMolPerSm3 = (1 - f.vapor()) * WaterContent.MOL_PER_SM3;
        return new Result(q * f.vapor(), f.y(), q * nglMolPerSm3 * Flash.liquidM3PerMol(f.x()), q * nglMolPerSm3, f.x(),
                waterKgS, q * (work + (tChill < COOLER_K ? coldJPerSm3 / cop : 0)), q * dry.heatJPerSm3(), tLts, dry.dewPointK(),
                WaterContent.dewPointK(dry.dryMgSm3(), ltsAbsPa), hydrate, inhibited, freeWaterKgS, wt, plug, blocked());
    }

    /**
     * @param salesSm3s       товарный газ после НТС, нм³/с
     * @param salesComposition его состав
     * @param nglM3s          ШФЛУ, м³/с (жидкость при 15 °C)
     * @param nglMolS         ШФЛУ, моль/с
     * @param nglComposition  состав ШФЛУ
     * @param waterKgS        вода, выделенная на установке (охладитель, гликоль, НТС), кг/с
     * @param compressorW     мощность на валу: сжатие газа и холодильный цикл, Вт
     * @param regenerationW   тепло регенерации гликоля, Вт
     * @param ltsK            температура в низкотемпературном сепараторе, К
     * @param dewPointK       точка росы осушенного газа по воде при давлении абсорбера, К
     * @param ltsDewPointK    она же при давлении НТС, К
     * @param hydrateK        температура гидратообразования без ингибитора, приведённая к НТС: T_НТС плюс наибольший
     *                        недогрев до гидратов в испарителе или в НТС, К
     * @param inhibitedK      то же с метанолом, К
     * @param freeWaterKgS    свободная вода в штуцере и НТС, кг/с
     * @param methanolWtPct   концентрация метанола в водной фазе, %
     * @param plug            заполнение гидратной пробки
     * @param blocked         штуцер перекрыт пробкой
     */
    public record Result(double salesSm3s, double[] salesComposition, double nglM3s, double nglMolS, double[] nglComposition,
            double waterKgS, double compressorW, double regenerationW, double ltsK, double dewPointK, double ltsDewPointK,
            double hydrateK, double inhibitedK, double freeWaterKgS, double methanolWtPct, double plug, boolean blocked) {
    }
}
