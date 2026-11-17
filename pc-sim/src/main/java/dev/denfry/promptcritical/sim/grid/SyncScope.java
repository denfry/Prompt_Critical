package dev.denfry.promptcritical.sim.grid;

/**
 * Синхроноскоп (07 §7.3.5): чистая функция двух фазоров — генератора и сети.
 *
 * @param slipHz          Δf = f_ген − f_сети; > 0 — стрелка идёт по часовой
 * @param angleRad        Δθ = θ_ген − θ_сети, приведённый к (−π, π]
 * @param dvPu            ΔV = V_ген − V_сети, о.е.
 * @param advanceAngleRad φ_опер = 2π·Δf·t_вкл — угол, пройденный за время хода выключателя
 */
public record SyncScope(double slipHz, double angleRad, double dvPu, double advanceAngleRad) {
    public static SyncScope of(double fGen, double fGrid, double thGen, double thGrid, double vGen, double vGrid,
            double tCbS) {
        double slip = fGen - fGrid;
        return new SyncScope(slip, wrap(thGen - thGrid), vGen - vGrid, 2 * Math.PI * slip * tCbS);
    }

    /**
     * Пиковый момент при включении сейчас, о.е.:
     * T_pk = (E''/X''_Σ)·(|E'' − V·e^{−jΔθ}| + V·|sin Δθ|), V = E'' − ΔV.
     * <p>
     * Потокосцепление статора в момент замыкания сохраняется, поэтому ток — периодическая
     * составляющая плюс апериодическая: i(t) = i_ac·(1 − e^{−jωt}). Мощность в зазоре
     * P(t) = P₀·(1 − cos ωt) + Q₀·sin ωt, P₀ = E''V·sin Δθ/X'', Q₀ = E''(E'' − V cos Δθ)/X'',
     * её максимум |P₀| + √(P₀² + Q₀²). При V = 0 — E''²/X'', пик трёхфазного КЗ на выводах;
     * худший угол для вала около 120°, а не 180°.
     *
     * @param ePP       E'' входящей машины, о.е.
     * @param xSigmaPP  X''_Σ = x''_d + x_T + x_sys на S_ном машины
     */
    public double closingTorquePu(double ePP, double xSigmaPP) {
        double v = ePP - dvPu;
        return ePP * (closingCurrentPu(ePP, xSigmaPP) + v * Math.abs(Math.sin(angleRad)) / xSigmaPP);
    }

    /**
     * Амплитуда периодической составляющей тока включения, о.е.: |E'' − V·e^{−jΔθ}|/X''_Σ.
     * Нагрев и электродинамические силы в обмотках идут от тока, худший угол — 180°.
     */
    public double closingCurrentPu(double ePP, double xSigmaPP) {
        if (!(xSigmaPP > 0)) throw new IllegalArgumentException("xSigmaPP");
        double v = ePP - dvPu;
        return Math.sqrt(Math.max(0, ePP * ePP + v * v - 2 * ePP * v * Math.cos(angleRad))) / xSigmaPP;
    }

    /** Угол, при котором коснутся контакты, если дать команду сейчас. */
    public double angleAtClosingRad() {
        return wrap(angleRad + advanceAngleRad);
    }

    /** Окно точной синхронизации (IEEE C50.13): |Δθ|, 0 ≤ ΔV ≤ допуск (генератор не ниже сети), |Δf|. */
    public boolean inWindow(double maxAngleRad, double maxDvPu, double maxSlipHz) {
        return Math.abs(angleRad) <= maxAngleRad && dvPu > -1e-6 && dvPu <= maxDvPu && Math.abs(slipHz) <= maxSlipHz;
    }

    static double wrap(double a) {
        double w = a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI));
        return w == -Math.PI ? Math.PI : w;
    }
}
