package dev.denfry.promptcritical.sim.automation.plc;

import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.DT;
import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.NO_AI;
import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.di;
import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.running;
import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.scan;
import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/** ST (IEC 61131-3 §3.3): те же программы, что на IL в {@link PlcCpuTest}, дают те же выходы. */
class StCompilerTest {

    /** Прогоняет IL и ST одной последовательностью входов и сравнивает выходы, биты и слова после каждого цикла. */
    private static void sameAs(String il, String st, double[] ai, IntPredicate... steps) {
        PlcCpu a = running(il), b = running(st);
        assertThat(PlcLanguage.of(st)).isEqualTo(PlcLanguage.ST);
        assertThat(PlcLanguage.of(il)).isEqualTo(PlcLanguage.IL);
        for (int k = 0; k < steps.length; k++) {
            boolean[] in = di(steps[k]);
            a.scan(DT, true, in, ai);
            b.scan(DT, true, in, ai);
            for (int i = 0; i < IlCompiler.QX; i++) assertThat(b.output(i)).as("step %d QX%d", k, i).isEqualTo(a.output(i));
            for (int i = 0; i < IlCompiler.QW; i++) assertThat(b.analogOutput(i)).as("step %d QW%d", k, i).isEqualTo(a.analogOutput(i));
            for (int i = 0; i < IlCompiler.MW; i++) assertThat(b.word(i)).as("step %d MW%d", k, i).isEqualTo(a.word(i));
        }
    }

    private static IntPredicate[] repeat(int n, IntPredicate p) {
        IntPredicate[] r = new IntPredicate[n];
        java.util.Arrays.fill(r, p);
        return r;
    }

    private static IntPredicate[] concat(IntPredicate[]... parts) {
        return java.util.Arrays.stream(parts).flatMap(java.util.Arrays::stream).toArray(IntPredicate[]::new);
    }

    @Test
    void sealInMatchesIl() {
        sameAs("""
                LD   %IX0.0
                OR   %QX0.0
                AND  %IX0.1
                ST   %QX0.0
                """, """
                PROGRAM SealIn
                VAR
                    Start AT %IX0.0 : BOOL;
                    Stop  AT %IX0.1 : BOOL;   (* НЗ кнопка *)
                    Motor AT %QX0.0 : BOOL;
                END_VAR
                Motor := (Start OR Motor) AND Stop;
                END_PROGRAM
                """, NO_AI, concat(repeat(2, i -> i == 1), repeat(1, i -> i <= 1), repeat(5, i -> i == 1), repeat(2, i -> false)));
    }

    @Test
    void timerMatchesIl() {
        PlcCpu cpu = running("""
                (*ST*)
                VAR T1 : TON; END_VAR
                T1(IN := %IX0.0, PT := T#2S);
                %QX0.0 := T1.Q;
                """);
        scan(cpu, di(i -> i == 0), 39);
        assertThat(cpu.output(0)).as("1.95 s").isFalse();
        scan(cpu, di(i -> i == 0), 1);
        assertThat(cpu.output(0)).as("2.00 s").isTrue();
        sameAs("""
                CAL TOF1(IN := %IX0.0, PT := T#500MS)
                LD  TOF1.Q
                ST  %QX0.0
                CAL TP2(IN := %IX0.1, PT := T#1S)
                LD  TP2.Q
                ST  %QX0.1
                """, """
                {ST}
                TOF1(IN := %IX0.0, PT := T#500ms, Q => %QX0.0);
                TP2(IN := %IX0.1, PT := T#1s);
                %QX0.1 := TP2.Q;
                """, NO_AI, concat(repeat(1, i -> i <= 1), repeat(25, i -> false)));
    }

    @Test
    void counterMatchesIl() {
        IntPredicate[] pulses = concat(repeat(2, i -> i == 0), repeat(1, i -> false), repeat(2, i -> i == 0), repeat(1, i -> false),
                repeat(2, i -> i == 0), repeat(1, i -> false), repeat(1, i -> i == 1));
        sameAs("""
                CAL CTU0(CU := %IX0.0, R := %IX0.1, PV := 3)
                LD  CTU0.Q
                ST  %QX0.0
                LD  CTU0.CV
                ST  %MW0
                """, """
                PROGRAM Count
                CTU0(CU := %IX0.0, R := %IX0.1, PV := 3);
                %QX0.0 := CTU0.Q;
                %MW0 := CTU0.CV;
                END_PROGRAM
                """, NO_AI, pulses);
    }

    @Test
    void pidMatchesIl() {
        double[] ai = {3.5, 0, 0, 0};
        sameAs("""
                CAL PID0(PV := %IW0, SP := 6, KP := 2, TI := 4, LO := 0, HI := 10)
                LD  PID0.Y
                ST  %QW0
                """, """
                PROGRAM Loop
                VAR Tic : PID; END_VAR
                Tic(PV := %IW0, SP := 6, KP := 2, TI := 4, LO := 0, HI := 10);
                %QW0 := Tic.Y;
                """, ai, repeat(40, i -> false));
    }

    @Test
    void precedenceFollowsIec() {
        PlcCpu cpu = running("""
                PROGRAM P
                %MW0 := 2 + 3 * 4;            (* 14 *)
                %MW1 := (2 + 3) * 4;          (* 20 *)
                %MW2 := 10 - 4 - 3;           (* 3, слева направо *)
                %MW3 := -2 * 3 + 17 MOD 5;    (* -6 + 2 = -4 *)
                %MW4 := 1 + 2 * (3 + 4 * (5 - 1));   (* 39 *)
                %QX0.0 := %IX0.0 OR %IX0.1 AND %IX0.2;   (* AND выше OR *)
                %QX0.1 := NOT %IX0.0 AND %IX0.1;         (* NOT выше AND *)
                %QX0.2 := 1 + 1 = 2 AND 3 > 2;
                %QX0.3 := %IX0.0 XOR %IX0.1 OR %IX0.2;   (* XOR выше OR *)
                %QX0.4 := NOT (%IX0.0 & %IX0.1);
                END_PROGRAM
                """);
        scan(cpu, di(i -> i == 0), 1);
        assertThat(cpu.word(0)).isEqualTo(14);
        assertThat(cpu.word(1)).isEqualTo(20);
        assertThat(cpu.word(2)).isEqualTo(3);
        assertThat(cpu.word(3)).isEqualTo(-4);
        assertThat(cpu.word(4)).isEqualTo(39);
        assertThat(cpu.output(0)).as("a OR (b AND c)").isTrue();
        assertThat(cpu.output(1)).as("(NOT a) AND b").isFalse();
        assertThat(cpu.output(2)).isTrue();
        assertThat(cpu.output(3)).isTrue();
        assertThat(cpu.output(4)).isTrue();
    }

    @Test
    void controlFlow() {
        PlcCpu cpu = running("""
                PROGRAM Flow
                VAR Sel AT %IW0 : INT; i AT %MW9 : INT; END_VAR
                IF %IX0.0 THEN %MW0 := 1;
                ELSIF %IX0.1 THEN %MW0 := 2;
                ELSE %MW0 := 3;
                END_IF;
                CASE Sel OF
                    1: %MW1 := 10;
                    2, 3: %MW1 := 20;
                    4..6: %MW1 := 30;
                ELSE %MW1 := -1;
                END_CASE;
                %MW2 := 0;
                FOR i := 1 TO 10 DO %MW2 := %MW2 + i; END_FOR;      (* 55 *)
                %MW3 := 0;
                FOR i := 10 TO 1 BY -3 DO %MW3 := %MW3 + 1; END_FOR;  (* 10 7 4 1 *)
                %MW4 := 1;
                WHILE %MW4 < 100 DO %MW4 := %MW4 * 2; END_WHILE;      (* 128 *)
                %MW5 := 0;
                REPEAT %MW5 := %MW5 + 1; IF %MW5 >= 7 THEN EXIT; END_IF; UNTIL FALSE END_REPEAT;
                END_PROGRAM
                """);
        double[] ai = {5, 0, 0, 0};
        cpu.scan(DT, true, di(i -> i == 1), ai);
        assertThat(cpu.word(0)).isEqualTo(2);
        assertThat(cpu.word(1)).isEqualTo(30);
        assertThat(cpu.word(2)).isEqualTo(55);
        assertThat(cpu.word(3)).isEqualTo(4);
        assertThat(cpu.word(4)).isEqualTo(128);
        assertThat(cpu.word(5)).isEqualTo(7);
        ai[0] = 9;
        cpu.scan(DT, true, di(i -> false), ai);
        assertThat(cpu.word(0)).isEqualTo(3);
        assertThat(cpu.word(1)).isEqualTo(-1);
        ai[0] = 3;
        cpu.scan(DT, true, di(i -> i == 0), ai);
        assertThat(cpu.word(0)).isEqualTo(1);
        assertThat(cpu.word(1)).isEqualTo(20);
    }

    @Test
    void endlessLoopTripsTheWatchdog() {
        PlcCpu cpu = running("""
                PROGRAM Hang
                WHILE TRUE DO %MW0 := %MW0 + 1; END_WHILE;
                END_PROGRAM
                """);
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.mode()).isEqualTo(PlcCpu.Mode.FAULT);
        cpu.setRunKey(false);
        cpu.setRunKey(true);
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.mode()).as("faults again on the next run").isEqualTo(PlcCpu.Mode.FAULT);
    }

    @Test
    void errorsCarryLineNumbers() {
        IlError e = StCompiler.compile("PROGRAM P\n%QX0.0 := %IX0.0;\nIF %IX0.1 %QX0.1 := TRUE; END_IF;\n").errors().get(0);
        assertThat(e.line()).isEqualTo(3);
        assertThat(e.kind()).isEqualTo(IlError.Kind.SYNTAX);
        assertThat(e.token()).contains("THEN");

        e = StCompiler.compile("PROGRAM P\n\n%IX0.0 := TRUE;\n").errors().get(0);
        assertThat(e.line()).isEqualTo(3);
        assertThat(e.kind()).isEqualTo(IlError.Kind.NOT_WRITABLE);

        e = StCompiler.compile("PROGRAM P\n%QX0.9 := TRUE;\n").errors().get(0);
        assertThat(e.kind()).isEqualTo(IlError.Kind.BAD_OPERAND);

        e = StCompiler.compile("PROGRAM P\n(* open\ncomment\n").errors().get(0);
        assertThat(e.line()).isEqualTo(2);
        String fits = "PROGRAM P\n%MW0 := 1" + " + (1".repeat(9) + ")".repeat(9) + ";\n";
        assertThat(StCompiler.compile(fits).errors()).as("eight brackets fit").isEmpty();

        String deep = "PROGRAM P\n%MW0 := 1" + " + (1".repeat(10) + ")".repeat(10) + ";\n";
        assertThat(StCompiler.compile(deep).errors().get(0).kind()).isEqualTo(IlError.Kind.NESTING_TOO_DEEP);

        String longProgram = "PROGRAM P\n" + "%MW0 := %MW0 + 1;\n".repeat(200);
        assertThat(StCompiler.compile(longProgram).errors().get(0).kind()).isEqualTo(IlError.Kind.TOO_LONG);
        assertThat(StCompiler.compile("PROGRAM P\n" + " ".repeat(IlCompiler.MAX_CHARS)).errors().get(0).kind())
                .isEqualTo(IlError.Kind.TOO_LONG);
    }

    @Test
    void ilModWorks() {
        PlcCpu cpu = running("LD 17\nMOD 5\nST %MW0\n");
        scan(cpu, di(i -> false), 1);
        assertThat(cpu.word(0)).isEqualTo(2);
    }
}
