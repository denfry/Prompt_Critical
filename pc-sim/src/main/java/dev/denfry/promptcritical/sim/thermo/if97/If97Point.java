package dev.denfry.promptcritical.sim.thermo.if97;

/**
 * Состояние воды/пара по эталонной IF97 (01 §1.3.1). Все величины в СИ:
 * Па, К, кг/м³, Дж/кг, Дж/(кг·К), м/с, Па·с, Вт/(м·К).
 *
 * <p>В двухфазной области (регион 4) {@code x} — массовое паросодержание,
 * а калорические и транспортные величины, не определённые для смеси
 * ({@code cp}, {@code cv}, {@code w}, {@code mu}, {@code k}, {@code drhodpT}),
 * равны {@code NaN}. В однофазной области {@code x} = {@code NaN}.
 *
 * @param region  регион IF97: 1, 2, 3, 4 (двухфазная смесь) или 5
 * @param drhodpT (∂ρ/∂p)_T, кг/(м³·Па)
 */
public record If97Point(int region, double p, double t, double rho, double h, double u, double s,
        double cp, double cv, double w, double x, double mu, double k, double drhodpT) {

    public double v() {
        return 1.0 / rho;
    }
}
