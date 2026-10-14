package dev.denfry.promptcritical.sim.grid;

/** Итоги тика по всем островам (СИ); объект переиспользуется вызывающим. */
public final class GridOutputs {
    double generationW, loadW, lossW, maxSolverStepPu;
    int islands, pendingIslands;

    /** Σ P_e машин, Вт. */
    public double generationW() {
        return generationW;
    }

    /** Σ P нагрузок (отрицательные — источники без инерции), Вт. */
    public double loadW() {
        return loadW;
    }

    /** Потери в ветвях, шунтах и КЗ, Вт. */
    public double lossW() {
        return lossW;
    }

    /** Невязка баланса острова Σ P_gen − Σ P_load − Σ P_loss, Вт (тест T7.20). */
    public double imbalanceW() {
        return generationW - loadW - lossW;
    }

    /** Наибольшее изменение напряжения на последней итерации сети, о.е. */
    public double maxSolverStepPu() {
        return maxSolverStepPu;
    }

    public int islands() {
        return islands;
    }

    /** Острова, ждущие переразложения (флаг {@code TOPOLOGY_PENDING}, §7.1.3). */
    public int pendingIslands() {
        return pendingIslands;
    }
}
