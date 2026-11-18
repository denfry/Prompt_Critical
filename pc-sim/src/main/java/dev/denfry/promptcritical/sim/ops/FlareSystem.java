package dev.denfry.promptcritical.sim.ops;

/**
 * Факельная система (13 §13.4.3): горение на оголовке с паровым кольцом, дымность, полнота сгорания, тепловое
 * излучение на землю и выбросы.
 *
 * <ul>
 *   <li>Бездымность: Φ = max(0, 1 − s/s_req), s — пар к углеводородам, кг/кг; s_req для насыщенных лёгких газов
 *   0,25–0,5, до 1 для олефинов (API 521 [сверить]).</li>
 *   <li>Полнота сгорания: η = 0,98 при s ≤ 3·s_req, иначе 0,98 − 0,1·(s/s_req − 3) (переподача пара); ниже
 *   {@value #ETA_MIN} — срыв пламени. Без дежурных горелок газ уходит несгоревшим (P1).</li>
 *   <li>Излучение, точечный источник API 521: K = τ·F·Q/(4πD²), Q = ṁ·LHV, F = {@value #RADIANT_FRACTION}
 *   ({@code ops.flare_radiant_fraction}), τ = 1.</li>
 *   <li>Выбросы: CO₂ = η·ṁ_C·44/12, SO₂ = η·ṁ_H₂S·64/34, несгоревший метан = (1 − η)·ṁ_CH₄.</li>
 * </ul>
 */
public final class FlareSystem {
    public static final double RADIANT_FRACTION = 0.25, ETA_MAX = 0.98, ETA_MIN = 0.6;
    /** Пороги излучения для персонала по API 521, Вт/м² (§13.4.3). */
    public static final double K_CONTINUOUS = 1580, K_EMERGENCY_MIN = 4730, K_ESCAPE_30S = 6310, K_SECONDS = 9460, K_IGNITION = 12_500;
    public static final int PILOTS = 3;

    /** Состояние горения за шаг. */
    public record FlareState(double heatReleaseW, double smokeIndex, double combustionEfficiency, boolean flameOut,
            double co2KgS, double so2KgS, double ch4UnburntKgS) {
        public static final FlareState IDLE = new FlareState(0, 0, 0, false, 0, 0, 0);
    }

    /** Состав сбросного газа: массовые доли метана и H₂S, доля углерода в массе, теплота сгорания, нужный пар. */
    public record Gas(double ch4Mass, double h2sMass, double carbonMass, double lhvJkg, double steamRequired) {
        /** Попутный газ: метан с тяжёлыми, 2 % H₂S по объёму (как у аминовой очистки 09). */
        public static final Gas ASSOCIATED = new Gas(0.80, 0.04, 0.72, 47.5e6, 0.3);
    }

    private FlareState last = FlareState.IDLE;

    private FlareSystem() {
    }

    public static FlareSystem create() {
        return new FlareSystem();
    }

    public FlareState last() {
        return last;
    }

    /** Индекс дымности по отношению пар/углеводороды. */
    public static double smokeIndex(double steamRatio, double required) {
        return Math.max(0, 1 - steamRatio / required);
    }

    /** Полнота сгорания; ниже {@value #ETA_MIN} — срыв (значение не обрезается, чтобы его было видно). */
    public static double combustionEfficiency(double steamRatio, double required) {
        double r = steamRatio / required;
        return r <= 3 ? ETA_MAX : ETA_MAX - 0.1 * (r - 3);
    }

    /**
     * Шаг: расход сбросного газа {@code flowKgS}, пар на кольцо {@code steamKgS}, горящих дежурных горелок
     * {@code pilotsLit}.
     */
    public FlareState step(double flowKgS, double steamKgS, Gas gas, int pilotsLit) {
        if (!(flowKgS > 0)) return last = FlareState.IDLE;
        double s = steamKgS / flowKgS;
        double eta = combustionEfficiency(s, gas.steamRequired());
        boolean out = pilotsLit <= 0 || eta < ETA_MIN;
        if (out) eta = 0;
        double phi = out ? 0 : smokeIndex(s, gas.steamRequired());
        double q = eta * flowKgS * gas.lhvJkg();
        return last = new FlareState(q, phi, eta, out, eta * flowKgS * gas.carbonMass() * 44 / 12,
                eta * flowKgS * gas.h2sMass() * 64 / 34, (1 - eta) * flowKgS * gas.ch4Mass());
    }

    /** Плотность теплового потока на расстоянии {@code distanceM} от центра пламени, Вт/м². */
    public static double radiantFluxWm2(double heatReleaseW, double distanceM) {
        double d = Math.max(1, distanceM);
        return RADIANT_FRACTION * heatReleaseW / (4 * Math.PI * d * d);
    }

    /** Расстояние, на котором поток падает до {@code fluxWm2}, м. */
    public static double distanceForFlux(double heatReleaseW, double fluxWm2) {
        return Math.sqrt(RADIANT_FRACTION * heatReleaseW / (4 * Math.PI * fluxWm2));
    }

    /** Урон игроку без огнезащитного костюма в секунду по таблице §13.4.3. */
    public static double damagePerSecond(double fluxWm2) {
        if (fluxWm2 >= K_SECONDS) return 3;
        if (fluxWm2 >= K_ESCAPE_30S) return 1;
        if (fluxWm2 >= K_EMERGENCY_MIN) return 0.5;
        return 0;
    }
}
