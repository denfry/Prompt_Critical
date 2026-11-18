package dev.denfry.promptcritical.sim.production;

/**
 * Индикаторная кривая (IPR) нефтяной скважины: выше давления насыщения — прямая Дарси q = J(p̄ − p_wf), ниже — кривая
 * Вогеля для режима растворённого газа q/q_max = 1 − 0,2(p_wf/p̄) − 0,8(p_wf/p̄)² (Vogel, «Inflow Performance
 * Relationships for Solution-Gas Drive Wells», JPT, январь 1968), сшитые в составную кривую Стэндинга: для
 * p_wf &lt; p_b q = q_b + (J·p_b/1,8)·[1 − 0,2(p_wf/p_b) − 0,8(p_wf/p_b)²] (Brown, «The Technology of Artificial Lift
 * Methods», т. 1, гл. 1; Petroleum Engineering Handbook, т. IV, гл. 2).
 *
 * @param reservoirPa      среднее пластовое давление, Па
 * @param bubblePointPa    давление насыщения, Па
 * @param productivityM3sPa коэффициент продуктивности J выше p_b, м³/(с·Па), по нефти в поверхностных условиях
 */
public record Inflow(double reservoirPa, double bubblePointPa, double productivityM3sPa) {
    public Inflow {
        if (!(reservoirPa >= 0 && bubblePointPa >= 0 && productivityM3sPa >= 0)) throw new IllegalArgumentException("inflow parameters");
    }

    /** Дебит при забойном давлении, м³/с. */
    public double rateM3s(double pwfPa) {
        double pr = reservoirPa, pb = Math.min(bubblePointPa, pr), j = productivityM3sPa;
        double pwf = Math.max(0, pwfPa);
        if (pwf >= pr || pr <= 0) return 0;
        if (pwf >= pb) return j * (pr - pwf);
        double r = pwf / pb;
        return j * (pr - pb) + j * pb / 1.8 * (1 - 0.2 * r - 0.8 * r * r);
    }

    /** Абсолютно свободный дебит (p_wf = 0), м³/с. */
    public double maxRateM3s() {
        return rateM3s(0);
    }

    /** Забойное давление, при котором пласт даёт {@code q}; выше максимума — 0. */
    public double pwfAtPa(double q) {
        if (q <= 0) return reservoirPa;
        if (q >= maxRateM3s()) return 0;
        double lo = 0, hi = reservoirPa;
        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (lo + hi);
            if (rateM3s(mid) > q) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /** q_max Вогеля по одному замеру ниже давления насыщения (p_b = p̄): q_test / (1 − 0,2r − 0,8r²). */
    public static double vogelMaxRate(double qTest, double pwfTest, double reservoirPa) {
        double r = pwfTest / reservoirPa;
        return qTest / (1 - 0.2 * r - 0.8 * r * r);
    }
}
