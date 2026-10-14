package dev.denfry.promptcritical.sim.mining;

/** Сборка тестовых шахт. */
public final class Mines {
    /** Шаг SLOW: 1 с реального времени при K_slow = 72. */
    static final double K_SLOW = 72;
    static final double T = 293.15;
    /** Сечение и периметр типового штрека. */
    static final double S = 9, P = 12;

    private Mines() {}

    /** Ветвь с заданным сопротивлением R (α подобрано), сечение 9 м², периметр 12 м, длина 100 м. */
    static int branchR(MineTopology.Builder b, long key, int from, int to, double r, BranchKind kind) {
        double alpha = r * S * S * S / (P * 100);
        return b.branch(key, from, to, 100, S, P, alpha, kind);
    }

    /** Узел-объём на отметке 0 при 20 °C. */
    static int node(MineTopology.Builder b, long key, double volume) {
        return b.node(key, volume, 0, S, P, T);
    }

    /** Ключ блока {@code i} узла {@code node}. */
    static long block(int node, int i) {
        return ((long) node << 32) | i;
    }

    /** Решить сеть до сходимости. */
    static void settle(MineVentilation v) {
        for (int k = 0; k < 20; k++) v.solveSlow(K_SLOW, 10);
    }

    /**
     * Эталонная шахта 08 §8.18: 40 узлов двух параллельных штреков (поступающий I и исходящий R), 20 лав-сбоек,
     * 4 тупика, стволы и вентилятор главного проветривания — 45 узлов с поверхностью, 64 ветви;
     * 2000 обнажённых блоков угля на узлах исходящего штрека.
     */
    public static final class Reference {
        final MineTopology topology;
        public final MineVentilation ventilation = new MineVentilation();
        public final MethaneField field = new MethaneField(MiningConfig.defaults());
        final int fan;
        public final int[] intake = new int[20], ret = new int[20];

        public Reference() {
            MineTopology.Builder b = MineTopology.builder(64, 283);
            long key = 1;
            for (int k = 0; k < 20; k++) {
                intake[k] = b.node(key++, 900, -20 - k, S, P, 290 + 0.2 * k);
                ret[k] = b.node(key++, 900, -20 - k, S, P, 292 + 0.2 * k);
            }
            int[] dead = new int[4];
            for (int d = 0; d < 4; d++) dead[d] = b.node(key++, 400, -25 - 5 * d, S, P, 295);
            long bk = 1;
            branchR(b, bk++, MineTopology.ATMOSPHERE, intake[0], 0.05, BranchKind.SHAFT);
            int fanBranch = branchR(b, bk++, ret[0], MineTopology.ATMOSPHERE, 0.05, BranchKind.SHAFT);
            for (int k = 0; k < 19; k++) {
                branchR(b, bk++, intake[k], intake[k + 1], 0.02, BranchKind.DRIFT);
                branchR(b, bk++, ret[k + 1], ret[k], 0.02, BranchKind.DRIFT);
            }
            for (int k = 0; k < 20; k++) {
                int face = b.branch(bk++, intake[k], ret[k], 150, 6, 10, AirwayLining.POWERED_FACE.alpha,
                        BranchKind.FACE);
                if (k % 5 == 0) b.door(face, DoorKind.REGULATOR);
            }
            for (int d = 0; d < 4; d++) branchR(b, bk++, intake[5 * d + 4], dead[d], 0.1, BranchKind.DRIFT);
            fan = b.fan(fanBranch, FanSpec.of(3000, 120, 600, 0.75));
            for (int k = 0; k < 20; k++)
                for (int i = 0; i < 100; i++) b.assignBlock(block(ret[k], i), ret[k]);
            topology = b.build();
            ventilation.rebuild(topology);
            field.rebuild(topology);
            ventilation.setFanSpeed(fan, 600);
            for (int d = 0; d < topology.doorCount(); d++) ventilation.setRegulatorArea(d, 3);
            for (int k = 0; k < 20; k++)
                for (int i = 0; i < 100; i++) field.onExposed(block(ret[k], i), 15, 0);
        }

        public void tick() {
            ventilation.solveSlow(K_SLOW, 10);
            field.stepSlow(K_SLOW, ventilation);
        }
    }
}
