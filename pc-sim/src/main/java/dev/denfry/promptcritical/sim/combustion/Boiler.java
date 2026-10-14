package dev.denfry.promptcritical.sim.combustion;

/**
 * Котёл — газовая сторона (07 §7.9.1): горение, тепловосприятие топки и конвективных поверхностей, тяга. Вода и
 * пар — узлы гидросети (01): котёл отдаёт тепло через {@link BoilerOutputs#heatToWaterW}, биндинг кладёт его в узел
 * ({@code FluidNode.addHeat}). Водотрубный котёл и котёл-утилизатор появятся с эпохой I1 (M3).
 */
public sealed interface Boiler permits FireTubeBoiler {
    void stepFast(double dtS, BoilerInputs in, BoilerOutputs out);
}
