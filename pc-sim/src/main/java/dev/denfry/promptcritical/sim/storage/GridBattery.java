package dev.denfry.promptcritical.sim.storage;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Сетевая батарея {@code grid_battery_rack} (15 §15.7.1): стеллажи свинцово-кислотных элементов, модуль
 * {@value #MODULE_KWH} кВт·ч, до {@value #MAX_MODULES} модулей за инвертором.
 *
 * <ul>
 *   <li>Разряд по Пейкерту (показатель {@value #PEUKERT}) от десятичасового режима, заряд и разряд с КПД
 *       {@value #CHARGE_EFFICIENCY} и {@value #DISCHARGE_EFFICIENCY}: КПД цикла около 0,78.</li>
 *   <li>Мощность одного модуля ограничена током C/4 (заряд — C/10, полностью заряженная — плавающий подзаряд).</li>
 *   <li>Старение (R22): эквивалентные циклы по глубине разряда, ресурс {@value #CYCLE_LIFE} полных циклов;
 *       глубокие разряды старят быстрее (показатель Вёлера {@value #WOEHLER}). Ёмкость падает до 80 % к концу
 *       ресурса и дальше.</li>
 *   <li>При заряде выше {@value #GASSING_SOC} SOC идёт газовыделение водорода — нужна вентиляция.</li>
 * </ul>
 */
public final class GridBattery {
    public static final double MODULE_KWH = 250;
    public static final int MAX_MODULES = 40;
    public static final double PEUKERT = 1.15, CHARGE_EFFICIENCY = 0.88, DISCHARGE_EFFICIENCY = 0.89;
    public static final double CYCLE_LIFE = 1000, WOEHLER = 1.3, GASSING_SOC = 0.9;
    /**
     * Водород при заряде выше {@link #GASSING_SOC}: кг на Дж, ушедший в газ. Фарадей: 1 А·ч = 3600/96485 моль
     * электронов → 0,0376 г H₂ на элемент 2 В.
     */
    public static final double H2_KG_PER_J = 3600.0 / 96485 / 2 * 2.016e-3 / (2 * 3600);
    /** Самый мелкий учитываемый цикл по глубине разряда. */
    static final double MIN_DOD = 0.02;

    private final int modules;
    private double energyJ;
    private double cycles;
    /** Глубина текущего полуцикла разряда: SOC в начале разряда. */
    private double dischargeStartSoc = Double.NaN;
    private double h2Kg;

    public GridBattery(int modules) {
        this.modules = Math.max(1, Math.min(MAX_MODULES, modules));
        this.energyJ = capacityJ() * 0.5;
    }

    /** Батарея из сохранения мира: модулей, степень заряда, эквивалентные циклы. */
    public static GridBattery restore(int modules, double soc, double cycles) {
        GridBattery g = new GridBattery(modules);
        g.cycles = Double.isFinite(cycles) ? Math.max(0, cycles) : 0;
        g.energyJ = (Double.isFinite(soc) ? Math.max(0, Math.min(1, soc)) : 0.5) * g.capacityJ();
        return g;
    }

    /** Та же батарея с другим числом модулей: степень заряда и старение сохраняются. */
    public GridBattery withModules(int n) {
        GridBattery g = new GridBattery(n);
        g.cycles = cycles;
        g.energyJ = soc() * g.capacityJ();
        g.dischargeStartSoc = dischargeStartSoc;
        return g;
    }

    public int modules() {
        return modules;
    }

    /** Ёмкость с учётом старения, Дж. */
    public double capacityJ() {
        return modules * MODULE_KWH * 3.6e6 * health();
    }

    /** Доля исходной ёмкости, 0,5…1. */
    public double health() {
        return Math.max(0.5, 1 - 0.2 * cycles / CYCLE_LIFE);
    }

    public double soc() {
        return Math.max(0, Math.min(1, energyJ / capacityJ()));
    }

    public double cycles() {
        return cycles;
    }

    /** Водород, выделенный с прошлого опроса, кг (вызывающий отводит его вентиляцией и обнуляет). */
    public double takeHydrogenKg() {
        double h = h2Kg;
        h2Kg = 0;
        return h;
    }

    /** Наибольшая мощность разряда на стороне постоянного тока, Вт. */
    public double maxDischargeW() {
        return soc() <= 0.05 ? 0 : modules * MODULE_KWH * 1e3 / 4;
    }

    /** Наибольшая мощность заряда, Вт: C/10, у полной батареи — подзаряд. */
    public double maxChargeW() {
        double c10 = modules * MODULE_KWH * 1e3 / 10;
        return soc() >= 0.999 ? 0 : soc() > GASSING_SOC ? c10 * (1 - soc()) / (1 - GASSING_SOC) : c10;
    }

    /**
     * Шаг: {@code dcW} на стороне постоянного тока (плюс — разряд, минус — заряд).
     *
     * @return мощность, которую батарея действительно дала (плюс) или приняла (минус), Вт
     */
    public double step(double dt, double dcW) {
        if (!(dt > 0) || !Double.isFinite(dcW)) return 0;
        double socBefore = soc();
        double p;
        if (dcW > 0) {
            p = Math.min(dcW, maxDischargeW());
            double p10 = modules * MODULE_KWH * 1e3 / 10;
            double drawn = p / DISCHARGE_EFFICIENCY * dt * (p > p10 ? Math.pow(p / p10, PEUKERT - 1) : 1);
            if (drawn > energyJ) {
                p *= energyJ / drawn;
                drawn = energyJ;
            }
            if (Double.isNaN(dischargeStartSoc)) dischargeStartSoc = socBefore;
            energyJ -= drawn;
        } else {
            p = -Math.min(-dcW, maxChargeW());
            double in = -p * CHARGE_EFFICIENCY * dt;
            if (soc() > GASSING_SOC) h2Kg += -p * (1 - CHARGE_EFFICIENCY) * dt * H2_KG_PER_J;
            closeHalfCycle(socBefore);
            energyJ = Math.min(capacityJ(), energyJ + in);
        }
        return p;
    }

    /** Разряд кончился: эквивалентные циклы по глубине (Вёлер). */
    private void closeHalfCycle(double socNow) {
        if (Double.isNaN(dischargeStartSoc)) return;
        double dod = dischargeStartSoc - socNow;
        dischargeStartSoc = Double.NaN;
        if (dod >= MIN_DOD) cycles += Math.pow(dod, WOEHLER);
    }

    public void save(StateWriter out) {
        out.putDouble("energy_j", energyJ);
        out.putDouble("cycles", cycles);
        out.putDouble("start_soc", Double.isNaN(dischargeStartSoc) ? -1 : dischargeStartSoc);
    }

    public void load(StateReader in) {
        double c = in.optDouble("cycles", 0);
        cycles = Double.isFinite(c) ? Math.max(0, c) : 0;
        double e = in.optDouble("energy_j", capacityJ() * 0.5);
        energyJ = Double.isFinite(e) ? Math.max(0, Math.min(capacityJ(), e)) : capacityJ() * 0.5;
        double s = in.optDouble("start_soc", -1);
        dischargeStartSoc = s >= 0 && s <= 1 ? s : Double.NaN;
    }
}
