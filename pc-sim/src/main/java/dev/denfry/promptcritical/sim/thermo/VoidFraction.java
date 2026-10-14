package dev.denfry.promptcritical.sim.thermo;

/**
 * Объёмное паросодержание α по расходному паросодержанию x (01 §1.3.5, §1.3.6).
 * Гомогенная модель — для массы и энергии узла; drift-flux Зубера–Финдлея — для
 * обратных связей по реактивности и уровня. Единицы — СИ.
 */
public final class VoidFraction {
    /** Конфиг {@code hydraulics.void_model}. */
    public enum Model { DRIFT_FLUX, HEM }

    /** Параметр распределения: пузырьковый/снарядный режим, круглые каналы (Ishii 1977). */
    public static final double C0 = 1.13;
    public static final double GRAVITY = 9.80665;

    private VoidFraction() {}

    /** α по выбранной модели; σ — поверхностное натяжение, Н/м; massFlux — G, кг/(м²·с). */
    public static double of(Model model, double x, double massFlux, double rhoF, double rhoG, double sigma) {
        return switch (model) {
            case HEM -> homogeneous(x, rhoF, rhoG);
            case DRIFT_FLUX -> driftFlux(x, massFlux, rhoF, rhoG, driftVelocity(sigma, rhoF, rhoG));
        };
    }

    /** α_HEM = [1 + (1 − x)/x · ρ_g/ρ_f]⁻¹ (скольжение S = 1). */
    public static double homogeneous(double x, double rhoF, double rhoG) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x / (x + (1 - x) * rhoG / rhoF);
    }

    /** Скорость дрейфа V_gj = 1,41·[σ g (ρ_f − ρ_g)/ρ_f²]^{1/4}, м/с (Harmathy 1960, Zuber–Findlay). */
    public static double driftVelocity(double sigma, double rhoF, double rhoG) {
        return 1.41 * Math.sqrt(Math.sqrt(sigma * GRAVITY * Math.max(rhoF - rhoG, 0) / (rhoF * rhoF)));
    }

    /**
     * Зубер–Финдлей: α = x / (C₀[x + (1 − x)ρ_g/ρ_f] + ρ_g V_gj/G). При G → 0 стремится
     * к x·G/(ρ_g V_gj) — пузыри всплывают через неподвижную жидкость. Знак G не важен.
     * Корреляция пузырькового и снарядного режимов; x ≥ 1 — чистый пар, α = 1.
     */
    public static double driftFlux(double x, double massFlux, double rhoF, double rhoG, double vgj) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double g = Math.abs(massFlux);
        double den = C0 * (x + (1 - x) * rhoG / rhoF) * g + rhoG * vgj;
        if (den <= 0) return homogeneous(x, rhoF, rhoG); // G = 0 и V_gj = 0: дрейфа нет
        return Math.min(x * g / den, 1.0);
    }

    /**
     * Паросодержание двухфазного слоя под уровнем узла {@code SEPARATED} (§1.3.6):
     * α_pool = j_g/(C₀ j_g + V_gj), j_g — приведённая скорость пара, м/с.
     */
    public static double pool(double jg, double vgj) {
        if (jg <= 0) return 0;
        return jg / (C0 * jg + vgj);
    }
}
