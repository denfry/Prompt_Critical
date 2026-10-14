package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.thermo.GasSpecies;

/**
 * Модель горения (07 §7.8): общая для котлов, подогревателей, печей и факела. Квазистатика FAST: по расходам топлива
 * и воздуха даёт тепловыделение, адиабатную температуру, состав газов и выбросы. Константы топлива считаются в
 * конструкторе, {@link #burn} не аллоцирует.
 *
 * <p>Неполное сгорание (07 §7.8.3): доля углерода до CO f_CO = ½[1 − tanh((α − α_кр)/w_α)]·k_mix. Если воздуха не
 * хватает и на горение с этим f_CO, сначала растёт f_CO (до 1), затем часть горючего уходит несгоревшей
 * (газ — как CH₄, твёрдое и жидкое — как сажа в PM). Это упрощение для режима α &lt; 1: состав сажи и H₂ в газах не
 * моделируется.
 */
public final class Combustor {
    private static final double T0 = 298.15;
    private static final double W_ALPHA = 0.03;
    /** Массы продуктов на кг элемента. */
    private static final double CO2_PER_C = 1 + FuelSpec.O2_PER_C, CO_PER_C = 1 + FuelSpec.O2_PER_C_TO_CO,
            H2O_PER_H = 1 + FuelSpec.O2_PER_H, SO2_PER_S = 1 + FuelSpec.O2_PER_S;
    private static final double AIR_O2 = FuelSpec.AIR_O2_MASS_FRACTION, AIR_N2 = 1 - AIR_O2;
    private static final double CO_LHV_J_PER_KG =
            FuelGas.CO.lhvJPerNm3 / (FuelGas.CO.molarMass / FuelGas.NORMAL_MOLAR_VOLUME);
    private static final double NO2_PER_N = 0.0460055 / FuelGas.M_N;
    /** Термические NOx: e_th,ref = 80 г/ГДж при T_ref = 2200 К и 3 % O₂ (07 §7.8.4), кг/Дж. */
    private static final double NOX_TH_REF = 80e-3 / 1e9, NOX_T_REF = 2200, NOX_ACTIVATION = 38_370, NOX_O2_REF = 0.03;

    private static final GasSpecies[] SPECIES = GasSpecies.values();
    private static final double[] H0 = new double[SPECIES.length];
    private static final int CO2 = GasSpecies.CO2.ordinal(), CO = GasSpecies.CO.ordinal(),
            H2O = GasSpecies.H2O_VAPOR.ordinal(), SO2 = GasSpecies.SO2.ordinal(), N2 = GasSpecies.N2.ordinal(),
            O2 = GasSpecies.O2.ordinal();

    static {
        for (GasSpecies s : SPECIES) H0[s.ordinal()] = s.enthalpy(T0);
    }

    private final FuelSpec fuel;
    private final BurnerSpec burner;
    private final double lhv, o2st, l0, alphaCrit, q4;
    /** Мольные количества продуктов на кг топлива (рабочий буфер). */
    private final double[] n = new double[SPECIES.length];

    public Combustor(FuelSpec fuel, BurnerSpec burner) {
        this.fuel = fuel;
        this.burner = burner;
        this.lhv = fuel.lhv();
        this.o2st = fuel.stoichOxygenKgPerKg();
        this.l0 = fuel.stoichAirKgPerKg();
        this.alphaCrit = Double.isNaN(fuel.alphaCrit()) ? burner.kind().alphaCrit : fuel.alphaCrit();
        this.q4 = fuel.phase() == FuelPhase.SOLID ? burner.kind().mechanicalLoss : 0;
    }

    public FuelSpec fuel() {
        return fuel;
    }

    public BurnerSpec burner() {
        return burner;
    }

    /** Q_н^р, Дж/кг. */
    public double lhv() {
        return lhv;
    }

    /** L₀, кг воздуха на кг топлива. */
    public double stoichAir() {
        return l0;
    }

    /** Коэффициент избытка воздуха α = L / (B·L₀). */
    public double excessAir(double fuelKgS, double airKgS) {
        return fuelKgS > 0 && l0 > 0 ? airKgS / (fuelKgS * l0) : Double.POSITIVE_INFINITY;
    }

    /** Расход воздуха, кг/с, дающий избыток α. */
    public double airFor(double fuelKgS, double alpha) {
        return fuelKgS * alpha * l0;
    }

    /**
     * Горение за шаг.
     *
     * @param fuelKgS  расход топлива B, кг/с
     * @param airKgS   расход сухого воздуха, кг/с
     * @param airTempK температура воздуха (после подогревателя), К
     * @param fuelTempK температура топлива, К
     */
    public void burn(double fuelKgS, double airKgS, double airTempK, double fuelTempK, CombustionResult out) {
        out.clear(airTempK);
        if (!(fuelKgS > 0) || !(lhv > 0) || !(l0 > 0)) {
            passAir(Math.max(airKgS, 0), out);
            return;
        }
        double c = fuel.c(), h = fuel.h(), s = fuel.s();
        double alpha = airKgS / (fuelKgS * l0);
        double fCo = clamp01(0.5 * (1 - Math.tanh((alpha - alphaCrit) / W_ALPHA)) * burner.mixingQuality());
        double o2Avail = AIR_O2 * alpha * l0;
        if (o2Avail < o2st - FuelSpec.O2_PER_C_TO_CO * fCo * c && c > 0)
            fCo = clamp01((o2st - o2Avail) / (FuelSpec.O2_PER_C_TO_CO * c));
        double o2Demand = o2st - FuelSpec.O2_PER_C_TO_CO * fCo * c;
        double burnt = burner.kind().completeness;
        if (o2Demand > 0 && o2Avail < burnt * o2Demand) burnt = o2Avail / o2Demand;
        double unburnt = 1 - burnt;

        double mCo2 = CO2_PER_C * c * burnt * (1 - fCo);
        double mCo = CO_PER_C * c * burnt * fCo;
        double mH2o = H2O_PER_H * h * burnt + fuel.moisture();
        double mSo2 = SO2_PER_S * s * burnt * (1 - fuel.sulfurRetention());
        double mN2 = AIR_N2 * alpha * l0 + fuel.n();
        double mO2 = Math.max(0, o2Avail - burnt * o2Demand);
        java.util.Arrays.fill(n, 0);
        n[CO2] = mCo2 / GasSpecies.CO2.molarMass();
        n[CO] = mCo / GasSpecies.CO.molarMass();
        n[H2O] = mH2o / GasSpecies.H2O_VAPOR.molarMass();
        n[SO2] = mSo2 / GasSpecies.SO2.molarMass();
        n[N2] = mN2 / GasSpecies.N2.molarMass();
        n[O2] = mO2 / GasSpecies.O2.molarMass();

        double q3 = fCo * burnt * CO_PER_C * c * CO_LHV_J_PER_KG / lhv;
        double heatPerKg = lhv * Math.max(0, burnt - q3 - q4);
        double airSensible = alpha * l0 * (AIR_N2 * (GasSpecies.N2.enthalpy(airTempK) - H0[N2]) / GasSpecies.N2.molarMass()
                + AIR_O2 * (GasSpecies.O2.enthalpy(airTempK) - H0[O2]) / GasSpecies.O2.molarMass());
        double fuelSensible = fuel.phase().heatCapacity * (fuelTempK - T0);
        out.adiabaticFlameK = flameTemperature(n, heatPerKg + airSensible + fuelSensible);

        double total = 0;
        for (double v : n) total += v;
        double dry = total - n[H2O];
        out.excessAir = alpha;
        out.q3 = q3;
        out.q4 = q4;
        out.heatReleaseW = fuelKgS * heatPerKg;
        double unburntKg = unburnt * (c + h + s);
        out.fluegasKgS = fuelKgS * (mCo2 + mCo + mH2o + mSo2 + mN2 + mO2 + unburntKg);
        out.xCO2 = n[CO2] / total;
        out.xCO = n[CO] / total;
        out.xH2O = n[H2O] / total;
        out.xSO2 = n[SO2] / total;
        out.xN2 = n[N2] / total;
        out.xO2 = n[O2] / total;
        out.xO2Dry = n[O2] / dry;
        out.dryFluegasNm3S = fuelKgS * dry * FuelGas.NORMAL_MOLAR_VOLUME;
        for (int k = 0; k < n.length; k++) out.productMolS[k] = n[k] * fuelKgS;

        out.ashKgS = fuelKgS * fuel.ash();
        out.flyAshKgS = out.ashKgS * burner.kind().flyAshFraction;
        out.co2KgS = fuelKgS * mCo2;
        out.coKgS = fuelKgS * mCo;
        out.so2KgS = fuelKgS * mSo2;
        out.ch4KgS = fuel.phase() == FuelPhase.GAS ? fuelKgS * unburnt * (c + h) : 0;
        out.pmKgS = out.flyAshKgS + (fuel.phase() == FuelPhase.GAS ? 0 : fuelKgS * unburnt * c);

        double thermal = out.xO2Dry > 0
                ? NOX_TH_REF * Math.exp(-NOX_ACTIVATION * (1 / out.adiabaticFlameK - 1 / NOX_T_REF))
                        * Math.sqrt(out.xO2Dry / NOX_O2_REF) * fuelKgS * lhv
                : 0;
        double fuelNox = fuelKgS * burnt * fuel.n() * fuel.fuelNConversion() * NO2_PER_N;
        out.noxKgS = burner.noxFactor() * (thermal + fuelNox);
        out.noxMgPerNm3Dry6 = out.noxKgS * 1e6 / out.dryFluegasNm3S * (0.21 - 0.06) / Math.max(0.21 - out.xO2Dry, 5e-3);
    }

    /** Без топлива через топку идёт воздух (продувка, погасший факел). */
    private void passAir(double airKgS, CombustionResult out) {
        double nN2 = AIR_N2 * airKgS / GasSpecies.N2.molarMass(), nO2 = AIR_O2 * airKgS / GasSpecies.O2.molarMass();
        out.productMolS[N2] = nN2;
        out.productMolS[O2] = nO2;
        out.fluegasKgS = airKgS;
        out.excessAir = Double.POSITIVE_INFINITY;
        if (airKgS > 0) {
            out.xN2 = nN2 / (nN2 + nO2);
            out.xO2 = out.xO2Dry = nO2 / (nN2 + nO2);
            out.dryFluegasNm3S = (nN2 + nO2) * FuelGas.NORMAL_MOLAR_VOLUME;
        }
    }

    /** T_ad из Σ n_k (h_k(T) − h_k(T₀)) = Q, Ньютон по c_p NASA (07 §7.8.4, без диссоциации). */
    private static double flameTemperature(double[] moles, double heat) {
        double t = 2000;
        for (int it = 0; it < 40; it++) {
            double f = sensibleHeat(moles, t) - heat, d = 0;
            for (int k = 0; k < moles.length; k++) if (moles[k] > 0) d += moles[k] * SPECIES[k].cp(t);
            if (!(d > 0)) return T0;
            double next = Math.min(GasSpecies.T_MAX, Math.max(GasSpecies.T_MIN, t - f / d));
            if (Math.abs(next - t) < 1e-3) return next;
            t = next;
        }
        return t;
    }

    static double sensibleHeat(double[] moles, double tK) {
        double q = 0;
        for (int k = 0; k < moles.length; k++) if (moles[k] != 0) q += moles[k] * (SPECIES[k].enthalpy(tK) - H0[k]);
        return q;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
