package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Энергия Вигнера в графите (04c §4c.11.1): смещённые нейтронами атомы решётки запасают энергию, пока графит облучают
 * холодным, и отдают её теплом при нагреве выше температуры облучения.
 *
 * <p>Накопление по флюенсу (04c §4c.11.1): {@code dE/dΦ = k_W (1 − E/E_sat(T_irr))},
 * {@code E_sat(T) = E₀ max(0, 1 − (T − 300)/300)}, {@code k_W = E₀/Φ_s}; E₀ = 2000 Дж/г
 * ({@code accident.wigner_saturation_j_g}).
 *
 * <p>Высвобождение — спектр по температуре отжига, как его снимают калориметрией облучённого графита: запас лежит в
 * корзинах по {@value #BIN_K} К; новая энергия ложится выше {@code T_irr + 50 К} гауссовым пиком около 200 °C (у
 * графита, облучённого горячее, пик сдвинут вверх). Корзина при температуре графита выше своей отдаёт запас с
 * постоянной времени {@link #RELEASE_TIME_S}, ниже — экспоненциально медленнее (логистическая ступень шириной
 * {@link #ONSET_WIDTH_K}). Если плотность спектра dE/dT больше теплоёмкости графита c_p, отданное тепло само поднимает
 * графит к следующим корзинам: нагрев самоподдерживается, как на Windscale-1 в 1957 году. Отжиг удаётся, пока запас мал
 * (dE/dT &lt; c_p) или пока отвод тепла успевает за выделением.
 *
 * <p>Модель сосредоточенная: одна ёмкость на всю кладку, как и температура графита котла. Время — то, что передаёт
 * вызывающий: котёл T0 ведёт графит на часах SLOW ({@code K_slow}, 00 §0.4).
 */
public final class WignerEnergy {
    /** E₀, Дж/г: насыщенная запасённая энергия графита, облучённого около 30 °C (04c §4c.11.1). */
    public static final double SATURATION_J_G = 2000;
    /** Теплоёмкость графита около 300–400 К, Дж/(г·К) (04c §4c.11.1: 0,7–1,0). */
    public static final double CP_J_GK = 0.71;
    /** Пик высвобождения у графита, облучённого холодным, К (04c §4c.11.1: около 200 °C). */
    public static final double PEAK_K = 473.15;
    /** Запас начинает выходить на столько выше температуры облучения, К (04c §4c.11.1). */
    public static final double ONSET_ABOVE_IRRADIATION_K = 50;
    /**
     * Полуширина спектра, К [BALANCE]: спектры отжига холодно облучённого графита (BEPO, Windscale) тянутся от ~100
     * до ~400 °C с максимумом около 200 °C.
     */
    public static final double SPECTRUM_SIGMA_K = 80;
    /** Постоянная высвобождения корзины выше её температуры, с [BALANCE]: изотермический отжиг идёт минутами. */
    public static final double RELEASE_TIME_S = 300;
    /** Ширина ступени включения корзины, К [BALANCE]. */
    public static final double ONSET_WIDTH_K = 6;
    /** Корзины спектра: от {@value #BIN_LOW_K} К через {@value #BIN_K} К. */
    public static final double BIN_LOW_K = 320, BIN_K = 10;
    public static final int BINS = 44;
    /** Окисление графита на воздухе становится заметным выше 400 °C (04c §4c.11.1): отжиг выше — уже не отжиг. */
    public static final double OXIDATION_K = 673.15;
    /** Сколько надо отдать за отжиг, Дж/г, чтобы он считался (достижение «Отжиг», 11). */
    public static final double ANNEAL_MIN_RELEASE_J_G = 50;
    /** Отжиг закончен, когда запас упал до этой доли от исходного. */
    public static final double ANNEAL_DONE_FRACTION = 0.5;

    /** Высвобождение, заметное на приборах, Вт/г (100 Вт на тонну). */
    public static final double SIGNIFICANT_RELEASE_W_G = 1e-4;

    private final double fluenceScale;
    private final double[] bins = new double[BINS];
    private double releasePowerWg;
    /** Наибольший запас с последнего отжига и наибольшая температура графита с тех пор. */
    private double peakJg, peakTempK;

    /**
     * @param fluenceScale Φ_s, н/см²: за такой флюенс холодный графит подходит к насыщению
     *                     ({@code accident.wigner_fluence_scale})
     */
    public WignerEnergy(double fluenceScale) {
        if (!(fluenceScale > 0)) throw new IllegalArgumentException("fluenceScale must be > 0");
        this.fluenceScale = fluenceScale;
    }

    /** E_sat при температуре облучения {@code tK}, Дж/г. */
    public static double saturation(double tK) {
        return SATURATION_J_G * Math.max(0, 1 - (tK - 300) / 300);
    }

    /** Облучение флюенсом {@code fluence} н/см² при температуре графита {@code tK}. */
    public void irradiate(double fluence, double tK) {
        if (!(fluence > 0)) return;
        double sat = saturation(tK), e = storedJg();
        if (!(sat > e)) return;
        // точное решение dE/dΦ = k(1 − E/E_sat) на шаге: E → E_sat − (E_sat − E)·exp(−kΦ/E_sat)
        deposit((sat - e) * -Math.expm1(-SATURATION_J_G * fluence / (fluenceScale * sat)), tK);
    }

    /** Положить {@code add} Дж/г в спектр графита, облучённого при {@code tK}. */
    private void deposit(double add, double tK) {
        double onset = tK + ONSET_ABOVE_IRRADIATION_K, peak = Math.max(PEAK_K, tK + 2 * ONSET_ABOVE_IRRADIATION_K);
        double sum = 0;
        double[] w = new double[BINS];
        for (int i = 0; i < BINS; i++) {
            double t = binTempK(i);
            if (t < onset) continue;
            double x = (t - peak) / SPECTRUM_SIGMA_K;
            w[i] = Math.exp(-0.5 * x * x);
            sum += w[i];
        }
        if (!(sum > 0) || !(add > 0)) return;
        for (int i = 0; i < BINS; i++) bins[i] += add * w[i] / sum;
        trackPeak(tK);
    }

    /**
     * Высвобождение за {@code dt} при температуре графита {@code tK}.
     *
     * @return отданная энергия, Дж/г
     */
    public double release(double tK, double dt) {
        double out = 0;
        for (int i = 0; i < BINS; i++) {
            if (bins[i] <= 0) continue;
            double d = bins[i] * -Math.expm1(-rate(tK, binTempK(i)) * dt);
            bins[i] -= d;
            out += d;
        }
        releasePowerWg = dt > 0 ? out / dt : 0;
        trackPeak(tK);
        return out;
    }

    /** Скорость отдачи корзины с температурой {@code binK} при графите {@code tK}, 1/с. */
    static double rate(double tK, double binK) {
        double x = (tK - binK) / ONSET_WIDTH_K;
        if (x < -40) return 0;
        return 1 / (RELEASE_TIME_S * (1 + Math.exp(-x)));
    }

    /**
     * Отжиг закончен: запас упал до {@link #ANNEAL_DONE_FRACTION} от наибольшего, отдано не меньше
     * {@link #ANNEAL_MIN_RELEASE_J_G}, выделение улеглось, а графит ни разу не дошёл до окисления. Возвращает отданную энергию, Дж/г, или 0;
     * в обоих случаях, когда запас стал мал, отсчёт начинается заново (перегретый отжиг не засчитывается).
     */
    public double takeCompletedAnneal() {
        double e = storedJg();
        if (peakJg - e < ANNEAL_MIN_RELEASE_J_G || e > ANNEAL_DONE_FRACTION * peakJg) return 0;
        if (releasePowerWg > SIGNIFICANT_RELEASE_W_G) return 0; // ещё идёт: судить, когда уляжется
        double released = peakTempK < OXIDATION_K ? peakJg - e : 0;
        peakJg = e;
        peakTempK = 0;
        return released;
    }

    private void trackPeak(double tK) {
        double e = storedJg();
        if (e > peakJg) peakJg = e;
        peakTempK = Math.max(peakTempK, tK);
    }

    static double binTempK(int i) {
        return BIN_LOW_K + BIN_K * (i + 0.5);
    }

    /** Запасённая энергия, Дж/г. */
    public double storedJg() {
        double s = 0;
        for (double b : bins) s += b;
        return s;
    }

    /** Мощность высвобождения последнего шага, Вт/г. */
    public double releasePowerWg() {
        return releasePowerWg;
    }

    /**
     * Плотность спектра у температуры {@code tK}, Дж/(г·К): ещё не отданный запас в корзинах на 30 К выше. Больше
     * {@link #CP_J_GK} — тепло отжига само поднимет графит дальше.
     */
    public double spectrumDensity(double tK) {
        double s = 0;
        for (int i = 0; i < BINS; i++) {
            double t = binTempK(i);
            if (t >= tK - BIN_K / 2 && t < tK + 30) s += bins[i];
        }
        return s / 30;
    }

    /** Самоподдерживающийся нагрев: dE/dT впереди больше c_p и запас уже выходит. */
    public boolean selfSustaining(double tK) {
        return spectrumDensity(tK) > CP_J_GK && releasePowerWg > SIGNIFICANT_RELEASE_W_G;
    }

    public double peakJg() {
        return peakJg;
    }

    /** Для тестов и сценариев (11, {@code wigner_anneal}): запас, облучённый при {@code tK}. */
    public void preload(double jg, double tK) {
        deposit(Math.min(jg, saturation(tK)) - storedJg(), tK);
    }

    public void save(StateWriter out) {
        out.putDoubles("wigner_bins", bins, BINS);
        out.putDouble("wigner_peak", peakJg);
        out.putDouble("wigner_peak_k", peakTempK);
    }

    public void load(StateReader in) {
        java.util.Arrays.fill(bins, 0);
        if (in.has("wigner_bins")) {
            double[] b = in.getDoubles("wigner_bins");
            for (int i = 0; i < Math.min(b.length, BINS); i++) bins[i] = Math.max(0, b[i]);
        }
        peakJg = in.has("wigner_peak") ? in.getDouble("wigner_peak") : storedJg();
        peakTempK = in.has("wigner_peak_k") ? in.getDouble("wigner_peak_k") : 0;
    }
}
