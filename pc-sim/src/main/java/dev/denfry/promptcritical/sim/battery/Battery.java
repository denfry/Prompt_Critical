package dev.denfry.promptcritical.sim.battery;

import java.util.Objects;

/**
 * Аккумуляторная батарея: заряд, напряжение под нагрузкой, саморазряд, старение по циклам и нагрев.
 *
 * <p>Напряжение элемента — модель Tremblay ({@link BatterySpec}): при разряде током I
 * V = E₀ − K·Q′/(Q′ − it) + A·exp(−B·it) − R·I, где Q′ = Q·h·min(1, (I_ref/I)^(k−1)) — полная ёмкость с учётом износа
 * h и закона Пейкерта (при токе больше паспортного батарея садится раньше). При заряде к ЭДС добавляются падение R·|I| и
 * перенапряжение газовыделения V_g·SOC^n; та же доля тока SOC^n уходит на электролиз воды (кислород и водород), а не
 * в активную массу, — отсюда коэффициент заряда больше единицы.
 *
 * <p>Саморазряд — доля ёмкости в сутки, удваивается на каждые 10 К выше 20 °C (правило Вант-Гоффа). Износ — по
 * эквивалентным полным циклам (отданные А·ч / номинальная ёмкость): ёмкость падает линейно до
 * {@link BatterySpec#endOfLife} за {@link BatterySpec#cycleLife} циклов. Нагрев — потери R·I² и газовыделение,
 * охлаждение корпусом; одна тепловая ёмкость.
 */
public final class Battery {
    private final BatterySpec spec;
    /** Отданный заряд it, А·ч (0 — полностью заряжена). */
    private double removedAh;
    /** Эквивалентные полные циклы. */
    private double cycles;
    private double tempC = 20;

    public Battery(BatterySpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    public BatterySpec spec() {
        return spec;
    }

    // ---------------------------------------------------------------- состояние

    /** Доля начальной ёмкости после износа. */
    public double health() {
        return Math.max(spec.endOfLife() * 0.5, 1 - (1 - spec.endOfLife()) * cycles / spec.cycleLife());
    }

    /** Полная ёмкость с учётом износа, А·ч. */
    public double capacityAh() {
        return spec.maxAh() * health();
    }

    /** Степень заряженности 0…1. */
    public double soc() {
        return Math.max(0, 1 - removedAh / capacityAh());
    }

    public double removedAh() {
        return removedAh;
    }

    public double cycles() {
        return cycles;
    }

    public double temperatureC() {
        return tempC;
    }

    /** Восстановить состояние (предмет, сохранение). */
    public void restore(double removedAh, double cycles, double tempC) {
        this.cycles = Math.max(0, cycles);
        this.removedAh = Math.max(0, Math.min(removedAh, capacityAh() * 0.999));
        this.tempC = Double.isFinite(tempC) ? tempC : 20;
    }

    // ---------------------------------------------------------------- напряжение

    /**
     * ЭДС разряженного щелочного элемента без тока, В: модель Tremblay у полного разряда уходит в −∞ (член
     * K·Q/(Q − it)), а на заряде элемент начинает с ≈ 1,1 В [оценка по зарядным кривым NiCd, Linden &amp; Reddy].
     */
    static final double CHARGE_EMF_FLOOR = 1.1;

    /** ЭДС элемента на заряде: кривая Tremblay, но не ниже {@link #CHARGE_EMF_FLOOR}. */
    private double chargeEmf() {
        return Math.max(CHARGE_EMF_FLOOR, cellEmf(removedAh, capacityAh()));
    }

    /** ЭДС элемента без тока при отданном заряде it и полной ёмкости q, В. */
    private double cellEmf(double it, double q) {
        double x = Math.min(it, 0.999 * q);
        return spec.e0() - spec.k() * q / (q - x) + spec.a() * Math.exp(-spec.b() * x);
    }

    /**
     * Полная ёмкость при токе разряда {@code currentA} по закону Пейкерта, А·ч: больше тока — меньше ёмкости. Ниже
     * паспортного тока ёмкость не растёт сверх запасённой в активной массе Q.
     */
    double peukertCapacityAh(double currentA) {
        double q = capacityAh();
        if (currentA <= spec.refCurrentA()) return q;
        return q * Math.pow(spec.refCurrentA() / currentA, spec.peukert() - 1);
    }

    /**
     * Напряжение на зажимах, В: {@code currentA} &gt; 0 — разряд, &lt; 0 — заряд, 0 — без тока. Глубоко разряженная
     * батарея под током проседает до нуля, но не уходит в минус.
     */
    public double terminalVolts(double currentA) {
        if (currentA >= 0) {
            double q = peukertCapacityAh(currentA);
            return Math.max(0, spec.cells() * (cellEmf(removedAh, q) - spec.cellOhm() * currentA));
        }
        double soc = soc();
        double cell = chargeEmf() - spec.cellOhm() * currentA + spec.gasVolts() * Math.pow(soc, spec.gasExponent());
        return spec.cells() * cell;
    }

    /** Ток заряда при заданном напряжении на зажимах (участок постоянного напряжения), А, ≥ 0. */
    public double chargeCurrentAt(double volts) {
        double soc = soc();
        double cell = volts / spec.cells() - chargeEmf() - spec.gasVolts() * Math.pow(soc, spec.gasExponent());
        return spec.cellOhm() > 0 ? Math.max(0, cell / spec.cellOhm()) : 0;
    }

    /** Доля зарядного тока, идущая в активную массу (остальное — газ). */
    public double chargeEfficiency() {
        return 1 - Math.pow(soc(), spec.gasExponent());
    }

    // ---------------------------------------------------------------- шаги

    /**
     * Разряд током {@code currentA} в течение {@code dtS} секунд; износ считается по отданным А·ч.
     *
     * @return выделившееся тепло, Дж
     */
    public double discharge(double dtS, double currentA) {
        if (currentA <= 0 || dtS <= 0) return 0;
        double ah = currentA * dtS / 3600;
        removedAh = Math.min(removedAh + ah, capacityAh() * 0.999);
        cycles += ah / spec.ratedAh();
        return currentA * currentA * spec.cells() * spec.cellOhm() * dtS;
    }

    /**
     * Заряд током {@code currentA} в течение {@code dtS} секунд.
     *
     * @return выделившееся тепло, Дж: R·I² и энергия, ушедшая на газовыделение
     */
    public double charge(double dtS, double currentA) {
        if (currentA <= 0 || dtS <= 0) return 0;
        double eta = chargeEfficiency();
        double volts = terminalVolts(-currentA);
        removedAh = Math.max(0, removedAh - currentA * eta * dtS / 3600);
        double ohmic = currentA * currentA * spec.cells() * spec.cellOhm();
        // газ уходит из открытой батареи: его ток превращает в тепло перенапряжение сверх 1,48 В термонейтрального
        // напряжения разложения воды, остальное уносит газ
        double gas = currentA * (1 - eta) * Math.max(0, volts / spec.cells() - 1.48) * spec.cells();
        return (ohmic + gas) * dtS;
    }

    /** Саморазряд за {@code dtS} секунд при температуре батареи. */
    public void selfDischarge(double dtS) {
        double rate = spec.selfDischargePerDay() * Math.pow(2, (tempC - 20) / 10) / 86400;
        removedAh = Math.min(capacityAh() * 0.999, removedAh + rate * dtS * capacityAh());
    }

    /** Тепловой шаг: тепло {@code heatJ} за {@code dtS} секунд, окружающий воздух {@code ambientC}. */
    public void heat(double dtS, double heatJ, double ambientC) {
        double c = spec.heatCapacityJK(), g = spec.coolingWK();
        // точное решение для постоянной мощности: T → T_окр + P/G с постоянной C/G
        double p = dtS > 0 ? heatJ / dtS : 0;
        double target = ambientC + p / g;
        tempC = target + (tempC - target) * Math.exp(-g * dtS / c);
    }
}
