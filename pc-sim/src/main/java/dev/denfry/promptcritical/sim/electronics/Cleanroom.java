package dev.denfry.promptcritical.sim.electronics;

/**
 * Чистая комната и выход годных (11 §11.5.3).
 *
 * <p><b>Концентрация частиц.</b> Баланс хорошо перемешанной комнаты: подсос наружного воздуха L (неплотности, открытая
 * дверь шлюза), чистый воздух через HEPA Q с эффективностью η = 0,9997 и источники внутри G (люди в комбинезонах —
 * около 10⁵ частиц ≥ 0,5 мкм в минуту, Whyte 2010): C = (C_out·L + G)/(L + Q·η). Наружный воздух цеха — около
 * 3,5·10⁷ частиц/м³ ≥ 0,5 мкм (ISO 9).
 *
 * <p><b>Класс ISO 14644-1.</b> Предел C_n(D) = 10^N·(0,1/D)^2,08 частиц/м³ размером ≥ D мкм, отсюда N по счёту ≥ 0,5 мкм.
 *
 * <p><b>Выход годных.</b> Пуассон Y = e^{−A·D₀}. Убивает кристалл частица, сравнимая с половиной элемента, поэтому
 * D₀ = D_собств + K·C(≥ CD/2), C(≥ d) = C(≥ 0,5)·(0,5/d)^2,08. Калибровка K: в комнате ISO 5 при CD = 1 мкм
 * D₀ ≈ 3–4 см⁻² — порядок фабрик начала 1980-х (Stapper 1983: 1–5 см⁻²). Собственная плотность (дефекты кристалла,
 * литографии) — 0,1 см⁻²; контактная печать изнашивает маску — плюс 0,5 см⁻².
 */
public final class Cleanroom {
    public static final double HEPA_EFFICIENCY = 0.9997, OUTSIDE_PER_M3 = 3.5e7;
    /** Частиц ≥ 0,5 мкм от человека в комбинезоне, 1/ч. */
    public static final double PERSON_PER_H = 6e6;
    /** Калибровка плотности смертельных дефектов, см⁻² на частицу/м³. */
    public static final double K_DEFECT = 1e-3;
    public static final double D_INTRINSIC = 0.1, D_CONTACT_MASK = 0.5;

    private Cleanroom() {
    }

    /** Концентрация ≥ 0,5 мкм внутри, частиц/м³. */
    public static double concentration(double leakM3H, double hepaM3H, double peopleInside) {
        double l = Math.max(0, leakM3H), q = Math.max(0, hepaM3H);
        double den = l + q * HEPA_EFFICIENCY;
        if (den <= 0) return OUTSIDE_PER_M3;
        return Math.min(OUTSIDE_PER_M3, (OUTSIDE_PER_M3 * l + PERSON_PER_H * Math.max(0, peopleInside)) / den);
    }

    /** Класс ISO по счёту ≥ 0,5 мкм (дробный; 5 — бывший класс 100). */
    public static double isoClass(double perM3At05) {
        double c = Math.max(1e-3, perM3At05);
        return Math.log10(c / Math.pow(0.1 / 0.5, 2.08));
    }

    /** Предел ISO-класса {@code n} для частиц ≥ {@code dUm}, частиц/м³. */
    public static double isoLimit(double n, double dUm) {
        return Math.pow(10, n) * Math.pow(0.1 / dUm, 2.08);
    }

    /** Плотность смертельных дефектов, см⁻². */
    public static double defectDensity(double perM3At05, double cdUm, boolean contactMask) {
        double killer = Math.max(0.05, cdUm / 2);
        double c = perM3At05 * Math.pow(0.5 / killer, 2.08);
        return D_INTRINSIC + K_DEFECT * c + (contactMask ? D_CONTACT_MASK : 0);
    }

    /** Выход годных по Пуассону. */
    public static double poissonYield(double areaCm2, double d0) {
        return Math.exp(-Math.max(0, areaCm2) * Math.max(0, d0));
    }

    /** Кристаллов на пластине диаметром {@code dMm} при площади кристалла {@code areaMm2} (с потерями у края). */
    public static int diesPerWafer(double dMm, double areaMm2) {
        if (areaMm2 <= 0) return 0;
        double n = Math.PI * dMm * dMm / 4 / areaMm2 - Math.PI * dMm / Math.sqrt(2 * areaMm2);
        return (int) Math.max(0, Math.floor(n));
    }
}
