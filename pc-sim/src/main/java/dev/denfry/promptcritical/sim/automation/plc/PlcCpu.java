package dev.denfry.promptcritical.sim.automation.plc;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Instr;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Op;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Operand;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;
import java.util.List;

/**
 * Процессор ПЛК (A3): циклическое сканирование с образом процесса, как у ПЛК с конца 1960-х (Modicon 084, 1969;
 * Simatic S5, 1979). Цикл в RUN: снять входы в образ → исполнить программу сверху вниз → выдать образ выходов.
 * Выходы меняются только в конце цикла, поэтому внутри цикла программа видит один и тот же снимок входов.
 *
 * <p>Режимы: OFF (нет питания 24 В), STOP (ключ в STOP или программа с ошибками — выходы сброшены), RUN, FAULT
 * (сработал сторожевой таймер: за цикл больше {@link #WATCHDOG} инструкций — бесконечный цикл; выходы сброшены до
 * перевода ключа в STOP и обратно). Память делится как в S5: при пропадании питания сбрасываются биты
 * {@code %MX0…31}, слова {@code %MW0…15}, таймеры, фронты и регуляторы; «реманентные» {@code %MX32…63},
 * {@code %MW16…31} и счётчики держит батарея.
 *
 * <p>Исполнение без аллокаций; программа — {@link IlProgram}, компилируется при загрузке текста.
 */
public final class PlcCpu {
    /** Предел инструкций за цикл: сторожевой таймер. */
    public static final int WATCHDOG = 4096;
    public static final int RETAIN_MX = 32, RETAIN_MW = 16;
    /** Ширина входов, выходов и состояния экземпляра блока (под самый широкий — {@code PC_ANTISURGE}). */
    private static final int IN_W = 12, OUT_W = 4, ST_W = 8;
    /** Раскладка до {@code PC_ANTISURGE}: 8 типов, ширины 9/2/4 — так сохранены старые миры. */
    private static final int OLD_KINDS = 8, OLD_IN_W = 9, OLD_OUT_W = 2, OLD_ST_W = 4, LAYOUT = 2;

    public enum Mode { OFF, STOP, RUN, FAULT }

    private IlProgram program = IlProgram.EMPTY;
    private boolean runKey = true;
    private Mode mode = Mode.OFF;

    private final boolean[] ix = new boolean[IlCompiler.IX], qx = new boolean[IlCompiler.QX], mx = new boolean[IlCompiler.MX];
    private final double[] iw = new double[IlCompiler.IW], qw = new double[IlCompiler.QW], mw = new double[IlCompiler.MW];
    private final double[] fbIn = new double[Fb.COUNT * IN_W], fbOut = new double[Fb.COUNT * OUT_W], fbSt = new double[Fb.COUNT * ST_W];
    private final Op[] stOp = new Op[IlCompiler.MAX_DEPTH];
    private final boolean[] stNeg = new boolean[IlCompiler.MAX_DEPTH];
    private final double[] stCr = new double[IlCompiler.MAX_DEPTH];
    private int lastExecuted;
    private double dt;

    public PlcCpu() {
        coldDefaults();
    }

    // ---------------------------------------------------------------- программа и ключ

    /** Загрузить текст программы. Возвращает результат компиляции; с ошибками ПЛК встаёт в STOP. */
    public IlProgram load(String source) {
        program = PlcLanguage.compile(source);
        if (mode == Mode.RUN || mode == Mode.FAULT) mode = Mode.STOP;
        clearOutputs();
        return program;
    }

    public IlProgram program() {
        return program;
    }

    /** Ключ RUN/STOP на ПЛК. STOP сбрасывает FAULT; RUN запускает, если есть питание и программа без ошибок. */
    public void setRunKey(boolean run) {
        runKey = run;
        if (!run && mode != Mode.OFF) {
            mode = Mode.STOP;
            clearOutputs();
        }
    }

    public boolean runKey() {
        return runKey;
    }

    public Mode mode() {
        return mode;
    }

    public int lastExecuted() {
        return lastExecuted;
    }

    // ---------------------------------------------------------------- цикл

    /**
     * Один цикл сканирования за шаг {@code dtS}. {@code powered} — есть 24 В на L+/M; {@code di} и {@code ai} —
     * входы модулей (длины {@link IlCompiler#IX}, {@link IlCompiler#IW}).
     */
    public void scan(double dtS, boolean powered, boolean[] di, double[] ai) {
        if (!powered) {
            if (mode != Mode.OFF) powerLoss();
            mode = Mode.OFF;
            return;
        }
        System.arraycopy(di, 0, ix, 0, ix.length);
        System.arraycopy(ai, 0, iw, 0, iw.length);
        if (mode == Mode.OFF) mode = Mode.STOP;
        if (mode == Mode.STOP && runKey && program.ok()) mode = Mode.RUN;
        if (mode != Mode.RUN) {
            clearOutputs();
            return;
        }
        dt = dtS;
        if (!execute(program.code())) {
            mode = Mode.FAULT;
            clearOutputs();
        }
    }

    public boolean output(int i) {
        return mode == Mode.RUN && qx[i];
    }

    public double analogOutput(int i) {
        return mode == Mode.RUN ? qw[i] : 0;
    }

    public boolean input(int i) {
        return ix[i];
    }

    public double analogInput(int i) {
        return iw[i];
    }

    public boolean marker(int i) {
        return mx[i];
    }

    public double word(int i) {
        return mw[i];
    }

    /**
     * Запись маркера {@code %MXi} снаружи (верхний уровень, 03d §3d.14.1 {@code writeVar}): программа видит её на
     * ближайшем цикле и может переписать сама, как у настоящего ПЛК.
     */
    public void writeMarker(int i, boolean v) {
        mx[i] = v;
    }

    /** Запись слова {@code %MWi} снаружи; только конечные значения. */
    public void writeWord(int i, double v) {
        if (!Double.isFinite(v)) throw new IllegalArgumentException("finite value required");
        mw[i] = v;
    }

    /** Исполнение программы; {@code false} — сработал сторожевой таймер. */
    private boolean execute(List<Instr> code) {
        double cr = 0;
        int sp = 0, pc = 0, n = code.size(), executed = 0;
        while (pc < n) {
            if (++executed > WATCHDOG) {
                lastExecuted = executed;
                return false;
            }
            Instr in = code.get(pc++);
            switch (in.op()) {
                case LD -> {
                    double v = read(in.operand());
                    cr = in.negate() ? not(v) : v;
                }
                case ST -> write(in.operand(), in.negate() ? not(cr) : cr);
                case S -> {
                    if (cr != 0) write(in.operand(), 1);
                }
                case R -> {
                    if (cr != 0) write(in.operand(), 0);
                }
                case NOT -> cr = not(cr);
                case JMP -> {
                    if (!in.conditional() || (cr != 0) != in.negate()) pc = in.target();
                }
                case RET -> {
                    if (!in.conditional() || (cr != 0) != in.negate()) pc = n;
                }
                case CAL -> {
                    if (!in.conditional() || (cr != 0) != in.negate()) call(in);
                }
                case CLOSE -> {
                    sp--;
                    cr = apply(stOp[sp], stNeg[sp], stCr[sp], cr);
                }
                default -> {
                    if (in.open()) {
                        stOp[sp] = in.op();
                        stNeg[sp] = in.negate();
                        stCr[sp] = cr;
                        sp++;
                        cr = in.operand() == null ? 0 : read(in.operand());
                    } else {
                        cr = apply(in.op(), in.negate(), cr, read(in.operand()));
                    }
                }
            }
        }
        lastExecuted = executed;
        return true;
    }

    private static double apply(Op op, boolean negate, double a, double b) {
        double v = negate ? not(b) : b;
        return switch (op) {
            case AND -> a != 0 && v != 0 ? 1 : 0;
            case OR -> a != 0 || v != 0 ? 1 : 0;
            case XOR -> (a != 0) != (v != 0) ? 1 : 0;
            case ADD -> a + v;
            case SUB -> a - v;
            case MUL -> a * v;
            case DIV -> v == 0 ? 0 : a / v;
            case MOD -> v == 0 ? 0 : a % v;
            case GT -> a > v ? 1 : 0;
            case GE -> a >= v ? 1 : 0;
            case EQ -> Math.abs(a - v) <= 1e-9 * Math.max(1, Math.abs(a)) ? 1 : 0;
            case NE -> Math.abs(a - v) > 1e-9 * Math.max(1, Math.abs(a)) ? 1 : 0;
            case LE -> a <= v ? 1 : 0;
            case LT -> a < v ? 1 : 0;
            default -> b;
        };
    }

    private static double not(double v) {
        return v != 0 ? 0 : 1;
    }

    private double read(Operand o) {
        return switch (o.area()) {
            case CONST -> o.value();
            case IX -> ix[o.index()] ? 1 : 0;
            case QX -> qx[o.index()] ? 1 : 0;
            case MX -> mx[o.index()] ? 1 : 0;
            case IW -> iw[o.index()];
            case QW -> qw[o.index()];
            case MW -> mw[o.index()];
            case FB -> fbOut[o.index() * OUT_W + o.member()];
        };
    }

    private void write(Operand o, double v) {
        double x = Double.isFinite(v) ? v : 0;
        switch (o.area()) {
            case QX -> qx[o.index()] = x != 0;
            case MX -> mx[o.index()] = x != 0;
            case QW -> qw[o.index()] = x;
            case MW -> mw[o.index()] = x;
            default -> throw new IllegalStateException("compiler let a store into " + o.area());
        }
    }

    // ---------------------------------------------------------------- функциональные блоки

    private void call(Instr in) {
        int fb = in.fb(), base = fb * IN_W;
        for (int k = 0; k < in.params().length; k++) fbIn[base + in.params()[k]] = read(in.args()[k]);
        int o = fb * OUT_W, s = fb * ST_W;
        switch (Fb.kindOf(fb)) {
            case TON -> {
                boolean on = fbIn[base] != 0;
                double pt = Math.max(0, fbIn[base + 1]);
                double et = on ? Math.min(fbOut[o + 1] + dt, pt) : 0;
                fbOut[o + 1] = et;
                fbOut[o] = on && reached(et, pt) ? 1 : 0;
            }
            case TOF -> {
                boolean on = fbIn[base] != 0, was = fbSt[s] != 0;
                double pt = Math.max(0, fbIn[base + 1]);
                if (on) {
                    fbOut[o] = 1;
                    fbOut[o + 1] = 0;
                    fbSt[s + 1] = 0;
                } else {
                    if (was) fbSt[s + 1] = 1;
                    if (fbSt[s + 1] != 0) {
                        fbOut[o + 1] = Math.min(fbOut[o + 1] + dt, pt);
                        if (reached(fbOut[o + 1], pt)) {
                            fbOut[o] = 0;
                            fbSt[s + 1] = 0;
                        }
                    }
                }
                fbSt[s] = on ? 1 : 0;
            }
            case TP -> {
                boolean on = fbIn[base] != 0, was = fbSt[s] != 0, running = fbSt[s + 1] != 0;
                double pt = Math.max(0, fbIn[base + 1]);
                if (on && !was && !running) {
                    running = true;
                    fbOut[o + 1] = 0;
                }
                if (running) {
                    fbOut[o + 1] = Math.min(fbOut[o + 1] + dt, pt);
                    if (reached(fbOut[o + 1], pt)) running = false;
                } else if (!on) {
                    fbOut[o + 1] = 0;
                }
                fbOut[o] = running ? 1 : 0;
                fbSt[s] = on ? 1 : 0;
                fbSt[s + 1] = running ? 1 : 0;
            }
            case CTU -> {
                boolean cu = fbIn[base] != 0;
                if (fbIn[base + 1] != 0) fbOut[o + 1] = 0;
                else if (cu && fbSt[s] == 0) fbOut[o + 1] = Math.min(32767, fbOut[o + 1] + 1);
                fbSt[s] = cu ? 1 : 0;
                fbOut[o] = fbOut[o + 1] >= fbIn[base + 2] ? 1 : 0;
            }
            case CTD -> {
                boolean cd = fbIn[base] != 0;
                if (fbIn[base + 1] != 0) fbOut[o + 1] = Math.rint(fbIn[base + 2]);
                else if (cd && fbSt[s] == 0) fbOut[o + 1] = Math.max(-32768, fbOut[o + 1] - 1);
                fbSt[s] = cd ? 1 : 0;
                fbOut[o] = fbOut[o + 1] <= 0 ? 1 : 0;
            }
            case R_TRIG -> {
                boolean clk = fbIn[base] != 0;
                fbOut[o] = clk && fbSt[s] == 0 ? 1 : 0;
                fbSt[s] = clk ? 1 : 0;
            }
            case F_TRIG -> {
                boolean clk = fbIn[base] != 0;
                fbOut[o] = !clk && fbSt[s] != 0 ? 1 : 0;
                fbSt[s] = clk ? 1 : 0;
            }
            case PID -> pid(base, o, s);
            case PC_ANTISURGE -> AntiSurge.scan(fbIn, base, fbOut, o, fbSt, s, dt);
        }
    }

    /**
     * ПИД в идеальной форме Y = K_p (e + 1/T_i ∫e dt) − K_p T_d dPV/dt: дифференцирование по PV, а не по ошибке,
     * чтобы скачок уставки не бил по выходу; ограничение LO…HI с обратным пересчётом интеграла (антинасыщение);
     * в ручном режиме выход = XM, интеграл следит за ним — переход в автомат без толчка.
     */
    private void pid(int base, int o, int s) {
        double pv = fbIn[base], sp = fbIn[base + 1], kp = fbIn[base + 2], ti = fbIn[base + 3], td = fbIn[base + 4];
        double lo = fbIn[base + 5], hi = Math.max(lo, fbIn[base + 6]);
        double e = sp - pv, p = kp * e;
        double d = fbSt[s + 1] != 0 && dt > 0 ? -kp * td * (pv - fbSt[s + 2]) / dt : 0;
        double y;
        if (fbIn[base + 7] != 0) {
            y = clamp(fbIn[base + 8], lo, hi);
            fbSt[s] = y - p - d;
        } else {
            if (ti > 0) fbSt[s] += kp * dt / ti * e;
            double raw = p + fbSt[s] + d;
            y = clamp(raw, lo, hi);
            if (y != raw && ti > 0) fbSt[s] = y - p - d;
        }
        fbSt[s + 1] = 1;
        fbSt[s + 2] = pv;
        fbOut[o] = y;
    }

    /** Время вышло: допуск на накопление шагов 0,05 с в двоичной арифметике (10 × 0,05 = 0,49999…). */
    private static boolean reached(double et, double pt) {
        return et >= pt - 1e-9;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------------------------------------------------------------- питание и память

    private void clearOutputs() {
        Arrays.fill(qx, false);
        Arrays.fill(qw, 0);
    }

    /** Пропало 24 В: несохраняемая память, таймеры, фронты и регуляторы сбрасываются, счётчики держит батарея. */
    private void powerLoss() {
        clearOutputs();
        Arrays.fill(mx, 0, RETAIN_MX, false);
        Arrays.fill(mw, 0, RETAIN_MW, 0);
        for (int fb = 0; fb < Fb.COUNT; fb++) {
            Fb.Kind k = Fb.kindOf(fb);
            if (k == Fb.Kind.CTU || k == Fb.Kind.CTD) continue;
            Arrays.fill(fbOut, fb * OUT_W, fb * OUT_W + OUT_W, 0);
            Arrays.fill(fbSt, fb * ST_W, fb * ST_W + ST_W, 0);
        }
    }

    /** Входы регуляторов по умолчанию: K_p = 1, шкала 0…10 В. */
    private void coldDefaults() {
        for (int i = 0; i < Fb.PER_KIND; i++) {
            int base = (Fb.Kind.PID.ordinal() * Fb.PER_KIND + i) * IN_W;
            fbIn[base + 2] = 1;
            fbIn[base + 6] = 10;
        }
        for (int i = 0; i < Fb.PER_KIND; i++) AntiSurge.defaults(fbIn, (Fb.Kind.PC_ANTISURGE.ordinal() * Fb.PER_KIND + i) * IN_W);
    }

    // ---------------------------------------------------------------- сохранение

    public void save(StateWriter w) {
        w.putString("source", program.source());
        w.putBoolean("run_key", runKey);
        w.putInt("mode", mode.ordinal());
        w.putInts("mx", bits(mx), mx.length);
        w.putInts("qx", bits(qx), qx.length);
        w.putDoubles("mw", mw, mw.length);
        w.putDoubles("qw", qw, qw.length);
        w.putDoubles("fb_in", fbIn, fbIn.length);
        w.putDoubles("fb_out", fbOut, fbOut.length);
        w.putDoubles("fb_st", fbSt, fbSt.length);
        w.putInt("fb_layout", LAYOUT);
    }

    public void load(StateReader r) {
        program = PlcLanguage.compile(r.optString("source", ""));
        runKey = r.getBoolean("run_key");
        Mode[] modes = Mode.values();
        mode = modes[Math.floorMod(r.optInt("mode", 0), modes.length)];
        if (mode == Mode.RUN && !program.ok()) mode = Mode.STOP;
        copyBits(r, "mx", mx);
        copyBits(r, "qx", qx);
        copy(r, "mw", mw);
        copy(r, "qw", qw);
        if (r.optInt("fb_layout", 1) >= LAYOUT) {
            copy(r, "fb_in", fbIn);
            copy(r, "fb_out", fbOut);
            copy(r, "fb_st", fbSt);
        } else {
            copyOld(r, "fb_in", fbIn, OLD_IN_W, IN_W);
            copyOld(r, "fb_out", fbOut, OLD_OUT_W, OUT_W);
            copyOld(r, "fb_st", fbSt, OLD_ST_W, ST_W);
        }
    }

    private static int[] bits(boolean[] b) {
        int[] a = new int[b.length];
        for (int i = 0; i < b.length; i++) a[i] = b[i] ? 1 : 0;
        return a;
    }

    private static void copyBits(StateReader r, String k, boolean[] into) {
        if (!r.has(k)) return;
        int[] a = r.getInts(k);
        for (int i = 0; i < Math.min(a.length, into.length); i++) into[i] = a[i] != 0;
    }

    /** Старая раскладка экземпляров (узкие поля) в новую: экземпляр за экземпляром. */
    private static void copyOld(StateReader r, String k, double[] into, int oldW, int newW) {
        if (!r.has(k)) return;
        double[] a = r.getDoubles(k);
        for (int fb = 0; fb < OLD_KINDS * Fb.PER_KIND && (fb + 1) * oldW <= a.length; fb++)
            System.arraycopy(a, fb * oldW, into, fb * newW, oldW);
    }

    private static void copy(StateReader r, String k, double[] into) {
        if (!r.has(k)) return;
        double[] a = r.getDoubles(k);
        System.arraycopy(a, 0, into, 0, Math.min(a.length, into.length));
    }
}
