package dev.denfry.promptcritical.sim.gasprocessing;

import dev.denfry.promptcritical.sim.refining.Fractionation;

/**
 * Газофракционирующая установка (ГФУ): ШФЛУ делится последовательно в деэтанизаторе (ключи этан/пропан, 2,8 МПа),
 * депропанизаторе (пропан/изобутан, 1,8 МПа) и дебутанизаторе (н-бутан/изопентан, 0,6 МПа) на сухой газ в топливную
 * сеть, технический пропан, бутаны и газовый бензин (C5+) (Бекиров, гл. 8; GPSA, разд. 19; Campbell, т. 2, гл. 17).
 *
 * <p>Каждая колонна — короткий метод Фенске–Андервуда–Гиллиленда ({@link Fractionation#effectiveStagesForAlpha}):
 * относительная летучесть ключей — отношение констант равновесия Вильсона ({@link Flash#wilsonK}) при температуре,
 * где K_лк·K_тк = 1 (средняя температура колонны), распределение неключевых — по Фенске d_i/b_i = K_i^{N_эф}.
 *
 * @param stagesPerColumn теоретических тарелок в каждой колонне
 * @param reflux          флегмовое число (одно на все колонны)
 */
public record NglFractionator(double stagesPerColumn, double reflux) {
    public static final double DEETHANIZER_PA = 2.8e6, DEPROPANIZER_PA = 1.8e6, DEBUTANIZER_PA = 0.6e6;

    /**
     * Продукты на моль сырья: мольные количества компонентов в каждом потоке.
     *
     * @param fuelGas  верх деэтанизатора (метан, этан)
     * @param propane  верх депропанизатора
     * @param butane   верх дебутанизатора
     * @param gasoline куб дебутанизатора
     */
    public record Result(double[] fuelGas, double[] propane, double[] butane, double[] gasoline) {
        public static double total(double[] s) {
            double t = 0;
            for (double v : s) t += v;
            return t;
        }

        /** Доля пропана в пропановой фракции. */
        public double propanePurity() {
            return frac(propane, GasComponent.C3);
        }

        /** Доля бутанов в бутановой фракции. */
        public double butanePurity() {
            return frac(butane, GasComponent.IC4) + frac(butane, GasComponent.NC4);
        }

        /** Доля C5+ в газовом бензине. */
        public double gasolinePurity() {
            return frac(gasoline, GasComponent.IC5) + frac(gasoline, GasComponent.NC5) + frac(gasoline, GasComponent.NC6);
        }

        private static double frac(double[] s, GasComponent c) {
            double t = total(s);
            return t > 0 ? s[c.ordinal()] / t : 0;
        }
    }

    public Result split(double[] feed) {
        double[][] c1 = column(feed, GasComponent.C2, GasComponent.C3, DEETHANIZER_PA);
        double[][] c2 = column(c1[1], GasComponent.C3, GasComponent.IC4, DEPROPANIZER_PA);
        double[][] c3 = column(c2[1], GasComponent.NC4, GasComponent.IC5, DEBUTANIZER_PA);
        return new Result(c1[0], c2[0], c3[0], c3[1]);
    }

    /** Колонна: [верх, куб] по мольным количествам. */
    double[][] column(double[] z, GasComponent lk, GasComponent hk, double p) {
        double lo = 150, hi = 550;
        for (int i = 0; i < 80; i++) {
            double t = 0.5 * (lo + hi);
            if (Flash.wilsonK(lk, t, p) * Flash.wilsonK(hk, t, p) > 1) hi = t;
            else lo = t;
        }
        double t = 0.5 * (lo + hi);
        double alpha = Flash.wilsonK(lk, t, p) / Flash.wilsonK(hk, t, p);
        double kRef = Math.sqrt(Flash.wilsonK(lk, t, p) * Flash.wilsonK(hk, t, p));
        double neff = Fractionation.effectiveStagesForAlpha(stagesPerColumn, reflux, alpha);
        double[] top = new double[z.length], bottom = new double[z.length];
        for (GasComponent c : GasComponent.values()) {
            int i = c.ordinal();
            double r = Math.pow(Flash.wilsonK(c, t, p) / kRef, neff);
            double ft = Double.isInfinite(r) ? 1 : r / (1 + r);
            top[i] = z[i] * ft;
            bottom[i] = z[i] - top[i];
        }
        return new double[][] {top, bottom};
    }
}
