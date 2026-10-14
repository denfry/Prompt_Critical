package dev.denfry.promptcritical.sim.thermo;

/**
 * Пакет свойств среды для гидросети (00 §0.13 R11, 01 §1.3, 09 §9.15.1). {@code FluidNetwork}
 * остаётся единственным решателем, а EOS, транспорт и критический поток берёт отсюда.
 * Одна сеть — один пакет: среды смешиваются только через теплообменник или
 * сосуд-разделитель, где масса явно переходит в другую сеть источником S_M.
 *
 * <p>Состав узла — {@link #componentCount()} массовых долей, начиная с {@code offset}
 * в массиве {@code massFractions}; однокомпонентные среды массив не читают. Методы
 * потокобезопасны и не аллоцируют. Единицы — СИ.
 */
public interface FluidPackage {
    /** {@code "water"}, {@code "hydrocarbon"}, {@code "gas_mixture"}. */
    String id();

    /** Число долей состава, влияющих на EOS (0 для однокомпонентной среды). */
    int componentCount();

    /** (ρ, u, состав) → состояние; pGuess — тёплый старт (давление на прошлом шаге). */
    void stateFromMassEnergy(double density, double specificEnergy, double[] massFractions, int offset,
            double pGuess, ThermoState out);

    void stateFromPressureEnthalpy(double p, double h, double[] massFractions, int offset, ThermoState out);

    /** Критический массовый поток G_c [кг/(м²·с)] по параметрам торможения (01 §1.2.8). */
    double criticalMassFlux(double p0, double h0, double[] massFractions, int offset);

    /** Скорость звука, м/с: оценка гидроудара по Жуковскому (01 §1.2.9), акустика 04a. */
    double soundSpeed(double p, double h, double[] massFractions, int offset);

    /** Ключ семейства состава для кэша таблиц (0 для однокомпонентных сред). */
    long familyKey(double[] massFractions, int offset);
}
