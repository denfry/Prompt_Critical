package dev.denfry.promptcritical.sim.radiation;

import java.util.ArrayList;
import java.util.List;

/**
 * Мощность дозы от точечного источника за слоями защиты (Машкович, Кудрявцева, 1995; Chilton, Shultis, Faw, 1984,
 * гл. 3, 10).
 * <ul>
 *   <li>γ: керма в воздухе K̇ = Σ S_i·E_i·(μ_en/ρ)_возд(E_i)·B·e^{−Σμx}/(4πr²). Для многослойной защиты B берётся по
 *   материалу последнего слоя при полной оптической толщине (правило «последнего слоя», Broder); доза в ткани
 *   приравнена к керме в воздухе (Зв ≈ Гр для γ: H*(10)/K_возд = 1,16 при 1 МэВ — в пределах точности модели).</li>
 *   <li>Быстрые нейтроны: φ = S·e^{−ΣΣ_R x}/(4πr²), H = φ·h, h = 385 пЗв·см² (H*(10) для спектра Cf-252 —
 *   спектра деления, ISO 8529-3, ICRP 74). Без замедлителя на луче железо и свинец выводят вдвое хуже: нейтроны в
 *   них рассеиваются неупруго, но остаются в потоке (04b §4b.4.3, «свинец плохо защищает от нейтронов»); с
 *   водородсодержащими слоями множитель растёт линейно до 1 при {@value #NO_HYDROGEN_CM} см.</li>
 *   <li>Вторичные γ захвата (04b §4b.4.3): выведенные на луче нейтроны S·(1 − e^{−τ}) рождают фотоны по таблице того
 *   материала, где выведено больше всего, в точке, где τ набирает половину полной; дальше их ослабляет остаток защиты.
 *   Для сферической оболочки вокруг источника поток снаружи от источника на оболочке — тот же S/(4πr²), поэтому
 *   расстояние берётся полное.</li>
 * </ul>
 * Расстояния — в метрах, толщины — в сантиметрах; ближе {@value #MIN_R_M} м точка не подходит (размер источника).
 */
public final class DoseRate {
    public static final double MEV_J = 1.602176634e-13, NEUTRON_H_SV_CM2 = 385e-12, MIN_R_M = 0.3;
    /** (μ_en/ρ) воздуха, см²/г, на сетке {@link ShieldMaterial#ENERGY_MEV} (NIST, Hubbell & Seltzer). */
    static final double[] AIR_MUEN = {0.02966, 0.02953, 0.02882, 0.02789, 0.02666, 0.02547, 0.02345, 0.01870};
    /**
     * Без замедлителя на луче тяжёлые материалы выводят нейтроны с множителем {@value #NO_HYDROGEN_FACTOR}; он растёт
     * линейно с длиной водородсодержащих слоёв до 1 при {@value #NO_HYDROGEN_CM} см (тонкие стержни ПЭ в стенке
     * контейнера работают частично, а не никак).
     */
    public static final double NO_HYDROGEN_CM = 20, NO_HYDROGEN_FACTOR = 0.5;

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

    /** Оптическая толщина каждого слоя для быстрых нейтронов (с поправкой на отсутствие замедлителя). */
    public static double[] neutronTau(List<Layer> layers) {
        double moderatorCm = 0;
        for (Layer l : layers) if (l.material().moderating) moderatorCm += l.cm();
        double heavy = NO_HYDROGEN_FACTOR + (1 - NO_HYDROGEN_FACTOR) * Math.min(1, moderatorCm / NO_HYDROGEN_CM);
        double[] tau = new double[layers.size()];
        for (int i = 0; i < tau.length; i++) {
            Layer l = layers.get(i);
            tau[i] = l.material().removal(l.density()) * l.cm() * (l.material().moderating ? 1 : heavy);
        }
        return tau;
    }

    /** Мощность эквивалентной дозы быстрых нейтронов, Зв/ч. */
    public static double neutronSvH(PointSource src, double rM, List<Layer> layers) {
        if (src.neutrons() <= 0) return 0;
        double r2 = 4 * Math.PI * Math.pow(Math.max(MIN_R_M, rM) * 100, 2), sigmaX = 0;
        for (double t : neutronTau(layers)) sigmaX += t;
        return src.neutrons() / r2 * Math.exp(-sigmaX) * NEUTRON_H_SV_CM2 * 3600;
    }

    /** Мощность кермы вторичных γ захвата нейтронов в защите, Гр/ч. */
    public static double captureGammaGyH(PointSource src, double rM, List<Layer> layers) {
        if (src.neutrons() <= 0 || layers.isEmpty()) return 0;
        double[] tau = neutronTau(layers);
        double total = 0;
        int main = 0;
        for (int i = 0; i < tau.length; i++) {
            total += tau[i];
            if (tau[i] > tau[main]) main = i;
        }
        if (total <= 0) return 0;
        PointSource secondary = layers.get(main).material().captureGammas(src.neutrons() * -Math.expm1(-total));
        if (secondary.empty()) return 0;
        List<Layer> rest = restAfterHalf(layers, tau, total);
        return gammaGyH(secondary, rM, rest);
    }

    /**
     * Источник, эквивалентный {@code src} за собственной защитой {@code layers} (стенка контейнера, стена камеры):
     * фотоны ослаблены с накоплением, нейтроны — выведением, к линиям добавлены γ захвата в защите, ослабленные её
     * остатком. Дальше его можно ставить в мир как точечный и вести лучом через блоки.
     */
    public static PointSource behind(PointSource src, List<Layer> layers) {
        if (layers.isEmpty()) return src;
        double[] e = src.energyMeV(), p = src.photons().clone();
        for (int i = 0; i < p.length; i++) {
            double mfp = 0;
            for (Layer l : layers) mfp += l.material().mu(e[i], l.density()) * l.cm();
            p[i] *= layers.get(layers.size() - 1).material().buildup(mfp) * Math.exp(-mfp);
        }
        double[] tau = neutronTau(layers);
        double total = 0;
        int main = 0;
        for (int i = 0; i < tau.length; i++) {
            total += tau[i];
            if (tau[i] > tau[main]) main = i;
        }
        PointSource out = new PointSource(e, p, src.neutrons() * Math.exp(-total));
        if (src.neutrons() <= 0 || total <= 0) return out;
        PointSource secondary = layers.get(main).material().captureGammas(src.neutrons() * -Math.expm1(-total));
        return secondary.empty() ? out : out.plus(behind(secondary, restAfterHalf(layers, tau, total)));
    }

    /** Слои за точкой, где τ набирает половину {@code total}. */
    private static List<Layer> restAfterHalf(List<Layer> layers, double[] tau, double total) {
        List<Layer> rest = new ArrayList<>();
        double acc = 0;
        for (int i = 0; i < tau.length; i++) {
            Layer l = layers.get(i);
            if (!rest.isEmpty()) rest.add(l);
            else if (acc + tau[i] >= total / 2 && tau[i] > 0)
                rest.add(new Layer(l.material(), l.cm() * (acc + tau[i] - total / 2) / tau[i], l.density()));
            acc += tau[i];
        }
        return rest;
    }

    /** Полная мощность дозы: γ источника, нейтроны и γ захвата в защите, Зв/ч. */
    public static double totalSvH(PointSource src, double rM, List<Layer> layers) {
        return gammaGyH(src, rM, layers) + neutronSvH(src, rM, layers) + captureGammaGyH(src, rM, layers);
    }

    /** Доза от выброса частиц (вспышка), Зв: та же формула без перевода в час. */
    public static double burstSv(PointSource burst, double rM, List<Layer> layers) {
        return totalSvH(burst, rM, layers) / 3600;
    }
}
