package dev.denfry.promptcritical.sim.petrochem;

import dev.denfry.promptcritical.sim.refining.Fractionation;

/**
 * Газоразделение пирогаза по схеме с головным деметанизатором (Zimmermann, Walzl, «Ethylene», Ullmann's, 2009, §6;
 * Kniel, Winter, Stork, «Ethylene: Keystone to the Petrochemical Industry», 1980, гл. 6): закалочная колонна снимает смолу и тяжёлую часть пиробензина; пятиступенчатый компрессор поднимает газ с
 * 0,13 до 3,6 МПа с охлаждением между ступенями до 40 °C; щелочная промывка снимает CO₂ и H₂S (CO₂ + 2NaOH →
 * Na₂CO₃ + H₂O, H₂S + 2NaOH → Na₂S + 2H₂O); ацетилен гидрируется на палладии (70 % в этилен, 30 % — переводит до
 * этана); дальше колонны: деметанизатор, деэтанизатор, этиленовая (C2-сплиттер), депропанизатор, пропиленовая
 * (C3-сплиттер), дебутанизатор.
 *
 * <p>Колонны — короткий метод Фенске–Андервуда–Гиллиленда ({@link Fractionation#effectiveStagesForAlpha}) по
 * константам Вильсона при температуре, где K_лк·K_тк = 1, как в ГФУ; распределение неключевых — по Фенске. Число
 * тарелок и флегма — типичные: C2-сплиттер ~110 тарелок при флегме 3,5, C3-сплиттер ~160 при флегме 14 —
 * относительная летучесть пропилена к пропану при 1,8 МПа около 1,1.
 */
public final class CrackedGasSeparation {
    /** Колонна: ключи, давление, теоретические тарелки, флегма. */
    public record Column(PyroComponent lightKey, PyroComponent heavyKey, double pressurePa, double stages, double reflux) {
    }

    public static final Column DEMETHANIZER = new Column(PyroComponent.CH4, PyroComponent.C2H4, 3.2e6, 40, 1.5);
    public static final Column DEETHANIZER = new Column(PyroComponent.C2H6, PyroComponent.C3H6, 2.6e6, 35, 1.2);
    public static final Column C2_SPLITTER = new Column(PyroComponent.C2H4, PyroComponent.C2H6, 1.9e6, 110, 3.5);
    public static final Column DEPROPANIZER = new Column(PyroComponent.C3H8, PyroComponent.C4H6, 1.6e6, 40, 1.2);
    public static final Column C3_SPLITTER = new Column(PyroComponent.C3H6, PyroComponent.C3H8, 1.8e6, 160, 14);
    public static final Column DEBUTANIZER = new Column(PyroComponent.C4, PyroComponent.PYGAS, 0.5e6, 30, 1.0);

    /** Доля пиробензина, сконденсированная в закалочной колонне [оценка]. */
    static final double QUENCH_PYGAS = 0.6;
    public static final double SUCTION_PA = 0.13e6, DISCHARGE_PA = 3.6e6, STAGES = 5, KAPPA = 1.2, POLYTROPIC_EFF = 0.78,
            SUCTION_K = 313.15;
    public static final double ACETYLENE_TO_ETHYLENE = 0.7, NAOH_G_MOL = 40.0;

    /**
     * Итог разделения, моль/с по компонентам в каждом продукте.
     *
     * @param compressorW мощность компрессора пирогаза, Вт
     * @param causticKgS  расход NaOH, кг/с
     */
    public record Result(double[] fuelGas, double[] ethylene, double[] ethane, double[] propylene, double[] propane,
            double[] c4, double[] pygas, double[] fuelOil, double compressorW, double causticKgS) {
        public static double total(double[] s) {
            double t = 0;
            for (double v : s) t += v;
            return t;
        }

        public static double purity(double[] s, PyroComponent c) {
            double t = total(s);
            return t > 0 ? s[c.ordinal()] / t : 0;
        }

        public double ethylenePurity() {
            return purity(ethylene, PyroComponent.C2H4);
        }

        public double propylenePurity() {
            return purity(propylene, PyroComponent.C3H6);
        }
    }

    private CrackedGasSeparation() {
    }

    /** Разделить пирогаз {@code molS} (моль/с по компонентам). */
    public static Result split(double[] molS) {
        double[] gas = molS.clone();
        double[] oil = new double[PyroComponent.N], pygasQuench = new double[PyroComponent.N];
        oil[PyroComponent.FUEL_OIL.ordinal()] = gas[PyroComponent.FUEL_OIL.ordinal()];
        gas[PyroComponent.FUEL_OIL.ordinal()] = 0;
        pygasQuench[PyroComponent.PYGAS.ordinal()] = QUENCH_PYGAS * gas[PyroComponent.PYGAS.ordinal()];
        gas[PyroComponent.PYGAS.ordinal()] -= pygasQuench[PyroComponent.PYGAS.ordinal()];

        double compressor = compressorPowerW(gas);
        double caustic = 2 * (gas[PyroComponent.CO2.ordinal()] + gas[PyroComponent.H2S.ordinal()]) * NAOH_G_MOL / 1000;
        gas[PyroComponent.CO2.ordinal()] = 0;
        gas[PyroComponent.H2S.ordinal()] = 0;
        // гидрирование ацетилена
        double c2h2 = gas[PyroComponent.C2H2.ordinal()];
        double h2Need = c2h2 * (ACETYLENE_TO_ETHYLENE + 2 * (1 - ACETYLENE_TO_ETHYLENE));
        double done = h2Need <= gas[PyroComponent.H2.ordinal()] ? 1 : gas[PyroComponent.H2.ordinal()] / h2Need;
        gas[PyroComponent.C2H2.ordinal()] -= c2h2 * done;
        gas[PyroComponent.C2H4.ordinal()] += c2h2 * done * ACETYLENE_TO_ETHYLENE;
        gas[PyroComponent.C2H6.ordinal()] += c2h2 * done * (1 - ACETYLENE_TO_ETHYLENE);
        gas[PyroComponent.H2.ordinal()] -= h2Need * done;

        double[][] dm = column(gas, DEMETHANIZER);
        double[][] de = column(dm[1], DEETHANIZER);
        double[][] c2 = column(de[0], C2_SPLITTER);
        double[][] dp = column(de[1], DEPROPANIZER);
        double[][] c3 = column(dp[0], C3_SPLITTER);
        double[][] db = column(dp[1], DEBUTANIZER);
        double[] pygas = db[1].clone();
        for (int i = 0; i < pygas.length; i++) pygas[i] += pygasQuench[i];
        return new Result(dm[0], c2[0], c2[1], c3[0], c3[1], db[0], pygas, oil, compressor, caustic);
    }

    /**
     * Мощность многоступенчатого компрессора с промежуточным охлаждением: на ступень политропная работа
     * κ/(κ − 1)·RT₁·(π^{(κ−1)/κ} − 1)/η_пол на моль, π — равное отношение давлений ступеней.
     */
    public static double compressorPowerW(double[] molS) {
        double n = Result.total(molS);
        double pi = Math.pow(DISCHARGE_PA / SUCTION_PA, 1 / STAGES);
        double perStage = KAPPA / (KAPPA - 1) * EthaneCracking.R * SUCTION_K * (Math.pow(pi, (KAPPA - 1) / KAPPA) - 1) / POLYTROPIC_EFF;
        return n * perStage * STAGES;
    }

    /** Колонна: [верх, куб] по мольным расходам. */
    public static double[][] column(double[] z, Column c) {
        double p = c.pressurePa();
        double lo = 60, hi = 700;
        for (int i = 0; i < 80; i++) {
            double t = 0.5 * (lo + hi);
            if (c.lightKey().wilsonK(t, p) * c.heavyKey().wilsonK(t, p) > 1) hi = t;
            else lo = t;
        }
        double t = 0.5 * (lo + hi);
        double kl = c.lightKey().wilsonK(t, p), kh = c.heavyKey().wilsonK(t, p);
        double neff = Fractionation.effectiveStagesForAlpha(c.stages(), c.reflux(), kl / kh);
        double kRef = Math.sqrt(kl * kh);
        double[] top = new double[z.length], bottom = new double[z.length];
        for (PyroComponent comp : PyroComponent.values()) {
            int i = comp.ordinal();
            double r = Math.pow(comp.wilsonK(t, p) / kRef, neff);
            double ft = Double.isInfinite(r) || Double.isNaN(r) ? 1 : r / (1 + r);
            top[i] = z[i] * ft;
            bottom[i] = z[i] - top[i];
        }
        return new double[][] {top, bottom};
    }

    /** Температура колонны (где K_лк·K_тк = 1), К. */
    public static double temperatureK(Column c) {
        double lo = 60, hi = 700;
        for (int i = 0; i < 80; i++) {
            double t = 0.5 * (lo + hi);
            if (c.lightKey().wilsonK(t, c.pressurePa()) * c.heavyKey().wilsonK(t, c.pressurePa()) > 1) hi = t;
            else lo = t;
        }
        return 0.5 * (lo + hi);
    }
}
