package dev.denfry.promptcritical.sim.grid;

/**
 * Паритет SU↔FE (16 §16.7.1, R56). Мост другого мода переводит SU в FE своим курсом; вместе с курсами комплекса
 * {@code W_PER_SU} и {@code J_PER_FE} это может дать вечный двигатель. Здесь — усиления обеих петель и эффективный
 * {@code J_PER_FE} режима {@code auto}.
 */
public final class FeParity {
    private FeParity() {
    }

    /** Курсы Crafts & Additions и New Age по умолчанию: мотор и генератор, FE/т на 1 SU. */
    public static final double BRIDGE_MOTOR_FE_T_PER_SU = 480.0 / 16_384, BRIDGE_GENERATOR_FE_T_PER_SU = 360.0 / 16_384;
    /** КПД нашего генератора на валу и электродвигателя в оценке петель (16 §16.7.1: G = 629, G' = 0,77) [сверить]. */
    public static final double GENERATOR_EFFICIENCY = 0.95, MOTOR_EFFICIENCY = 0.9;
    /** Тиков в секунду. */
    private static final double TPS = 20;

    /**
     * Петля «чужой мотор → вал → наш генератор → интерфейс → FE»: G = W_SU·η_g·η_if / (20·J_FE·c_m). Больше единицы —
     * энергия из ничего.
     */
    public static double forwardGain(double wPerSu, double jPerFe, double ifEfficiency, double motorFeTPerSu) {
        return wPerSu * GENERATOR_EFFICIENCY * ifEfficiency / (TPS * jPerFe * motorFeTPerSu);
    }

    /** Обратная петля «наш мотор → вал → чужой генератор → FE → интерфейс»: G' = 20·J_FE·c_a·η_m·η_if / W_SU. */
    public static double reverseGain(double wPerSu, double jPerFe, double ifEfficiency, double generatorFeTPerSu) {
        return TPS * jPerFe * generatorFeTPerSu * MOTOR_EFFICIENCY * ifEfficiency / wPerSu;
    }

    /** Режим {@code auto} при загруженном мосте: J_FE = 2·W_SU, обе петли убыточны (16 §16.7.1). */
    public static double parityJPerFe(double wPerSu) {
        return 2 * wPerSu;
    }

    /** Мощность интерфейса в FE/т при мощности P, Вт (00 §0.9): P·η/(J_FE·20). */
    public static double fePerTick(double powerW, double ifEfficiency, double jPerFe) {
        return powerW * ifEfficiency / (jPerFe * TPS);
    }
}
