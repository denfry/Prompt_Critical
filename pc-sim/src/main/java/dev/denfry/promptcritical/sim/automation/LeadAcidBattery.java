package dev.denfry.promptcritical.sim.automation;

/**
 * Стационарная свинцово-кислотная батарея оперативного тока: {@link #CELLS} элементов на 24 В.
 * <ul>
 *   <li>ЭДС элемента от степени заряда: 1,95 В разряженного, 2,12 В заряженного (плотность электролита 1,10–1,28),
 *   внутреннее сопротивление растёт к концу разряда;</li>
 *   <li>разряд — закон Пейкерта: при токе I сверх десятичасового I₁₀ = C₁₀/10 ёмкость снимается быстрее,
 *   ΔQ = I·Δt·(I/I₁₀)^(k−1), k = {@link #PEUKERT} (1,1–1,3 у стационарных);</li>
 *   <li>конечное напряжение 1,75 В/эл.: ниже батарею отключают, иначе пластины сульфатируются;</li>
 *   <li>подзаряд от выпрямителя — ток до I₁₀ при 2,25 В/эл., кулоновский КПД {@link #CHARGE_EFFICIENCY};
 *   саморазряд ≈ 3 % в месяц.</li>
 * </ul>
 * Время — физическое, секунды.
 */
public final class LeadAcidBattery {
    public static final int CELLS = 12;
    public static final double PEUKERT = 1.25, CHARGE_EFFICIENCY = 0.9, CUTOFF_V_CELL = 1.75, FLOAT_V_CELL = 2.25;
    static final double SELF_DISCHARGE_PER_S = 0.03 / (30 * 86_400.0);

    private final double capacityAh;
    private double chargeAh;
    private boolean cutOff;

    /** Батарея ёмкостью {@code capacityAh} (десятичасовой режим), заряженная полностью. */
    public LeadAcidBattery(double capacityAh) {
        if (!(capacityAh > 0)) throw new IllegalArgumentException("capacityAh");
        this.capacityAh = capacityAh;
        this.chargeAh = capacityAh;
    }

    public double capacityAh() {
        return capacityAh;
    }

    /** Степень заряда, 0…1. */
    public double soc() {
        return chargeAh / capacityAh;
    }

    /** ЭДС элемента, В. */
    public double cellOpenCircuitV() {
        return 1.95 + 0.17 * soc();
    }

    /** Внутреннее сопротивление батареи, Ом: 0,5 мОм на элемент при 100 А·ч (∝ 1/C), у разряженной вдвое больше. */
    public double internalOhm() {
        return CELLS * 0.0005 * 100 / capacityAh * (2 - soc());
    }

    /** Напряжение на зажимах при токе разряда {@code currentA} (&lt; 0 — заряд), В; 0 — отключена по напряжению. */
    public double terminalV(double currentA) {
        if (cutOff) return 0;
        return CELLS * cellOpenCircuitV() - currentA * internalOhm();
    }

    /**
     * Разряд током {@code currentA} в течение {@code dtS}: снимает ёмкость по Пейкерту; ниже 1,75 В/эл. под током
     * отключается до заряда.
     */
    public void discharge(double currentA, double dtS) {
        double i = Math.max(0, currentA), i10 = capacityAh / 10;
        double drawn = i * dtS / 3600 * (i > i10 ? Math.pow(i / i10, PEUKERT - 1) : 1);
        chargeAh = Math.max(0, chargeAh - drawn - chargeAh * SELF_DISCHARGE_PER_S * dtS);
        if (CELLS * cellOpenCircuitV() - i * internalOhm() < CELLS * CUTOFF_V_CELL || chargeAh <= 0) cutOff = true;
    }

    /** Подзаряд от выпрямителя в течение {@code dtS}: ток до I₁₀, к 2,25 В/эл. спадает; снимает отключение. */
    public double charge(double dtS) {
        double headroom = CELLS * (FLOAT_V_CELL - cellOpenCircuitV());
        double i = Math.max(0, Math.min(capacityAh / 10, headroom / internalOhm()));
        chargeAh = Math.min(capacityAh, chargeAh + CHARGE_EFFICIENCY * i * dtS / 3600);
        if (soc() > 0.2) cutOff = false;
        return i;
    }

    public boolean cutOff() {
        return cutOff;
    }

    /** Восстановить из сохранения. */
    public void restore(double charge, boolean cut) {
        chargeAh = Math.max(0, Math.min(capacityAh, charge));
        cutOff = cut;
    }

    public double chargeAh() {
        return chargeAh;
    }
}
