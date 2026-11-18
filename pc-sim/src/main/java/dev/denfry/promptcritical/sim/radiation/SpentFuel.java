package dev.denfry.promptcritical.sim.radiation;

import java.util.EnumMap;

/**
 * Наработка продуктов деления и активации в топливе к моменту выгрузки (Benedict–Pigford–Levi, гл. 2, 7; Glasstone,
 * Sesonske, гл. 9; ORIGEN-подобная оценка без цепочек захвата). Деления — по выгоранию: 1 МВт·сут = 8,64·10¹⁰ Дж,
 * 200 МэВ на деление. Время облучения — выгорание, делённое на удельную мощность. Активность продукта с выходом y:
 * A = F·y·(1 − e^{−λT}) при скорости делений F — короткоживущие насыщаются, долгоживущие копятся линейно.
 *
 * <p>Кумулятивные выходы деления U-235 тепловыми нейтронами (ENDF/B-VII, JEFF-3.1): Cs-137 6,19 %, Sr-90 5,78 %,
 * I-131 2,89 %, Zr-95 6,50 %, Ru-106 0,40 %, Kr-85 0,286 % (основное состояние, после изомерного ветвления Kr-85m). Nb-95 и Y-90 к выгрузке — в равновесии с родителем. Cs-134 рождается
 * захватом нейтрона на стабильном Cs-133 и растёт как квадрат выгорания: A(Cs-134)/A(Cs-137) ≈ BU/40 (МВт·сут/кг) —
 * эмпирическое соотношение по измерениям ТВС ВВЭР и PWR (≈ 1 при 40 МВт·сут/кг). Co-60 — активация стали
 * конструкций ТВС: ≈ 3·10¹³ Бк на 0,46 т ТМ при 40 МВт·сут/кг [порядок по PNNL-6906]; у металлического блочка
 * графитового котла стали нет.
 */
public final class SpentFuel {
    public static final double J_PER_MWD = 8.64e10, MEV_PER_FISSION = 200;
    public static final double Y_CS137 = 0.0619, Y_SR90 = 0.0578, Y_I131 = 0.0289, Y_ZR95 = 0.0650, Y_RU106 = 0.0040, Y_KR85 = 0.00286;
    /** Co-60 активации стали на кг ТМ при 40 МВт·сут/кг, Бк. */
    public static final double CO60_BQ_PER_KG_AT_40 = 3e13 / 460;
    /**
     * Актиниды при выгрузке, кг на кг ТМ при опорном выгорании {@value #ACTINIDE_REF_BU} МВт·сут/кг — обратным
     * пересчётом из состава ОЯТ через 5 лет выдержки (02 §2.8.1: Pu 11,5 кг/т с Pu-238 2,7 % и Pu-241 12,5 %, Am 0,6 кг/т,
     * Cm 0,06 кг/т): Pu-238 0,31, Pu-241 1,83, Am-241 0,21 (0,6 минус врастание из Pu-241 за 5 лет), Cm-244 0,073 кг/т.
     * Зависимость от выгорания: Pu-241 — линейно, Pu-238 и Am-241 — квадрат, Cm-244 — четвёртая степень (цепочка
     * из четырёх захватов) [оценка по форме ORIGEN-зависимостей].
     */
    public static final double ACTINIDE_REF_BU = 45, PU238_AT_REF = 0.31e-3, PU241_AT_REF = 1.83e-3, AM241_AT_REF = 0.21e-3,
            CM244_AT_REF = 0.073e-3;
    private static final double AVOGADRO = 6.02214076e23;

    private SpentFuel() {
    }

    /** Делений за кампанию. */
    public static double fissions(double heavyMetalKg, double burnupMWdPerKg) {
        return heavyMetalKg * burnupMWdPerKg * J_PER_MWD / (MEV_PER_FISSION * DoseRate.MEV_J);
    }

    /**
     * Активности при выгрузке.
     *
     * @param specificPowerWPerG удельная мощность при облучении, Вт/г ТМ (ВВЭР ≈ 38, графитовый котёл — доли)
     * @param steelStructure     есть ли стальные конструкции (ТВС), дающие Co-60
     */
    public static ActivityInventory discharge(double heavyMetalKg, double burnupMWdPerKg, double specificPowerWPerG, double densityGcc,
            boolean steelStructure) {
        double total = fissions(heavyMetalKg, burnupMWdPerKg);
        double powerW = heavyMetalKg * 1000 * Math.max(1e-9, specificPowerWPerG);
        double irradiationS = heavyMetalKg * burnupMWdPerKg * J_PER_MWD / powerW;
        double rate = total / irradiationS;
        EnumMap<Emitter, Double> a = new EnumMap<>(Emitter.class);
        put(a, Emitter.CS137, rate, Y_CS137, irradiationS);
        put(a, Emitter.SR90, rate, Y_SR90, irradiationS);
        a.put(Emitter.Y90, a.get(Emitter.SR90));
        put(a, Emitter.I131, rate, Y_I131, irradiationS);
        put(a, Emitter.ZR95, rate, Y_ZR95, irradiationS);
        // Nb-95 при длительном облучении — переходное равновесие с Zr-95
        double l1 = Emitter.ZR95.lambda(), l2 = Emitter.NB95.lambda();
        a.put(Emitter.NB95, a.get(Emitter.ZR95) * l2 / (l2 - l1) * (1 - Math.exp(-(l2 - l1) * irradiationS)));
        put(a, Emitter.RU106, rate, Y_RU106, irradiationS);
        put(a, Emitter.KR85, rate, Y_KR85, irradiationS);
        a.put(Emitter.CS134, a.get(Emitter.CS137) * burnupMWdPerKg / 40);
        if (steelStructure) a.put(Emitter.CO60, CO60_BQ_PER_KG_AT_40 * heavyMetalKg * burnupMWdPerKg / 40);
        // актиниды: тепло и нейтроны выдержанного ОЯТ (Pu-238, Cm-244, Am-241 — около четверти тепла через 5 лет)
        double r = burnupMWdPerKg / ACTINIDE_REF_BU;
        a.put(Emitter.PU238, bq(Emitter.PU238, 238.05, PU238_AT_REF * r * r * heavyMetalKg));
        a.put(Emitter.PU241, bq(Emitter.PU241, 241.06, PU241_AT_REF * r * heavyMetalKg));
        a.put(Emitter.AM241, bq(Emitter.AM241, 241.06, AM241_AT_REF * r * r * heavyMetalKg));
        a.put(Emitter.CM244, bq(Emitter.CM244, 244.06, CM244_AT_REF * r * r * r * r * heavyMetalKg));
        return new ActivityInventory(a, heavyMetalKg, densityGcc);
    }

    /** Активность массы {@code kg} нуклида с молярной массой {@code gPerMol}, Бк. */
    static double bq(Emitter e, double gPerMol, double kg) {
        return e.lambda() * kg * 1000 / gPerMol * AVOGADRO;
    }

    /**
     * Наработка за шаг облучения при постоянной скорости делений (блочки T0 в канале, 12 §12.0.2): сначала распад с
     * накоплением дочерних, затем рождение продуктов деления A += F·y·(1 − e^{−λΔt}) — точно для постоянной F. Cs-134 и
     * Co-60 не рождаются: выгорание блочков — доли МВт·сут/т, стали в них нет.
     *
     * @param fissionsPerS делений в предмете, 1/с
     */
    public static ActivityInventory irradiate(ActivityInventory before, double fissionsPerS, double dtS) {
        ActivityInventory d = before.decayed(dtS);
        if (!(fissionsPerS > 0) || !(dtS > 0)) return d;
        EnumMap<Emitter, Double> a = new EnumMap<>(Emitter.class);
        a.putAll(d.activities());
        add(a, Emitter.CS137, fissionsPerS, Y_CS137, dtS);
        add(a, Emitter.SR90, fissionsPerS, Y_SR90, dtS);
        add(a, Emitter.I131, fissionsPerS, Y_I131, dtS);
        add(a, Emitter.ZR95, fissionsPerS, Y_ZR95, dtS);
        add(a, Emitter.RU106, fissionsPerS, Y_RU106, dtS);
        add(a, Emitter.KR85, fissionsPerS, Y_KR85, dtS);
        return new ActivityInventory(a, d.massKg(), d.densityGcc());
    }

    private static void add(EnumMap<Emitter, Double> a, Emitter e, double fissionRate, double yield, double dtS) {
        a.merge(e, fissionRate * yield * -Math.expm1(-e.lambda() * dtS), Double::sum);
    }

    private static void put(EnumMap<Emitter, Double> a, Emitter e, double fissionRate, double yield, double irradiationS) {
        a.put(e, fissionRate * yield * (1 - Math.exp(-e.lambda() * irradiationS)));
    }
}
