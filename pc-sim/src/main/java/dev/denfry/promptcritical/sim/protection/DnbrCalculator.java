package dev.denfry.promptcritical.sim.protection;

import dev.denfry.promptcritical.sim.thermal.HeatTransferCorrelations;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Расчётная DNBR на измерениях (01 §1.11.4): физика §1.4.4 по горячему каналу, но входы — показания датчиков,
 * сдвинутые на погрешности в опасную сторону: P − δP, G(1 − δ_G), T_in + δT, N(1 + δ_N).
 *
 * <p>Форма F_z(u) — косинус со средним 1 и максимумом F_z посередине:
 * f(u) = 1 + c[cos π(u − ½) − 2/π], c = (F_z − 1)/(1 − 2/π). Смещение пика по аксиальному офсету не
 * моделируется: детекторы отдают сразу F_z. Без детекторов — консервативный F_z модели.
 */
public final class DnbrCalculator {
    private static final double TWO_OVER_PI = 2 / Math.PI;
    private static final double P_MIN = 1e5, P_MAX = 21e6;

    private final HotChannelModel model;
    private final WaterProperties water;
    private final double dP, dG, dT, dN;
    private final double[] satBuf = new double[17];
    private final SaturationState satState = new SaturationState(); // буфер: вычислитель не потокобезопасен

    public DnbrCalculator(HotChannelModel model, ProtectionConfig cfg, WaterProperties water) {
        this.model = model;
        this.water = water;
        this.dP = cfg.dnbrUncertaintyPPa();
        this.dG = cfg.dnbrUncertaintyFlow();
        this.dT = cfg.dnbrUncertaintyTinK();
        this.dN = cfg.dnbrUncertaintyPower();
    }

    public HotChannelModel model() {
        return model;
    }

    /**
     * MDNBR по горячему каналу. NaN — нет обязательного входа (вычислитель в {@code FAULT}); +∞ — нет мощности.
     *
     * @param pressure      давление, Па
     * @param inletTemp     T холодной нитки, К
     * @param flowFrac      расход, доля номинала
     * @param powerFrac     мощность, доля номинала
     * @param axialPeaking  F_z по детекторам; NaN — консервативный
     */
    public double calculate(double pressure, double inletTemp, double flowFrac, double powerFrac, double axialPeaking) {
        if (Double.isNaN(pressure) || Double.isNaN(inletTemp) || Double.isNaN(flowFrac) || Double.isNaN(powerFrac))
            return Double.NaN;
        double n = Math.max(0, powerFrac) * (1 + dN);
        if (n <= 0) return Double.POSITIVE_INFINITY;
        double p = Math.max(P_MIN, Math.min(P_MAX, pressure - dP));
        double g = model.nominalMassFlux() * Math.max(0, flowFrac) * (1 - dG);
        double fz = Double.isNaN(axialPeaking) ? model.conservativeAxialPeaking()
                : Math.max(1, Math.min(HotChannelModel.MAX_AXIAL_PEAKING, axialPeaking));

        water.saturationP(p, satBuf);
        SaturationState sat = satState.set(p, satBuf);
        double hIn = Math.min(water.enthalpyPT(p, inletTemp + dT), sat.hF());
        double massFlow = Math.max(g * model.flowArea(), 1e-6);
        double hotRise = model.enthalpyRiseFactor() * n * model.nominalPower() / massFlow;
        double hotFlux = model.averageHeatFlux() * n * model.enthalpyRiseFactor();
        double c = (fz - 1) / (1 - TWO_OVER_PI);

        double min = Double.POSITIVE_INFINITY;
        int m = model.axialPoints();
        for (int j = 0; j < m; j++) {
            double u = (j + 0.5) / m;
            double phase = Math.PI * (u - 0.5);
            double shape = 1 + c * (Math.cos(phase) - TWO_OVER_PI);
            double cumulative = u + c * ((Math.sin(phase) + 1) / Math.PI - TWO_OVER_PI * u);
            double xe = (hIn + hotRise * cumulative - sat.hF()) / sat.latentHeat();
            double chf = HeatTransferCorrelations.chf(sat, g, xe, model.hydraulicDiameter(), sat.hF() - hIn);
            min = Math.min(min, chf / (hotFlux * shape));
        }
        return min;
    }
}
