package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.nuclide.Nuclide;
import dev.denfry.promptcritical.sim.nuclide.NuclideVector;

/**
 * Выгорание топливной сборки (канала T1, ТВС T2a) по её доле мощности. На деление расходуется 1 + α ядер делящегося
 * нуклида (α — отношение захвата к делению: U-235 0,17, Pu-239 0,36), захваты без деления дают U-236 и Pu-240,
 * Pu-239 нарабатывается с коэффициентом воспроизводства {@code conversion} из U-238.
 *
 * <p>МОКС: с массивами Pu-241 и Pu-242 Pu-241 делится наравне с U-235 и Pu-239 (α = 362/1012 ≈ 0,36, его захват даёт
 * Pu-242), а Pu-240 захватывает нейтроны и переходит в Pu-241. Доли делений и захватов — по тепловым сечениям 2200 м/с
 * (Mughabghab, «Atlas of Neutron Resonances», 2006: σ_f U-235 585 б, Pu-239 748 б, Pu-241 1012 б; σ_γ Pu-240 289,5 б,
 * Pu-241 362 б) [упрощение: резонанс Pu-240 1,06 эВ в спектре ЛВР поднимает его захват в несколько раз — расход Pu-240
 * здесь занижен]. β-распад Pu-241 (T½ 14,3 года) за игровую кампанию — доли процента, не ведётся. Без этих массивов
 * (старая сигнатура) — урановое топливо как раньше: Pu-241 и Pu-242 при выгораниях игровой кампании — доли процента
 * плутония.
 *
 * <p>Массивы — состояние сборок вызывающего реактора: начальный ТМ, U-235, U-238, U-236, Pu-239, Pu-240, продукты
 * деления (кг) и выгорание (МВт·сут/кг ТМ).
 */
public final class FuelDepletion {
    public static final double ALPHA_U5 = 0.17, ALPHA_PU9 = 0.36, SIGMA_F5 = 585, SIGMA_F9 = 748;
    /** Pu-241: σ_f, б, и α = σ_γ/σ_f = 362/1012; захват Pu-240 σ_γ, б (2200 м/с, Mughabghab 2006). */
    public static final double SIGMA_F1 = 1012, ALPHA_PU1 = 362.0 / 1012, SIGMA_C0 = 289.5;
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
        deplete(i, pw, gameDt, conversion, hm0, u5, u8, u6, pu9, pu0, null, null, fp, burnup);
    }

    /**
     * Шаг выгорания сборки {@code i} с Pu-241 и Pu-242 (МОКС); {@code pu1} или {@code pu2} null — без них, как в
     * старой сигнатуре.
     */
    public static void deplete(int i, double pw, double gameDt, double conversion, double[] hm0, double[] u5, double[] u8,
            double[] u6, double[] pu9, double[] pu0, double[] pu1, double[] pu2, double[] fp, double[] burnup) {
        if (pw <= 0 || gameDt <= 0 || hm0[i] <= 0) return;
        boolean mox = pu1 != null && pu2 != null;
        double fissions = pw / PointKinetics.FISSION_ENERGY * gameDt;
        double w5 = u5[i] * SIGMA_F5 / Nuclide.U235.molarMass(), w9 = pu9[i] * SIGMA_F9 / Nuclide.PU239.molarMass(),
                w1 = mox ? pu1[i] * SIGMA_F1 / Nuclide.PU241.molarMass() : 0;
        double w = w5 + w9 + w1;
        double f9 = w > 0 ? w9 / w : 0, f1 = w > 0 ? w1 / w : 0, f5 = 1 - f9 - f1;
        double mol = fissions / AVOGADRO;
        double d5 = mol * f5 * (1 + ALPHA_U5) * Nuclide.U235.molarMass() / 1000,
                d9 = mol * f9 * (1 + ALPHA_PU9) * Nuclide.PU239.molarMass() / 1000;
        double made9 = conversion * mol * (f5 * (1 + ALPHA_U5) + f9 * (1 + ALPHA_PU9) + f1 * (1 + ALPHA_PU1))
                * Nuclide.PU239.molarMass() / 1000;
        // захваты в Pu-240, моль: на одно деление N₀σ_γ0 / Σ N_k σ_f,k (поток тот же); не больше, чем ядер есть
        double c0 = mox && w > 0 ? Math.min(mol * pu0[i] * SIGMA_C0 / Nuclide.PU240.molarMass() / w,
                pu0[i] * 1000 / Nuclide.PU240.molarMass()) : 0;
        u5[i] = Math.max(0, u5[i] - d5);
        u6[i] += mol * f5 * ALPHA_U5 * 236.046 / 1000;
        pu0[i] = Math.max(0, pu0[i] + (mol * f9 * ALPHA_PU9 - c0) * Nuclide.PU240.molarMass() / 1000);
        pu9[i] = Math.max(0, pu9[i] - d9 + made9);
        if (mox) {
            double d1 = mol * f1 * (1 + ALPHA_PU1) * Nuclide.PU241.molarMass() / 1000;
            pu1[i] = Math.max(0, pu1[i] - d1 + c0 * Nuclide.PU241.molarMass() / 1000);
            pu2[i] += mol * f1 * ALPHA_PU1 * Nuclide.PU242.molarMass() / 1000;
        }
        u8[i] = Math.max(0, u8[i] - made9 * Nuclide.U238.molarMass() / Nuclide.PU239.molarMass());
        fp[i] += mol * (f5 * Nuclide.U235.molarMass() + f9 * Nuclide.PU239.molarMass() + f1 * Nuclide.PU241.molarMass()) / 1000;
        burnup[i] += pw * gameDt / 86400 / 1e6 / hm0[i];
    }

    /** Средний состав сборок на 1 т исходного ТМ — вход модели ячейки. */
    public static NuclideVector averageFuel(double[] hm0, double[] u5, double[] u8, double[] pu9, double[] fp, double fallbackEnrichment) {
        return averageFuel(hm0, u5, u8, pu9, null, null, null, fp, fallbackEnrichment);
    }

    /**
     * Средний состав с Pu-240, Pu-241 и Pu-242 (null — не учитывать): Pu-240 и Pu-242 — поглотители, Pu-241 — делящийся.
     * Модель ячейки берёт их сечения и доли делений, а с ними — β смеси.
     */
    public static NuclideVector averageFuel(double[] hm0, double[] u5, double[] u8, double[] pu9, double[] pu0, double[] pu1,
            double[] pu2, double[] fp, double fallbackEnrichment) {
        double s5 = 0, s8 = 0, s9 = 0, s0 = 0, s1 = 0, s2 = 0, sf = 0, hm = 0;
        for (int i = 0; i < hm0.length; i++) {
            if (hm0[i] <= 0) continue;
            s5 += u5[i];
            s8 += u8[i];
            s9 += pu9[i];
            if (pu0 != null) s0 += pu0[i];
            if (pu1 != null) s1 += pu1[i];
            if (pu2 != null) s2 += pu2[i];
            sf += fp[i];
            hm += hm0[i];
        }
        if (hm <= 0) return NuclideVector.uranium(1000, fallbackEnrichment);
        double k = 1e6 / hm; // граммы на тонну исходного ТМ
        double[] a = new double[Nuclide.values().length];
        a[Nuclide.U235.ordinal()] = s5 * k / Nuclide.U235.molarMass() * AVOGADRO;
        a[Nuclide.U238.ordinal()] = s8 * k / Nuclide.U238.molarMass() * AVOGADRO;
        a[Nuclide.PU239.ordinal()] = s9 * k / Nuclide.PU239.molarMass() * AVOGADRO;
        a[Nuclide.PU240.ordinal()] = s0 * k / Nuclide.PU240.molarMass() * AVOGADRO;
        a[Nuclide.PU241.ordinal()] = s1 * k / Nuclide.PU241.molarMass() * AVOGADRO;
        a[Nuclide.PU242.ordinal()] = s2 * k / Nuclide.PU242.molarMass() * AVOGADRO;
        a[Nuclide.LFP.ordinal()] = sf * k / Nuclide.LFP.molarMass() * AVOGADRO / 2;
        return NuclideVector.ofAtoms(a);
    }
}
