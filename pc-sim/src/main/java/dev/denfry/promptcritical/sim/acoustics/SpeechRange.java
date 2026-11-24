package dev.denfry.promptcritical.sim.acoustics;

/**
 * Дальность прямой речи в шуме (16 §16.4.2): крик {@link #SPEECH_DB} дБ(А) на 1 м спадает сферически,
 * {@code r = 10^((L_речь − L_шум)/20)}, и ограничен снизу {@code minM}, сверху — дальностью голоса сервера.
 * Шум берётся у говорящего: дальность задаётся на отправителе.
 */
public final class SpeechRange {
    /** Крик на 1 м, дБ(А) [BALANCE] ({@code ops.voice_speech_spl_db}). */
    public static final double SPEECH_DB = 84;
    /** Нижняя граница дальности, м ({@code ops.voice_min_range_m}). */
    public static final double MIN_M = 2;
    /** Маска противогаза или респиратор глушат владельца [BALANCE]. */
    public static final double MASK_FACTOR = 0.6;
    /** Маска изолирующего аппарата с переговорной мембраной [BALANCE]. */
    public static final double SCBA_FACTOR = 0.8;

    private SpeechRange() {}

    /**
     * Дальность речи, м.
     *
     * @param noiseDb уровень шума у говорящего, дБ(А); −∞ — тихо
     * @param maskFactor множитель маски: 1, {@link #MASK_FACTOR} или {@link #SCBA_FACTOR}
     */
    public static double metres(double speechDb, double noiseDb, double minM, double maxM, double maskFactor) {
        double r = Double.isNaN(noiseDb) ? maxM : Math.pow(10, (speechDb - noiseDb) / 20);
        return Math.max(Math.min(minM, maxM), Math.min(maxM, Math.max(minM, r)) * maskFactor);
    }
}
