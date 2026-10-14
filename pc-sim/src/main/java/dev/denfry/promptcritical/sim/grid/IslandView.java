package dev.denfry.promptcritical.sim.grid;

/**
 * Снимок электрического острова (07 §7.1.3, §7.2.2) в СИ.
 *
 * @param frequencyHz    частота центра инерции f_COI
 * @param rocofHzPerS    df_COI/dt за последний тик
 * @param inertiaWs      Σ H_i S_i, Вт·с
 * @param topologyPending ждёт переразложения, решение заморожено
 */
public record IslandView(int id, boolean energized, boolean topologyPending, int busCount, int machineCount,
        double frequencyHz, double rocofHzPerS, double generationW, double loadW, double lossW, double inertiaWs) {}
