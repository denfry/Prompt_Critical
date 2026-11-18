package dev.denfry.promptcritical.sim.hazard;

/**
 * Взрыв облака топливно-воздушной смеси (09 §9.11.7) — упрощённая мульти-энергия TNO (van den Berg, 1985):
 * <ol>
 *   <li>в заряд идёт только объём горючей смеси в загромождённой зоне (доля непустых блоков в кубе 5×5×5 не ниже
 *   {@value #CONGESTION_THRESHOLD}); незагромождённая часть сгорает пожаром-вспышкой без ударной волны;</li>
 *   <li>энергия заряда E = {@value #CHARGE_J_PER_M3} Дж/м³ · V_загр;</li>
 *   <li>класс силы по загромождению: 0,3–0,4 → 4 (0,1 бар), 0,4–0,6 → 6 (0,5 бар), больше 0,6 или трёхстороннее
 *   ограничение → 8 (2 бар); детонация, класс 10 — только водород, этилен и ацетилен в сильном загромождении
 *   [BALANCE: соответствие загромождения классу — инженерная оценка];</li>
 *   <li>в {@code DamageBudget} идёт E·f_blast: 4 → 0,02, 6 → 0,1, 8 → 0,3, 10 → 0,5.</li>
 * </ol>
 * Эквивалентная формулировка — ТНТ-эквивалент W = η·M·ΔHc/4,184 МДж/кг (η = 0,01 открыто … 0,1 сильно загромождено).
 */
public final class VapourCloudExplosion {
    public static final double CHARGE_J_PER_M3 = 3.5e6;
    public static final double CONGESTION_THRESHOLD = 0.3;
    /** Пороги избыточного давления, кПа: стёкла; лёгкие конструкции и урон игроку; шкафы и аппараты; кирпичные стены. */
    public static final double GLASS_KPA = 7, LIGHT_KPA = 20, EQUIPMENT_KPA = 35, BRICK_KPA = 70;

    private VapourCloudExplosion() {
    }

    /** Класс силы взрыва TNO по средней доле загромождения. */
    public static int strengthClass(double congestion, boolean threeSidedConfinement, Flammable gas) {
        if (congestion < CONGESTION_THRESHOLD) return 0;
        if (congestion > 0.6 || threeSidedConfinement) return gas.detonable && congestion > 0.6 ? 10 : 8;
        if (congestion >= 0.4) return 6;
        return 4;
    }

    /** Доля энергии заряда в механическое разрушение. */
    public static double blastFraction(int strengthClass) {
        return switch (strengthClass) {
            case 4 -> 0.02;
            case 6 -> 0.1;
            case 8 -> 0.3;
            case 10 -> 0.5;
            default -> 0;
        };
    }

    /** Пик избыточного давления у заряда, бар (класс 10 — детонация, давление не задано: возвращаем 20 [моё]). */
    public static double peakOverpressureBar(int strengthClass) {
        return switch (strengthClass) {
            case 4 -> 0.1;
            case 6 -> 0.5;
            case 8 -> 2.0;
            case 10 -> 20.0;
            default -> 0;
        };
    }

    public static double chargeEnergyJ(double congestedVolumeM3) {
        return CHARGE_J_PER_M3 * Math.max(0, congestedVolumeM3);
    }

    /** Механическая энергия для {@code DamageBudget}, Дж. */
    public static double mechanicalEnergyJ(double congestedVolumeM3, int strengthClass) {
        return chargeEnergyJ(congestedVolumeM3) * blastFraction(strengthClass);
    }

    /** ТНТ-эквивалент облака, кг. */
    public static double tntEquivalentKg(double eta, double massKg, Flammable gas) {
        return eta * massKg * gas.lhvJkg / 4.184e6;
    }
}
