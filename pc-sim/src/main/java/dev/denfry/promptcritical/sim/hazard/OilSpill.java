package dev.denfry.promptcritical.sim.hazard;

/**
 * Разлив нефти на воде (09 §9.11.9, 14 §14.5): растекание по Фэю, испарение по Фингасу, обводнение («мусс»), сбор
 * бонами, скиммерами и сорбентом. Одна запись — одно пятно ({@code SpillRecord} 14 §14.5.1).
 * <ul>
 *   <li>Фэй (14 §14.5.2): r = min(k₁(ΔgVt²)^{1/4}, k₂(ΔgV²t^{3/2}/ν^{1/2})^{1/6}, k₃(σ²t³/(ρ²ν))^{1/4}),
 *   k = 1,14 / 1,45 / 2,30 [сверить], ν = 10⁻⁶ м²/с, σ = 0,02 Н/м; конечная площадь 10⁵·V^{3/4} м².</li>
 *   <li>Фингас (14 §14.5.3): %Ev = (0,165·D₁₈₀ + 0,045(T − 15))·ln t, t в минутах, не больше D₁₈₀ + 5 %.</li>
 *   <li>Обводнение: вода в эмульсии растёт до 70 % за сутки [BALANCE], линейно.</li>
 *   <li>Бон держит, если нормальная к нему скорость воды u·sin θ ≤ 0,35 м/с (ITOPF [сверить]); скиммер собирает
 *   Q_ном·h/(h + 2 мм) [BALANCE]; сорбент — 15 кг нефти на 1 кг [BALANCE].</li>
 * </ul>
 */
public final class OilSpill {
    public static final double K1 = 1.14, K2 = 1.45, K3 = 2.30, NU_WATER = 1e-6, SPREADING_N_M = 0.02, G = 9.81;
    public static final double WATER_DENSITY = 1000, SEA_WATER_DENSITY = 1025;
    /** Снос пятна ветром: доля скорости ветра. */
    public static final double WIND_DRIFT = 0.03;
    public static final double BOOM_MAX_NORMAL_MS = 0.35;
    public static final double SKIMMER_H50_M = 0.002;
    public static final double SORBENT_KG_PER_KG = 15;
    public static final double MOUSSE_MAX_WATER = 0.7, MOUSSE_TIME_S = 86_400;
    /** Полупериод естественного выветривания берега, сут (env.shore_half_life_days). */
    public static final double SHORE_HALF_LIFE_DAYS = 30;
    /** Видимые блоки: до 64 жидких на суше, до 256 плёнки на воде (09 §9.11.9). */
    public static final int MAX_FLUID_BLOCKS = 64, MAX_SLICK_BLOCKS = 256;

    private final double oilDensity, d180Pct, initialKg;
    private final double waterDensity;
    private double ageS, evaporatedKg, recoveredKg, burnedKg;

    /**
     * @param initialKg  масса пролитой нефти
     * @param oilDensity плотность нефти, кг/м³
     * @param d180Pct    доля, выкипающая до 180 °C, % (из разгонки ИТК)
     * @param sea        морская вода (иначе пресная)
     */
    public OilSpill(double initialKg, double oilDensity, double d180Pct, boolean sea) {
        if (!(initialKg > 0 && oilDensity > 0 && d180Pct >= 0)) throw new IllegalArgumentException("spill");
        this.initialKg = initialKg;
        this.oilDensity = oilDensity;
        this.d180Pct = d180Pct;
        this.waterDensity = sea ? SEA_WATER_DENSITY : WATER_DENSITY;
    }

    /** Восстановление из сохранения. */
    public static OilSpill restore(double initialKg, double oilDensity, double d180Pct, boolean sea, double ageS, double evaporatedKg,
            double recoveredKg, double burnedKg) {
        OilSpill s = new OilSpill(initialKg, oilDensity, d180Pct, sea);
        s.ageS = ageS;
        s.evaporatedKg = evaporatedKg;
        s.recoveredKg = recoveredKg;
        s.burnedKg = burnedKg;
        return s;
    }

    /** Радиус пятна по Фэю через {@code tS} секунд для объёма {@code vM3}, м. */
    public static double fayRadiusM(double vM3, double delta, double tS) {
        if (vM3 <= 0 || tS <= 0) return 0;
        double rGi = K1 * Math.pow(delta * G * vM3 * tS * tS, 0.25);
        double rGv = K2 * Math.pow(delta * G * vM3 * vM3 * Math.pow(tS, 1.5) / Math.sqrt(NU_WATER), 1.0 / 6);
        double rSt = K3 * Math.pow(SPREADING_N_M * SPREADING_N_M * tS * tS * tS / (WATER_DENSITY * WATER_DENSITY * NU_WATER), 0.25);
        return Math.min(rGi, Math.min(rGv, rSt));
    }

    /** Конечная площадь пятна, м². */
    public static double maxAreaM2(double vM3) {
        return 1e5 * Math.pow(Math.max(0, vM3), 0.75);
    }

    /** Доля испарившейся нефти по Фингасу через {@code minutes} при температуре {@code tC}. */
    public static double evaporatedFraction(double d180Pct, double tC, double minutes) {
        return Math.clamp(fingasPct(d180Pct, tC, minutes), 0, d180Pct + 5) / 100;
    }

    /**
     * Формула Фингаса без ограничения, %. Пример T-14-09 (36,0 % за сутки при D₁₈₀ = 30 %) больше предела
     * D₁₈₀ + 5 % из того же §14.5.3; в игре действует предел.
     */
    public static double fingasPct(double d180Pct, double tC, double minutes) {
        if (minutes <= 1) return 0;
        return (0.165 * d180Pct + 0.045 * (tC - 15)) * Math.log(minutes);
    }

    /** Бон держит пятно при течении {@code currentMs} под углом {@code thetaRad} к линии бона. */
    public static boolean boomHolds(double currentMs, double thetaRad) {
        return Math.abs(currentMs * Math.sin(thetaRad)) <= BOOM_MAX_NORMAL_MS;
    }

    /** Наибольший угол бона к течению, при котором он ещё держит, рад. */
    public static double maxBoomAngleRad(double currentMs) {
        return currentMs <= BOOM_MAX_NORMAL_MS ? Math.PI / 2 : Math.asin(BOOM_MAX_NORMAL_MS / currentMs);
    }

    /** Эффективность олеофильного скиммера на плёнке толщиной {@code hM}. */
    public static double skimmerEfficiency(double hM) {
        return hM <= 0 ? 0 : hM / (hM + SKIMMER_H50_M);
    }

    // ------------------------------------------------------------------ состояние пятна

    /** Шаг {@code dt} секунд при температуре воды/воздуха {@code tC}. */
    public void advance(double dt, double tC) {
        if (dt <= 0) return;
        ageS += dt;
        double target = initialKg * evaporatedFraction(d180Pct, tC, ageS / 60);
        // испаряется только то, что ещё на воде
        evaporatedKg = Math.min(Math.max(evaporatedKg, target), evaporatedKg + Math.max(0, remainingKg()));
    }

    public double ageS() {
        return ageS;
    }

    public double initialKg() {
        return initialKg;
    }

    public double evaporatedKg() {
        return evaporatedKg;
    }

    public double recoveredKg() {
        return recoveredKg;
    }

    public double burnedKg() {
        return burnedKg;
    }

    public double oilDensity() {
        return oilDensity;
    }

    public double d180Pct() {
        return d180Pct;
    }

    public boolean sea() {
        return waterDensity > WATER_DENSITY;
    }

    /** Нефть на воде, кг. */
    public double remainingKg() {
        return Math.max(0, initialKg - evaporatedKg - recoveredKg - burnedKg);
    }

    public double volumeM3() {
        return remainingKg() / oilDensity;
    }

    /** Площадь пятна сейчас, м² (растекается по исходному объёму; сбор не стягивает пятно — для этого боны). */
    public double areaM2() {
        if (remainingKg() <= 0) return 0;
        double v0 = initialKg / oilDensity, r = fayRadiusM(v0, (waterDensity - oilDensity) / waterDensity, ageS);
        return Math.min(Math.PI * r * r, maxAreaM2(v0));
    }

    /** Толщина плёнки на площади {@code areaM2} (площадь может ограничить бон), м. */
    public double thicknessM(double areaM2) {
        return areaM2 <= 0 ? 0 : volumeM3() / areaM2;
    }

    /** Доля воды в собираемой эмульсии. */
    public double waterFraction() {
        return MOUSSE_MAX_WATER * Math.min(1, ageS / MOUSSE_TIME_S);
    }

    /** Убрать нефть со сбором (скиммер, сорбент); возвращает собранную нефть, кг. */
    public double recover(double kg) {
        double got = Math.clamp(kg, 0, remainingKg());
        recoveredKg += got;
        return got;
    }

    /** Выгорание при поджоге пятна. */
    public double burn(double kg) {
        double got = Math.clamp(kg, 0, remainingKg());
        burnedKg += got;
        return got;
    }

    /** Видимые блоки плёнки: min(A/1 м², 256). */
    public int slickBlocks(double areaM2) {
        return (int) Math.min(MAX_SLICK_BLOCKS, Math.ceil(areaM2));
    }
}
