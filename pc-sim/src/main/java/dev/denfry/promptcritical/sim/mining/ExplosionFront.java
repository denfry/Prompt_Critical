package dev.denfry.promptcritical.sim.mining;

import java.util.Arrays;

/**
 * Фронт взрыва метана и пыли (08 §8.2.6): обход в ширину по узлам, один шаг графа за тик, пока узел
 * поддерживает горение ({@link MethaneField#propagates}). Заслон в ветви с ≥ {@link #BARRIER_KG_PER_M2} кг
 * инертного на м² сечения гасит фронт и при этом расходуется. Для каждого сгоревшего узла — энергия в разрушение,
 * из неё привязка делает {@code DamageRequest(BLAST, priority 2)} (04d §4d.2.4).
 */
public final class ExplosionFront {
    /** Заслон гасит фронт от этой удельной массы инертного, кг/м² сечения. */
    public static final double BARRIER_KG_PER_M2 = 400;
    /** Наименьшая доля сгоревшего углерода в CO. */
    public static final double CO_MIN_SHARE = 0.1;

    private final MethaneField field;
    private final MineTopology t;
    private final boolean ignited;
    private final boolean[] burned;
    private int[] nodes = new int[8];
    private double[] energy = new double[8];
    private int[] tick = new int[8];
    private int count, frontStart, frontEnd, ticks, quenched;

    ExplosionFront(MethaneField field, int origin, boolean ignited) {
        this.field = field;
        this.t = field.t;
        this.ignited = ignited;
        this.burned = new boolean[t.nodeCount()];
        if (ignited) {
            burn(origin);
            frontEnd = count;
        }
    }

    /** Взрыв состоялся (иначе источник ничего не воспламенил). */
    public boolean ignited() {
        return ignited;
    }

    /** Фронт остановился. */
    public boolean done() {
        return frontStart == frontEnd;
    }

    /** Один шаг фронта (тик): сжечь соседей текущего фронта. Возвращает число новых узлов. */
    public int advance() {
        if (done()) return 0;
        ticks++;
        int start = frontStart, end = frontEnd;
        for (int i = start; i < end; i++) {
            int u = nodes[i];
            for (int a = t.adjStart[u]; a < t.adjStart[u + 1]; a++) {
                int j = t.adjBranch[a], v = t.other(j, u);
                if (v == MineTopology.ATMOSPHERE || burned[v]) continue;
                if (barrier(j)) continue;
                if (field.propagates(v)) burn(v);
            }
        }
        frontStart = end;
        frontEnd = count;
        return frontEnd - frontStart;
    }

    /** Прогнать фронт до остановки (не больше числа узлов шагов). */
    public ExplosionFront runToEnd() {
        while (!done()) advance();
        return this;
    }

    private boolean barrier(int branch) {
        double kg = t.barrierKg[branch];
        if (kg <= 0 || field.barrierSpent[branch]) return false;
        field.barrierSpent[branch] = true; // волна опрокидывает полки или сосуды в любом случае
        if (kg / t.branchSection[branch] >= BARRIER_KG_PER_M2) {
            quenched++;
            return true;
        }
        return false;
    }

    private void burn(int node) {
        if (count == nodes.length) {
            nodes = Arrays.copyOf(nodes, count * 2);
            energy = Arrays.copyOf(energy, count * 2);
            tick = Arrays.copyOf(tick, count * 2);
        }
        burned[node] = true;
        nodes[count] = node;
        tick[count] = ticks;
        energy[count++] = field.burn(node);
    }

    /** Сколько узлов прошёл фронт. */
    public int burnedCount() {
        return count;
    }

    /** i-й сгоревший узел (в порядке фронта). */
    public int burnedNode(int i) {
        return nodes[i];
    }

    /** Энергия в разрушение i-го узла, Дж. */
    public double energyJ(int i) {
        return energy[i];
    }

    /** Тик, на котором сгорел i-й узел (0 — узел воспламенения). */
    public int burnedAtTick(int i) {
        return tick[i];
    }

    /** Узел прошёл фронт. */
    public boolean burned(int node) {
        return burned[node];
    }

    /** Сколько раз фронт погасили заслоны. */
    public int quenchedByBarriers() {
        return quenched;
    }

    /** Суммарная энергия в разрушение, Дж. */
    public double totalEnergyJ() {
        double s = 0;
        for (int i = 0; i < count; i++) s += energy[i];
        return s;
    }
}
