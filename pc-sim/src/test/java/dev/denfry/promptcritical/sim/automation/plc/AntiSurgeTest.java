package dev.denfry.promptcritical.sim.automation.plc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** {@code PC_ANTISURGE} (03d §3d.5.12): запас, медленное закрытие, линия отскока T-3d-19, обнаружение помпажа, языки. */
class AntiSurgeTest {
    static final double DT = 0.05;

    /** Блок с линией помпажа q² = 2·(R_c − 1); давления в барах, перепад в кПа. */
    static AntiSurge.Instance block() {
        AntiSurge.Instance b = new AntiSurge.Instance();
        b.in[AntiSurge.KS] = 2;
        b.in[AntiSurge.PS] = 5;
        return b;
    }

    /** Рабочая точка: R_c и запас S_s заданы, перепад подобран. */
    static void point(AntiSurge.Instance b, double rc, double ss) {
        b.in[AntiSurge.PD] = 5 * rc;
        b.in[AntiSurge.DPO] = 5 * 2 * (rc - 1) / ss;
    }

    @Test
    void reducedCoordinatesGiveTheSurgeMarginAndDeviation() {
        AntiSurge.Instance b = block();
        point(b, 1.8, 0.5);
        b.scan(DT);
        assertThat(b.out[AntiSurge.SS]).isCloseTo(0.5, within(1e-12));
        assertThat(b.out[AntiSurge.DEV]).isCloseTo(1 / 1.1 - 0.5, within(1e-12));
    }

    @Test
    void opensFastButClosesNoFasterThanTheCloseRate() {
        AntiSurge.Instance b = block();
        point(b, 1.8, 0.4);
        b.scan(DT);
        assertThat(b.y()).as("valve open on the first scan").isCloseTo(10, within(0.01));
        for (int k = 0; k < 200; k++) b.scan(DT);
        // 10 с при 1 %/с: закрылся ровно на 10 % хода
        assertThat(b.y()).isCloseTo(10 - 0.01 * 10 * 10, within(0.06));
        for (int k = 0; k < 3000; k++) b.scan(DT);
        assertThat(b.y()).isZero();
        point(b, 1.8, 0.95);
        b.scan(DT);
        assertThat(b.y()).as("opens at once").isGreaterThan(2);
    }

    @Test
    void recycleTripLineStepsThirtyPercentAndDoublesWithinTenSeconds() {
        // T-3d-19: q² падает и пересекает линию отскока — выход +30 % хода в том же скане; повторно за 10 с — +60 %
        AntiSurge.Instance b = block();
        b.in[AntiSurge.KP] = 0;
        b.in[AntiSurge.TI] = 0;
        point(b, 1.8, 0.5);
        closeByHand(b);
        assertThat(b.y()).isZero();
        point(b, 1.8, 0.99);
        b.scan(DT);
        assertThat(b.y()).isCloseTo(3, within(1e-9));
        point(b, 1.8, 0.9);
        for (int k = 0; k < 40; k++) b.scan(DT);
        double before = b.y();
        point(b, 1.8, 0.99);
        b.scan(DT);
        assertThat(b.y() - before).isCloseTo(6, within(0.01));
        // через 10 с без пересечений шаг снова 30 %
        point(b, 1.8, 0.5);
        for (int k = 0; k < 250; k++) b.scan(DT);
        closeByHand(b);
        point(b, 1.8, 0.99);
        b.scan(DT);
        assertThat(b.y()).isCloseTo(3, within(1e-9));
    }

    /** Без ПИ (K_p = 0) выход стоит на месте: закрыть клапан ручным режимом и вернуть в автомат. */
    static void closeByHand(AntiSurge.Instance b) {
        b.in[AntiSurge.MAN] = 1;
        b.in[AntiSurge.XM] = 0;
        b.scan(DT);
        b.in[AntiSurge.MAN] = 0;
        b.scan(DT);
    }

    @Test
    void flowCollapseCountsASurgeAndShiftsTheControlLine() {
        AntiSurge.Instance b = block();
        point(b, 1.8, 0.8);
        for (int k = 0; k < 10; k++) b.scan(DT);
        double dev0 = b.out[AntiSurge.DEV];
        b.in[AntiSurge.DPO] = 0; // реверс: дифманометр в упоре
        b.scan(DT);
        assertThat(b.out[AntiSurge.CNT]).isEqualTo(1);
        point(b, 1.8, 0.8);
        b.scan(DT);
        assertThat(b.out[AntiSurge.DEV]).as("b_ctrl + 2 %").isCloseTo(dev0 - (1 / 1.1 - 1 / 1.12), within(1e-12));
        b.in[AntiSurge.DPO] = 0;
        b.scan(DT);
        assertThat(b.out[AntiSurge.CNT]).isEqualTo(2);
    }

    @Test
    void manualModeHoldsTheOutputAndTransfersBumplessly() {
        AntiSurge.Instance b = block();
        point(b, 1.8, 0.85);
        b.in[AntiSurge.MAN] = 1;
        b.in[AntiSurge.XM] = 4;
        for (int k = 0; k < 20; k++) b.scan(DT);
        assertThat(b.y()).isEqualTo(4);
        b.in[AntiSurge.MAN] = 0;
        b.scan(DT);
        assertThat(b.y()).isCloseTo(4, within(0.05));
    }

    @Test
    void stIlAndFbdRunTheSameBlock() {
        double[] ai = {5 * 0.4, 1, 1.8, 0};
        String st = """
                PROGRAM As
                VAR A : PC_ANTISURGE; END_VAR
                A(DPO := %IW0, PS := %IW1, PD := %IW2, KS := 2, RATE := 0.1);
                %QW0 := A.Y;
                %MW0 := A.SS;
                """;
        String il = """
                CAL PC_ANTISURGE0(DPO := %IW0, PS := %IW1, PD := %IW2, KS := 2, RATE := 0.1)
                LD  PC_ANTISURGE0.Y
                ST  %QW0
                """;
        String fbd = """
                (*FBD*)
                A1 := PC_ANTISURGE(%IW0, %IW1, %IW2, 2, 0.1, 0.03, 10, 5, 0.1)
                %QW0 := A1.Y
                """;
        double[] y = new double[3];
        String[] src = {st, il, fbd};
        for (int i = 0; i < 3; i++) {
            PlcCpu cpu = PlcCpuTest.running(src[i]);
            for (int k = 0; k < 100; k++) cpu.scan(DT, true, new boolean[IlCompiler.IX], ai);
            y[i] = cpu.analogOutput(0);
        }
        assertThat(y[0]).isLessThan(10).isGreaterThan(0);
        assertThat(y[1]).isEqualTo(y[0]);
        assertThat(y[2]).isEqualTo(y[0]);
    }

    @Test
    void blockStateSurvivesSaveAndLoad() {
        PlcCpu cpu = PlcCpuTest.running("""
                CAL PID3(PV := %IW0, SP := 6, KP := 2, TI := 4)
                LD  PID3.Y
                ST  %QW0
                """);
        double[] ai = {3.5, 0, 0, 0};
        for (int k = 0; k < 40; k++) cpu.scan(DT, true, new boolean[IlCompiler.IX], ai);
        MutableStateTree tree = new MutableStateTree();
        cpu.save(new TreeStateWriter(tree));
        PlcCpu b = new PlcCpu();
        b.load(new TreeStateReader(tree));
        cpu.scan(DT, true, new boolean[IlCompiler.IX], ai);
        b.scan(DT, true, new boolean[IlCompiler.IX], ai);
        assertThat(b.analogOutput(0)).isEqualTo(cpu.analogOutput(0));
    }
}
