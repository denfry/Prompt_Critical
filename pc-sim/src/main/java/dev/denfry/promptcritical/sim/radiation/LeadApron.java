package dev.denfry.promptcritical.sim.radiation;

/**
 * Просвинцованный фартук 0,5 мм Pb-эквивалента (04b §4b.7.3): пропускание узкого пучка T = e^{−(μ/ρ)·ρ·t}. Хорош только
 * против мягкого излучения (рентген, Am-241): на G0 пропускает 4 %, на Cs-137 — 94 %, на Co-60 — 97 %. Носимая броня
 * в моде нарочно не защищает от жёсткого γ больше чем на несколько процентов. Тяжёлый: 6 кг, замедляет.
 * Массовые коэффициенты ослабления свинца — NIST XCOM (Hubbell & Seltzer), со скачком K-края ниже 0,1 МэВ не считаемся.
 */
public final class LeadApron {
    public static final double THICKNESS_CM = 0.05, MASS_KG = 6, LEAD_DENSITY = 11.35;
    static final double[] E_MEV = {0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.6, 0.8, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0};
    static final double[] MU_RHO = {5.549, 2.014, 0.9985, 0.4030, 0.2323, 0.1614, 0.1248, 0.0887, 0.0710, 0.0588, 0.0522,
            0.0461, 0.0423, 0.0420, 0.0427, 0.0436};

    private LeadApron() {
    }

    /** Пропускание фартука для фотонов энергии {@code energyMeV}. */
    public static double transmission(double energyMeV) {
        double e = Math.min(Math.max(energyMeV, E_MEV[0]), E_MEV[E_MEV.length - 1]);
        return Math.exp(-ShieldMaterial.logLog(E_MEV, MU_RHO, e) * LEAD_DENSITY * THICKNESS_CM);
    }
}
