package dev.denfry.promptcritical.sim.drilling;

/**
 * Аккумуляторная установка ПВО («Koomey», 09 §9.5.7): баллоны с азотной подушкой. Жидкость в баллоне при давлении p
 * по закону Бойля занимает V_ж = V_б(1 − p_пред/p); полезный объём между p_max и p_min —
 * V_б(p_пред/p_min − p_пред/p_max): баллон 41,6 л даёт 20,8 л при p_пред = 7 МПа, p_min = 8,3 МПа, p_max = 21 МПа.
 *
 * <p>Насос подзаряжает установку от предзарядки до максимума за 15 минут (API Std 53 — требование к насосам
 * аккумулятора): подача — полный запас жидкости при p_max за 900 с, мощность — подача × давление / КПД 0,85.
 */
public final class BopAccumulator {
    public static final double BOTTLE_M3 = 0.0416, PRECHARGE_PA = 7e6, MIN_PA = 8.3e6, MAX_PA = 21e6;
    /** Запас над предзарядкой после закрытия всех превенторов без насосов (09 §9.5.7, API Std 53), Па. */
    public static final double API53_MARGIN_PA = 1.4e6;
    public static final double RECHARGE_S = 900, PUMP_EFFICIENCY = 0.85;

    private int bottles;
    private double fluidM3;

    /** Установка из {@code bottles} баллонов, заряженная до максимума. */
    public BopAccumulator(int bottles) {
        this.bottles = Math.max(0, bottles);
        this.fluidM3 = fluidAtPa(MAX_PA);
    }

    /** Полезный объём одного баллона между давлениями, м³. */
    public static double usableM3(double bottleM3, double prechargePa, double minPa, double maxPa) {
        return bottleM3 * (prechargePa / minPa - prechargePa / maxPa);
    }

    public int bottles() {
        return bottles;
    }

    /** Сменить число баллонов (валидатор буровой), жидкость сохраняется в пределах ёмкости. */
    public void setBottles(int n) {
        bottles = Math.max(0, n);
        fluidM3 = Math.min(fluidM3, fluidAtPa(MAX_PA));
    }

    /** Жидкость в баллонах при давлении {@code pa}, м³. */
    public double fluidAtPa(double pa) {
        return pa <= PRECHARGE_PA ? 0 : bottles * BOTTLE_M3 * (1 - PRECHARGE_PA / pa);
    }

    public double fluidM3() {
        return fluidM3;
    }

    /** Давление в системе, Па; без жидкости — давление предзарядки (без баллонов — 0). */
    public double pressurePa() {
        if (bottles == 0) return 0;
        double frac = fluidM3 / (bottles * BOTTLE_M3);
        return PRECHARGE_PA / Math.max(1e-9, 1 - frac);
    }

    /** Можно ли отдать {@code m3}, оставив не меньше {@code minPa}. */
    public boolean canDraw(double m3, double minPa) {
        return bottles > 0 && fluidM3 - m3 >= fluidAtPa(minPa) - 1e-12;
    }

    /** Отдать объём на закрытие или открытие; {@code false} — давления не хватает, ничего не взято. */
    public boolean draw(double m3) {
        if (!canDraw(m3, MIN_PA)) return false;
        fluidM3 -= m3;
        return true;
    }

    /** Подача насоса подзарядки, м³/с. */
    public double pumpRateM3s() {
        return fluidAtPa(MAX_PA) / RECHARGE_S;
    }

    /**
     * Подзарядка за {@code dt} при доле подачи {@code drive} (0..1 — вал или сеть); возвращает потребляемую мощность,
     * Вт.
     */
    public double recharge(double dt, double drive) {
        double room = fluidAtPa(MAX_PA) - fluidM3;
        if (room <= 0 || drive <= 0 || dt <= 0) return 0;
        double q = pumpRateM3s() * Math.min(1, drive);
        double v = Math.min(room, q * dt);
        double p = pressurePa();
        fluidM3 += v;
        return v / dt * Math.max(p, PRECHARGE_PA) / PUMP_EFFICIENCY;
    }

    /**
     * Требование API Std 53: закрыть все превенторы и открыть гидроуправляемую задвижку без насосов, сохранив не
     * меньше p_пред + 1,4 МПа. {@code requiredM3} — сумма объёмов закрытия.
     */
    public boolean meetsApi53(double requiredM3) {
        return bottles > 0 && fluidAtPa(MAX_PA) - fluidAtPa(PRECHARGE_PA + API53_MARGIN_PA) >= requiredM3;
    }

    public void restoreFluid(double m3) {
        fluidM3 = Math.clamp(m3, 0, fluidAtPa(MAX_PA));
    }
}
