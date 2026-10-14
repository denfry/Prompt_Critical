package dev.denfry.promptcritical.sim.core;

/**
 * Величины, которые домен берёт из мира MC раз в тик (05 §5.2.1); заполняет биндинг, примитивно и без аллокаций.
 * Порты и станции адресуются целыми номерами, которые выдаёт биндинг.
 */
public interface ExternalInputs {
    ExternalInputs NONE = new ExternalInputs() {
        @Override
        public double shaftSpeed(int kineticPortId) {
            return 0.0;
        }

        @Override
        public double shaftStressCapacityW(int kineticPortId) {
            return 0.0;
        }

        @Override
        public double ambientTemperature(int stationId) {
            return 288.15;
        }

        @Override
        public boolean chunkLoaded(int stationId) {
            return true;
        }
    };

    /** Угловая скорость вала Create, рад/с (03a). */
    double shaftSpeed(int kineticPortId);

    /** Доступная мощность сети Create, Вт (через {@code energy.w_per_su}). */
    double shaftStressCapacityW(int kineticPortId);

    /** Температура окружающего воздуха, K. */
    double ambientTemperature(int stationId);

    boolean chunkLoaded(int stationId);
}
