package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.nuclide.Nuclide;
import dev.denfry.promptcritical.sim.nuclide.NuclideVector;

/**
 * Выгорание топливной сборки (канала T1, ТВС T2a) по её доле мощности. На деление расходуется 1 + α ядер делящегося
 * нуклида (α — отношение захвата к делению: U-235 0,17, Pu-239 0,36), захваты без деления дают U-236 и Pu-240,
 * Pu-239 нарабатывается с коэффициентом воспроизводства {@code conversion} из U-238. Pu-241 и Pu-242 не ведутся —
 * при выгораниях игровой кампании это доли процента плутония.
 *
 * <p>Массивы — состояние сборок вызывающего реактора: начальный ТМ, U-235, U-238, U-236, Pu-239, Pu-240, продукты
 * деления (кг) и выгорание (МВт·сут/кг ТМ).
 */
public final class FuelDepletion {
    public static final double ALPHA_U5 = 0.17, ALPHA_PU9 = 0.36, SIGMA_F5 = 585, SIGMA_F9 = 748;
    private static final double AVOGADRO = NuclideVector.AVOGADRO;

    private FuelDepletion() {
    }

    /**
     * Шаг выгорания сборки {@code i}.
     *
     * @param pw         тепловая мощность делений в сборке, Вт
     * @param gameDt     игровое время шага, с
     * @param conversion КВ: ядер Pu-239 на поглощённое в делящихся ядро
     */
    public static void deplete(int i, double pw, double gameDt, double conversion, double[] hm0, double[] u5, double[] u8,
            double[] u6, double[] pu9, double[] pu0, double[] fp, double[] burnup) {
        if (pw <= 0 || gameDt <= 0 || hm0[i] <= 0) return;
        double fissions = pw / PointKinetics.FISSION_ENERGY * gameDt;
        double w5 = u5[i] * SIGMA_F5 / Nuclide.U235.molarMass(), w9 = pu9[i] * SIGMA_F9 / Nuclide.PU239.molarMass();
        double f9 = w5 + w9 > 0 ? w9 / (w5 + w9) : 0;
        double mol = fissions / AVOGADRO;
        double d5 = mol * (1 - f9) * (1 + ALPHA_U5) * Nuclide.U235.molarMass() / 1000,
                d9 = mol * f9 * (1 + ALPHA_PU9) * Nuclide.PU239.molarMass() / 1000;
        double made9 = conversion * mol * ((1 - f9) * (1 + ALPHA_U5) + f9 * (1 + ALPHA_PU9)) * Nuclide.PU239.molarMass() / 1000;
        u5[i] = Math.max(0, u5[i] - d5);
        u6[i] += mol * (1 - f9) * ALPHA_U5 * 236.046 / 1000;
        pu0[i] += mol * f9 * ALPHA_PU9 * Nuclide.PU240.molarMass() / 1000;
        pu9[i] = Math.max(0, pu9[i] - d9 + made9);
        u8[i] = Math.max(0, u8[i] - made9 * Nuclide.U238.molarMass() / Nuclide.PU239.molarMass());
        fp[i] += mol * (1 - f9) * Nuclide.U235.molarMass() / 1000 + mol * f9 * Nuclide.PU239.molarMass() / 1000;
        burnup[i] += pw * gameDt / 86400 / 1e6 / hm0[i];
    }

    /** Средний состав сборок на 1 т исходного ТМ — вход модели ячейки. */
    public static NuclideVector averageFuel(double[] hm0, double[] u5, double[] u8, double[] pu9, double[] fp, double fallbackEnrichment) {
        double s5 = 0, s8 = 0, s9 = 0, sf = 0, hm = 0;
        for (int i = 0; i < hm0.length; i++) {
            if (hm0[i] <= 0) continue;
            s5 += u5[i];
            s8 += u8[i];
            s9 += pu9[i];
            sf += fp[i];
            hm += hm0[i];
        }
        if (hm <= 0) return NuclideVector.uranium(1000, fallbackEnrichment);
        double k = 1e6 / hm; // граммы на тонну исходного ТМ
        double[] a = new double[Nuclide.values().length];
        a[Nuclide.U235.ordinal()] = s5 * k / Nuclide.U235.molarMass() * AVOGADRO;
        a[Nuclide.U238.ordinal()] = s8 * k / Nuclide.U238.molarMass() * AVOGADRO;
        a[Nuclide.PU239.ordinal()] = s9 * k / Nuclide.PU239.molarMass() * AVOGADRO;
        a[Nuclide.LFP.ordinal()] = sf * k / Nuclide.LFP.molarMass() * AVOGADRO / 2;
        return NuclideVector.ofAtoms(a);
    }
}
