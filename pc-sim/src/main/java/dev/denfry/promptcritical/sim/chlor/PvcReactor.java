package dev.denfry.promptcritical.sim.chlor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Суспензионная полимеризация винилхлорида в автоклаве с мешалкой (Ullmann, «Poly(Vinyl Chloride)», разд. 3.1;
 * Xie, Hamielec, Wood, Woods, Polymer 32 (1991) 537; Лебедев, 1988). Вода и ВХ 1,2–1,5 : 1, защитный коллоид
 * (поливиниловый спирт), инициатор — пероксидикарбонат. Капля мономера полимеризуется в зерно ПВХ; молекулярную массу
 * (константу Фикентчера K) задаёт только температура — передача цепи на мономер: K 70 при 50 °C, K 57 при 63 °C.
 * Скорость — первый порядок по мономеру при постоянной концентрации радикалов; до ≈ 70 % превращения давление равно
 * упругости пара ВХ (0,7–1,0 МПа), затем свободный мономер исчезает и давление падает — по «падению давления» загрузку
 * заканчивают при 85–90 %, остаточный ВХ сдувают на рекуперацию. Тепло полимеризации 1,6 МДж/кг снимает рубашка.
 * ВХ — канцероген (ангиосаркома печени, 1974): поэтому дегазация смолы и закрытые автоклавы.
 */
public final class PvcReactor {
    public static final double MIN_K = 318.15, MAX_K = 343.15;
    /** Загрузка ВХ на цикл, кг [BALANCE: опытный автоклав ≈ 0,7 м³; промышленные — 20–150 м³]. */
    public static final double CHARGE_KG = 250;
    /** Вода на загрузку, кг (1,3 : 1). */
    public static final double WATER_KG = CHARGE_KG * 1.3;
    /** Окончание загрузки — падение давления. */
    public static final double END_CONVERSION = 0.87, PRESSURE_DROP_AT = 0.70;
    public static final double HEAT_J_KG = 1.6e6;

    public enum Phase { IDLE, CHARGING, REACTING, DONE }

    private double tempK = 330.15, vcmKg, waterKg, conversion;
    private Phase phase = Phase.IDLE;
    private double batchTimeS;

    /** Константа скорости при температуре, 1/с: 87 % за ≈ 6 ч при 57 °C (типовой цикл 5–8 ч). */
    public static double rate(double tempK) {
        double k330 = -Math.log(1 - END_CONVERSION) / (6 * 3600);
        return k330 * Math.exp(90e3 / 8.314462618 * (1 / 330.15 - 1 / tempK));
    }

    /** Константа Фикентчера смолы при температуре полимеризации. */
    public static double kValue(double tempK) {
        return 70 - 1.0 * (tempK - 323.15);
    }

    /** Упругость пара ВХ, МПа: Клаузиус–Клапейрон от 259,4 К; 0,62 МПа при 40 °C и 1,05 при 60 °C (справочно 0,61 и 0,98). */
    public static double vapourMpa(double tempK) {
        return 0.101325 * Math.exp(22.8e3 / 8.314462618 * (1 / 259.4 - 1 / tempK));
    }

    public double tempK() {
        return tempK;
    }

    public void setTempK(double k) {
        if (phase == Phase.REACTING) return; // температуру загрузки меняют до пуска: она задаёт марку
        tempK = Math.clamp(k, MIN_K, MAX_K);
    }

    public Phase phase() {
        return phase;
    }

    public double vcmKg() {
        return vcmKg;
    }

    public double waterKg() {
        return waterKg;
    }

    public double conversion() {
        return conversion;
    }

    /** Давление в автоклаве, МПа. */
    public double pressureMpa() {
        if (phase != Phase.REACTING) return 0.1;
        double p = vapourMpa(tempK);
        if (conversion <= PRESSURE_DROP_AT) return p;
        return p * Math.max(0.3, 1 - (conversion - PRESSURE_DROP_AT) / (1 - PRESSURE_DROP_AT));
    }

    public double batchTimeS() {
        return batchTimeS;
    }

    /** Загрузка: принимает мономер и воду, кг; возвращает принятое {ВХ, вода}. */
    public double[] charge(double vcmOfferKg, double waterOfferKg) {
        if (phase != Phase.IDLE && phase != Phase.CHARGING) return new double[2];
        phase = Phase.CHARGING;
        double v = Math.min(vcmOfferKg, CHARGE_KG - vcmKg), w = Math.min(waterOfferKg, WATER_KG - waterKg);
        vcmKg += Math.max(0, v);
        waterKg += Math.max(0, w);
        return new double[] {Math.max(0, v), Math.max(0, w)};
    }

    public boolean charged() {
        return vcmKg >= CHARGE_KG - 1e-6 && waterKg >= WATER_KG - 1e-6;
    }

    /** Пуск полимеризации: инициатор в автоклав. */
    public boolean start() {
        if (phase != Phase.CHARGING || !charged()) return false;
        phase = Phase.REACTING;
        conversion = 0;
        batchTimeS = 0;
        return true;
    }

    /** Шаг по игровому времени; возвращает тепло полимеризации, Вт. */
    public double step(double gameDtS) {
        if (phase != Phase.REACTING) return 0;
        double before = conversion;
        conversion = 1 - (1 - conversion) * Math.exp(-rate(tempK) * gameDtS);
        batchTimeS += gameDtS;
        if (conversion >= END_CONVERSION) {
            conversion = END_CONVERSION;
            phase = Phase.DONE;
        }
        return gameDtS > 0 ? (conversion - before) * vcmKg * HEAT_J_KG / gameDtS : 0;
    }

    /**
     * Выгрузка: смола, кг, и сдувка непрореагировавшего ВХ на рекуперацию, кг.
     */
    public double[] discharge() {
        if (phase != Phase.DONE) return new double[2];
        double pvc = vcmKg * conversion, recovered = vcmKg - pvc;
        vcmKg = waterKg = conversion = batchTimeS = 0;
        phase = Phase.IDLE;
        return new double[] {pvc, recovered};
    }

    public void save(StateWriter out) {
        out.putDouble("t", tempK);
        out.putDouble("vcm", vcmKg);
        out.putDouble("water", waterKg);
        out.putDouble("x", conversion);
        out.putDouble("time", batchTimeS);
        out.putInt("phase", phase.ordinal());
    }

    public void load(StateReader in) {
        tempK = Math.clamp(in.optDouble("t", 330.15), MIN_K, MAX_K);
        vcmKg = in.optDouble("vcm", 0);
        waterKg = in.optDouble("water", 0);
        conversion = in.optDouble("x", 0);
        batchTimeS = in.optDouble("time", 0);
        phase = Phase.values()[Math.clamp(in.optInt("phase", 0), 0, Phase.values().length - 1)];
    }
}
