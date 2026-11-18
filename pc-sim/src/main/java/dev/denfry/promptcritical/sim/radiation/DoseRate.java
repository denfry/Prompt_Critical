package dev.denfry.promptcritical.sim.radiation;

import java.util.List;

/**
 * Мощность дозы от точечного источника за слоями защиты (Машкович, Кудрявцева, 1995; Chilton, Shultis, Faw, 1984,
 * гл. 3, 10).
 * <ul>
 *   <li>γ: керма в воздухе K̇ = Σ S_i·E_i·(μ_en/ρ)_возд(E_i)·B·e^{−Σμx}/(4πr²). Для многослойной защиты B берётся по
 *   материалу последнего слоя при полной оптической толщине (правило «последнего слоя», Broder); доза в ткани
 *   приравнена к керме в воздухе (Зв ≈ Гр для γ: H*(10)/K_возд = 1,16 при 1 МэВ — в пределах точности модели).</li>
 *   <li>Быстрые нейтроны: φ = S·e^{−ΣΣ_R x}/(4πr²), H = φ·h, h = 385 пЗв·см² (H*(10) для спектра Cf-252 —
 *   спектра деления, ISO 8529-3, ICRP 74).</li>
 * </ul>
 * Расстояния — в метрах, толщины — в сантиметрах; ближе {@value #MIN_R_M} м точка не подходит (размер источника).
 */
public final class DoseRate {
    public static final double MEV_J = 1.602176634e-13, NEUTRON_H_SV_CM2 = 385e-12, MIN_R_M = 0.3;
    /** (μ_en/ρ) воздуха, см²/г, на сетке {@link ShieldMaterial#ENERGY_MEV} (NIST, Hubbell & Seltzer). */
    static final double[] AIR_MUEN = {0.02966, 0.02953, 0.02882, 0.02789, 0.02666, 0.02547, 0.02345};

    /** Слой защиты на пути луча: материал, толщина, см, плотность, г/см³. */
    public record Layer(ShieldMaterial material, double cm, double density) {
        public Layer(ShieldMaterial material, double cm) {
            this(material, cm, material.density);
        }
    }

    private DoseRate() {
    }

    /** (μ_en/ρ) воздуха при энергии, см²/г. */
    public static double airMuEn(double energyMeV) {
        return ShieldMaterial.logLog(ShieldMaterial.ENERGY_MEV, AIR_MUEN, energyMeV);
    }

    /** Мощность кермы γ в воздухе, Гр/ч (для выброса — керма, Гр, если {@code src} — числа частиц). */
    public static double gammaGyH(PointSource src, double rM, List<Layer> layers) {
        double r2 = 4 * Math.PI * Math.pow(Math.max(MIN_R_M, rM) * 100, 2);
        double sum = 0;
        for (int i = 0; i < src.energyMeV().length; i++) {
            double e = src.energyMeV()[i], mfp = 0;
            for (Layer l : layers) mfp += l.material().mu(e, l.density()) * l.cm();
            ShieldMaterial last = layers.isEmpty() ? null : layers.get(layers.size() - 1).material();
            double b = last == null ? 1 : last.buildup(mfp);
            double fluence = src.photons()[i] / r2 * b * Math.exp(-mfp);
            sum += fluence * e * airMuEn(e); // МэВ/г
        }
        return sum * MEV_J * 1000 * 3600;
    }

    /** Мощность эквивалентной дозы быстрых нейтронов, Зв/ч. */
    public static double neutronSvH(PointSource src, double rM, List<Layer> layers) {
        if (src.neutrons() <= 0) return 0;
        double r2 = 4 * Math.PI * Math.pow(Math.max(MIN_R_M, rM) * 100, 2), sigmaX = 0;
        for (Layer l : layers) sigmaX += l.material().removal(l.density()) * l.cm();
        return src.neutrons() / r2 * Math.exp(-sigmaX) * NEUTRON_H_SV_CM2 * 3600;
    }

    /** Полная мощность дозы, Зв/ч. */
    public static double totalSvH(PointSource src, double rM, List<Layer> layers) {
        return gammaGyH(src, rM, layers) + neutronSvH(src, rM, layers);
    }

    /** Доза от выброса частиц (вспышка), Зв: та же формула без перевода в час. */
    public static double burstSv(PointSource burst, double rM, List<Layer> layers) {
        return totalSvH(burst, rM, layers) / 3600;
    }
}
