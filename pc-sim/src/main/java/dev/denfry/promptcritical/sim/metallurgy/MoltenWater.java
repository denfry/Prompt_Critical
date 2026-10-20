package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Расплав в воду (08 §8.6.5): E_mech = f_FCI·m·c_p·(T_расп − T_s), m — масса в контакте за тик. Мир превращает
 * энергию в {@code DamageRequest(BLAST, priority} {@link #PRIORITY}{@code )} с радиусом {@link #radiusM} (слой
 * physics не зависит от {@code sim.consequence}). Источники: сырой ковш ({@link LadleState#waterContactKg}), течь
 * фурмы конвертера, прорыв горна на мокрый пол.
 */
public final class MoltenWater {
    /** Кипение воды при атмосферном давлении, К. */
    public static final double WATER_SAT_K = 373.15;
    public static final int PRIORITY = 1;
    /** Радиус оценки: приведённое расстояние, м/кг^(1/3) ТНТ (как у взрыва котла); ТНТ, Дж/кг. */
    public static final double SCALED_DISTANCE = 4.5, TNT_J_PER_KG = 4.184e6;

    private MoltenWater() {}

    /** E_mech, Дж; {@code cpJkgK} — c_p жидкого (сталь 820); ниже T_s — 0. */
    public static double mechanicalEnergyJ(double contactKg, double cpJkgK, double meltK, double fci) {
        if (!(contactKg >= 0)) throw new IllegalArgumentException("contactKg");
        return fci * contactKg * cpJkgK * Math.max(0, meltK - WATER_SAT_K);
    }

    /** E_mech стали в контакте с водой по конфигу. */
    public static double steelEnergyJ(double contactKg, double meltK, SteelmakingConfig cfg) {
        return mechanicalEnergyJ(contactKg, Converter.STEEL_CP, meltK, cfg.fciFraction());
    }

    /** Физический радиус для заявки, м. */
    public static double radiusM(double energyJ) {
        return SCALED_DISTANCE * Math.cbrt(Math.max(0, energyJ) / TNT_J_PER_KG);
    }
}
