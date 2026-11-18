package dev.denfry.promptcritical.mod.environment;

/**
 * Активность отработанного топлива при выгрузке — для T1 и любых облучённых сборок. Сигнатура стабильна: её зовёт
 * выгрузка канала (feat/i4-fuel-fabrication). Стальные конструкции (Co-60) считаются у сборок с размазанной плотностью
 * ниже 10 г/см³ (ТВС, кассеты); у сплошного металлического топлива (блочок, ≈ 19 г/см³) их нет.
 */
public final class SpentFuel {
    private SpentFuel() {
    }

    /**
     * @param heavyMetalKg       тяжёлый металл, кг
     * @param burnupMWdPerKg     выгорание, МВт·сут/кг ТМ
     * @param specificPowerWPerG удельная мощность при облучении, Вт/г ТМ
     * @param densityGcc         размазанная плотность сборки, г/см³
     * @param dischargeTick      игровое время выгрузки
     */
    public static ActivityData discharge(double heavyMetalKg, double burnupMWdPerKg, double specificPowerWPerG, double densityGcc,
            long dischargeTick) {
        return ActivityData.of(dev.denfry.promptcritical.sim.radiation.SpentFuel.discharge(heavyMetalKg, burnupMWdPerKg, specificPowerWPerG,
                densityGcc, densityGcc < 10), dischargeTick);
    }

    /** Остаточное тепловыделение предмета на игровое время {@code tick}, Вт. */
    public static double decayHeatW(ActivityData a, long tick) {
        return a.inventory(tick).decayHeatW();
    }
}
