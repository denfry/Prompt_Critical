package dev.denfry.promptcritical.sim.hydraulics;

/**
 * Начальное состояние при пересборке сети (01 §1.12.2). Узлы с прежним id сохраняют
 * (M, U) и скаляры; новый узел, собранный из блоков удалённых узлов, получает их долю
 * массы, энергии и скаляров по числу блоков; остальным узлам (p, h) задаёт этот объект.
 */
public interface StateCarryOver {
    /** Давление нового узла, Па. */
    double initialPressure(long nodeId);

    /** Удельная энтальпия нового узла, Дж/кг. */
    double initialEnthalpy(long nodeId);

    /** Расход новой связи, кг/с. */
    default double initialMassFlow(long linkId) {
        return 0;
    }

    /** Скорость вала нового насоса, рад/с. */
    default double initialPumpSpeed(long linkId) {
        return 0;
    }

    static StateCarryOver uniform(double pressure, double enthalpy) {
        return new StateCarryOver() {
            @Override
            public double initialPressure(long nodeId) {
                return pressure;
            }

            @Override
            public double initialEnthalpy(long nodeId) {
                return enthalpy;
            }
        };
    }
}
