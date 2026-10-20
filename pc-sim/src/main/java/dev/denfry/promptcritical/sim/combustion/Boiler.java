package dev.denfry.promptcritical.sim.combustion;

/**
 * Котёл — газовая сторона (07 §7.9.1): горение, тепловосприятие топки и конвективных поверхностей, тяга. Вода и
 * пар — узлы гидросети (01): котёл отдаёт тепло через {@link BoilerOutputs#heatToWaterW}, биндинг кладёт его в узел
 * ({@code FluidNode.addHeat}). Котёл-утилизатор появится с эпохой I3.
 */
public sealed interface Boiler permits FireTubeBoiler, WaterTubeBoiler {
    void stepFast(double dtS, BoilerInputs in, BoilerOutputs out);

    /** Состояние системы управления горелками (07 §7.9.6). */
    BmsState bms();
}
