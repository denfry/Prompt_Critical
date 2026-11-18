package dev.denfry.promptcritical.sim.gear;

import dev.denfry.promptcritical.sim.battery.BatterySpec;

/**
 * Теплообмен человека с холодным воздухом и электрообогреваемый костюм.
 *
 * <p>Сухие потери тепла — последовательные сопротивления тканей тела, одежды и пограничного слоя воздуха (ASHRAE
 * Fundamentals, гл. 9; ISO 11079): Q = A·(T_ядра − T_возд)/(I_т + I_од + I_в), где I_т = {@value #TISSUE_INSULATION}
 * м²·К/Вт — изоляция тканей при сужении сосудов на холоде (Burton &amp; Edholm, «Man in a Cold Environment», 1955:
 * до ≈ 0,9 кло), 1 кло = 0,155 м²·К/Вт, I_в = 1/(h_к + h_л), h_к = 8,3·√v (вынужденная конвекция, ASHRAE, v ≥ 0,2
 * м/с), h_л = 4,7 Вт/(м²·К). Дыхание уносит 0,0014·M·(34 − T_возд) (ASHRAE, сухая часть). Обмен M = MET·58,15
 * Вт/м²·A. Избыток тепла тело сбрасывает потом и расширением сосудов, нехватка остужает ядро: dT/dt = −ΔQ/(m·c),
 * c = 3470 Дж/(кг·К). Дрожь не учитывается [упрощение: она отодвигает переохлаждение, но не отменяет].
 *
 * <p>Костюм — комбинезон {@value #SUIT_CLO} кло с греющими проводами; терморегулятор даёт ровно недостающую
 * мощность, но не больше {@value #HEATER_MAX_W} Вт [оценка по порядку мощности авиационных костюмов с электрообогревом
 * на 24 В]. Батарея — двадцать ламельных NiCd элементов по 10 А·ч (24 В, 240 Вт·ч, ≈ 11 кг): на сильном морозе её
 * хватает на час-полтора, тяжесть батареи — плата за тепло.
 */
public final class HeatedSuit {
    public static final double TISSUE_INSULATION = 0.12, CLO = 0.155;
    public static final double WORK_CLOTHES_CLO = 0.7, SUIT_CLO = 1.0;
    public static final double HEATER_MAX_W = 160;
    public static final double CORE_C = 37, BODY_HEAT_JKGK = 3470;
    static final double H_RADIATIVE = 4.7;

    private final PowerPack pack;
    private double heaterW;

    public HeatedSuit(BatterySpec spec) {
        this.pack = new PowerPack(spec);
    }

    /** Костюм на 24 В: 20 элементов НК по 10 А·ч. */
    public static HeatedSuit standard() {
        return new HeatedSuit(BatterySpec.nickelCadmiumPocketPlate(20, 10));
    }

    public PowerPack pack() {
        return pack;
    }

    /**
     * Тепловой баланс тела без обогрева, Вт: &gt; 0 — тело теряет больше, чем вырабатывает.
     *
     * @param areaM2 поверхность тела, м²
     * @param met    нагрузка, MET
     * @param clo    изоляция одежды, кло
     */
    public static double deficitW(double areaM2, double met, double airC, double windMs, double clo) {
        double m = met * 58.15 * areaM2;
        double hc = 8.3 * Math.sqrt(Math.max(0.2, windMs));
        double r = TISSUE_INSULATION + clo * CLO + 1 / (hc + H_RADIATIVE);
        double dry = areaM2 * (CORE_C - airC) / r;
        double resp = 0.0014 * m * (34 - airC);
        return dry + resp - m;
    }

    /** Температура ядра после {@code dtS} секунд с тепловым дефицитом {@code deficitW}. */
    public static double coreAfter(double coreC, double deficitW, double massKg, double dtS) {
        double next = coreC - deficitW * dtS / (massKg * BODY_HEAT_JKGK);
        // избыток уходит потом: ядро возвращается к норме, но не выше
        return deficitW < 0 ? Math.max(coreC, Math.min(CORE_C, next)) : next;
    }

    /**
     * Надетый костюм за {@code dtS} секунд: терморегулятор закрывает дефицит {@code deficitW} (посчитанный с
     * изоляцией костюма), насколько хватает батареи.
     *
     * @return отданная телу мощность обогрева, Вт
     */
    public double heat(double dtS, double deficitW, double ambientC) {
        double want = Math.max(0, Math.min(HEATER_MAX_W, deficitW));
        heaterW = pack.supply(dtS, want, ambientC);
        return heaterW;
    }

    public void idle(double dtS, double ambientC) {
        heaterW = 0;
        pack.supply(dtS, 0, ambientC);
    }

    public double heaterW() {
        return heaterW;
    }

    public void restore(double removedAh, double cycles, double tempC) {
        pack.battery().restore(removedAh, cycles, tempC);
    }

    /** Стадия переохлаждения по температуре ядра (швейцарская классификация: 35 / 32 / 28 °C), 0 — нет. */
    public static int hypothermiaStage(double coreC) {
        if (coreC >= 35) return 0;
        if (coreC >= 32) return 1;
        if (coreC >= 28) return 2;
        return 3;
    }
}
