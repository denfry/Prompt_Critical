package dev.denfry.promptcritical.sim.thermo;

/**
 * Свойства воды и пара для горячего цикла (01 §1.3, §1.13.1). Реализация —
 * предвычисленные таблицы IAPWS-IF97; методы потокобезопасны и, кроме
 * {@link #saturationP(double)}, не аллоцируют. Единицы — СИ.
 */
public interface WaterProperties {
    /** Главная операция: (ρ, u) → полное состояние. pGuess — тёплый старт (давление на прошлом шаге). */
    void stateFromMassEnergy(double density, double specificEnergy, double pGuess, ThermoState out);

    void stateFromPressureEnthalpy(double p, double h, ThermoState out);

    double enthalpyPT(double p, double t);

    double temperaturePH(double p, double h);

    double entropyPH(double p, double h);

    /** Энтальпия на изоэнтропе: турбина (03a), критический расход. */
    double enthalpyPS(double p, double s);

    /** Кэшируемая ветка — для HMI и тестов (аллоцирует). */
    SaturationState saturationP(double p);

    /** Без аллокаций: 17 величин в порядке полей {@link SaturationState} после давления. */
    void saturationP(double p, double[] out17);

    double saturationPressure(double t);

    /** Скорость звука: однофазная — IF97, двухфазная — гомогенная формула Вуда. */
    double soundSpeed(double p, double h);

    static WaterProperties tables() {
        return If97Tables.INSTANCE;
    }
}
