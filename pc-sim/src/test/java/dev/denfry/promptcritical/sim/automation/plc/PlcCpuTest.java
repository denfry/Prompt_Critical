package dev.denfry.promptcritical.sim.automation.plc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/** ПЛК A3: компилятор IL (IEC 61131-3) и процессор со сканированием, таймерами, счётчиками, ПИД и памятью. */
class PlcCpuTest {
    static final double DT = 0.05;

    static PlcCpu running(String src) {
        PlcCpu cpu = new PlcCpu();
        IlProgram p = cpu.load(src);
        assertThat(p.errors()).as("compile errors").isEmpty();
        return cpu;
    }

    static boolean[] di(IntPredicate on) {
        boolean[] d = new boolean[IlCompiler.IX];
        for (int i = 0; i < d.length; i++) d[i] = on.test(i);
        return d;
    }

    static final double[] NO_AI = new double[IlCompiler.IW];

    static void scan(PlcCpu cpu, boolean[] in, int n) {
        for (int i = 0; i < n; i++) cpu.scan(DT, true, in, NO_AI);
    }

    @Test
    void selfHoldingStartStop() {
        // классическая самоподхватка: пуск %IX0.0, стоп (НЗ кнопка) %IX0.1, контактор %QX0.0
        PlcCpu cpu = running("""
                LD   %IX0.0
                OR   %QX0.0
                AND  %IX0.1   (* стоп — НЗ: под напряжением, пока не нажата *)
                ST   %QX0.0
                """);
        scan(cpu, di(i -> i == 1), 2);
        assertThat(cpu.output(0)).isFalse();
        scan(cpu, di(i -> i == 0 || i == 1), 1);
        assertThat(cpu.output(0)).isTrue();
        scan(cpu, di(i -> i == 1), 5);
        assertThat(cpu.output(0)).as("held after start released").isTrue();
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.output(0)).isFalse();
    }

    @Test
    void onDelayTimerRunsInSeconds() {
        PlcCpu cpu = running("""
                CAL TON0(IN := %IX0.0, PT := T#2S)
                LD  TON0.Q
                ST  %QX0.0
                """);
        scan(cpu, di(i -> i == 0), 39);
        assertThat(cpu.output(0)).as("1.95 s").isFalse();
        scan(cpu, di(i -> i == 0), 1);
        assertThat(cpu.output(0)).as("2.00 s").isTrue();
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.output(0)).isFalse();
    }

    @Test
    void offDelayAndPulseTimers() {
        PlcCpu cpu = running("""
                CAL TOF1(IN := %IX0.0, PT := T#500MS)
                LD  TOF1.Q
                ST  %QX0.0
                CAL TP2(IN := %IX0.1, PT := T#1S)
                LD  TP2.Q
                ST  %QX0.1
                """);
        scan(cpu, di(i -> i == 0 || i == 1), 1);
        assertThat(cpu.output(0)).isTrue();
        assertThat(cpu.output(1)).isTrue();
        scan(cpu, di(i -> false), 9);
        assertThat(cpu.output(0)).as("0.45 s after release").isTrue();
        assertThat(cpu.output(1)).as("pulse runs on without input").isTrue();
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.output(0)).isFalse();
        scan(cpu, di(i -> false), 10);
        assertThat(cpu.output(1)).as("pulse ended after 1 s").isFalse();
    }

    @Test
    void counterCountsRisingEdgesAndResets() {
        PlcCpu cpu = running("""
                CAL CTU0(CU := %IX0.0, R := %IX0.1, PV := 3)
                LD  CTU0.Q
                ST  %QX0.0
                LD  CTU0.CV
                ST  %MW0
                """);
        for (int k = 0; k < 3; k++) {
            scan(cpu, di(i -> i == 0), 2); // держим — один фронт
            scan(cpu, di(i -> false), 1);
        }
        assertThat(cpu.word(0)).isEqualTo(3);
        assertThat(cpu.output(0)).isTrue();
        scan(cpu, di(i -> i == 1), 1);
        assertThat(cpu.word(0)).isZero();
        assertThat(cpu.output(0)).isFalse();
    }

    @Test
    void parenthesesJumpsAndArithmetic() {
        PlcCpu cpu = running("""
                LD   %IX0.0
                AND( %IX0.1
                OR   %IX0.2
                )
                ST   %QX0.0          // a AND (b OR c)
                LD   2.5
                MUL  4
                ADD  0.5
                ST   %MW1            // 10.5
                LD   %MW1
                GT   10
                JMPCN skip
                LD   TRUE
                ST   %QX0.1
                skip:
                LD   %IX0.3
                RETC
                LD   TRUE
                ST   %QX0.2
                """);
        scan(cpu, di(i -> i == 0 || i == 2), 1);
        assertThat(cpu.output(0)).isTrue();
        assertThat(cpu.word(1)).isEqualTo(10.5);
        assertThat(cpu.output(1)).isTrue();
        assertThat(cpu.output(2)).isTrue();
        scan(cpu, di(i -> i == 0 || i == 3), 1);
        assertThat(cpu.output(0)).as("b and c both off").isFalse();
    }

    @Test
    void setResetAndNegatedOperands() {
        PlcCpu cpu = running("""
                LD  %IX0.0
                S   %QX0.0
                LD  %IX0.1
                R   %QX0.0
                LDN %IX0.2
                ST  %QX0.1
                LD  %IX0.2
                STN %QX0.2
                """);
        scan(cpu, di(i -> i == 0), 1);
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.output(0)).isTrue();
        assertThat(cpu.output(1)).isTrue();
        assertThat(cpu.output(2)).isTrue();
        scan(cpu, di(i -> i == 1 || i == 2), 1);
        assertThat(cpu.output(0)).isFalse();
        assertThat(cpu.output(1)).isFalse();
        assertThat(cpu.output(2)).isFalse();
    }

    @Test
    void endlessLoopTripsTheWatchdogUntilKeyCycled() {
        PlcCpu cpu = running("""
                again:
                LD  TRUE
                ST  %QX0.0
                JMP again
                """);
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.mode()).isEqualTo(PlcCpu.Mode.FAULT);
        assertThat(cpu.output(0)).isFalse();
        scan(cpu, di(i -> false), 3);
        assertThat(cpu.mode()).as("stays faulted").isEqualTo(PlcCpu.Mode.FAULT);
        cpu.load("LD TRUE\nST %QX0.0");
        cpu.setRunKey(false);
        cpu.setRunKey(true);
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.mode()).isEqualTo(PlcCpu.Mode.RUN);
        assertThat(cpu.output(0)).isTrue();
    }

    @Test
    void powerLossKeepsRetentiveMemoryOnly() {
        PlcCpu cpu = running("""
                LD 7
                ST %MW0
                ST %MW20
                LD TRUE
                ST %MX1
                ST %MX40
                CAL CTU3(CU := %IX0.0, PV := 100)
                """);
        scan(cpu, di(i -> i == 0), 1);
        cpu.scan(DT, false, di(i -> false), NO_AI);
        assertThat(cpu.mode()).isEqualTo(PlcCpu.Mode.OFF);
        assertThat(cpu.word(0)).isZero();
        assertThat(cpu.word(20)).isEqualTo(7);
        assertThat(cpu.marker(1)).isFalse();
        assertThat(cpu.marker(40)).isTrue();
        cpu.load("LD CTU3.CV\nST %MW2");
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.word(2)).as("counter kept by the battery").isEqualTo(1);
    }

    @Test
    void stopKeyDropsOutputsAndErrorsKeepItStopped() {
        PlcCpu cpu = running("LD TRUE\nST %QX0.0");
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.output(0)).isTrue();
        cpu.setRunKey(false);
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.mode()).isEqualTo(PlcCpu.Mode.STOP);
        assertThat(cpu.output(0)).isFalse();
        cpu.setRunKey(true);
        IlProgram bad = cpu.load("LD TRUE\nST %IX0.0");
        scan(cpu, di(i -> false), 1);
        assertThat(bad.ok()).isFalse();
        assertThat(cpu.mode()).isEqualTo(PlcCpu.Mode.STOP);
    }

    @Test
    void compilerReportsLineAndKind() {
        IlProgram p = IlCompiler.compile("""
                LD %IX0.0
                FOO %QX0.0
                ST %IX0.1
                AND(
                JMP nowhere
                LD %IX0.9
                CAL TON99(IN := TRUE)
                """);
        assertThat(p.errors()).extracting(IlError::line, IlError::kind).contains(
                org.assertj.core.groups.Tuple.tuple(2, IlError.Kind.UNKNOWN_OPERATOR),
                org.assertj.core.groups.Tuple.tuple(3, IlError.Kind.NOT_WRITABLE),
                org.assertj.core.groups.Tuple.tuple(6, IlError.Kind.BAD_OPERAND),
                org.assertj.core.groups.Tuple.tuple(7, IlError.Kind.BAD_CALL));
        assertThat(p.errors()).extracting(IlError::kind).contains(IlError.Kind.UNBALANCED_PARENTHESIS, IlError.Kind.UNKNOWN_LABEL);
        assertThat(IlCompiler.compile("LD 1\n".repeat(IlCompiler.MAX_INSTR + 1)).errors())
                .extracting(IlError::kind).contains(IlError.Kind.TOO_LONG);
    }

    @Test
    void pidHoldsAFirstOrderPlantAtSetpointWithoutWindup() {
        // объект первого порядка T = 5 с, K = 1, в вольтах; ПИ K_p = 2, T_i = 4 с
        PlcCpu cpu = running("""
                CAL PID0(PV := %IW0, SP := %MW0, KP := 2, TI := 4, LO := 0, HI := 10)
                LD  PID0.Y
                ST  %QW0
                """);
        double pv = 0;
        double[] ai = new double[IlCompiler.IW];
        cpu.load(cpu.program().source().replace("%MW0", "6"));
        for (int k = 0; k < 20 * 60; k++) {
            ai[0] = pv;
            cpu.scan(DT, true, di(i -> false), ai);
            pv += (cpu.analogOutput(0) - pv) / 5 * DT;
        }
        assertThat(pv).isCloseTo(6, within(0.05));
        assertThat(cpu.analogOutput(0)).isBetween(0.0, 10.0);
    }
}
