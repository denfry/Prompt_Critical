package dev.denfry.promptcritical.sim.drilling;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Стек противовыбросового оборудования (09 §9.5.7): кольцевой превентор и плашечные (трубные, глухие, срезающие),
 * закрываемые жидкостью аккумуляторной установки ({@link BopAccumulator}).
 *
 * <ul>
 * <li>Кольцевой закрывается на любой трубе; на пустом стволе — не полностью (давление удержания ×0,5 [BALANCE]);
 * после 20 закрытий уплотнитель изношен — давление удержания ×0,8 ({@link Fault#WORN_ELEMENT}).</li>
 * <li>Трубные плашки герметизируют только трубу своего диаметра; иначе закрываются, но не держат
 * ({@link Fault#RAM_MISMATCH}).</li>
 * <li>Глухие плашки на трубе не закрываются ({@link Fault#RAM_MISMATCH}).</li>
 * <li>Срезающие режут тело трубы и герметизируют ствол; замок трубы в зоне плашек не режется
 * ({@link Fault#TOOL_JOINT_IN_RAM}). Положение замков детерминировано: трубы второго класса длины по 9,144 м (30 фут,
 * API Spec 5DP) над УБТ, высаженная часть с замком — {@link #TOOL_JOINT_M} м.</li>
 * </ul>
 * Ход закрытия — {@link #CLOSE_TIME_S} (предел API Std 53); без давления в аккумуляторе не ниже p_min элемент не
 * закрывается ({@link Fault#NO_PRESSURE}). Неиспытанный дольше {@code drilling.bop_test_interval_days} (14 игровых суток)
 * элемент отказывает с вероятностью 5 % на закрытие [BALANCE] ({@link Fault#UNTESTED_FAILURE}); случайность — из
 * {@link SplittableRandom} с зерном от номера элемента и числа его закрытий.
 *
 * <p>Объёмы закрытия — по каталогам на стек 13⅝″ × 5000 psi: кольцевой Hydril GK — 17,98 гал (68,1 л), плашечный
 * Cameron U — 5,8 гал (22 л); гидроуправляемая задвижка — 0,5 гал (1,9 л).
 */
public final class BopStack {
    public enum Component { ANNULAR, PIPE_RAM, BLIND_RAM, SHEAR_RAM }

    public enum Fault { NONE, NO_PRESSURE, RAM_MISMATCH, TOOL_JOINT_IN_RAM, WORN_ELEMENT, UNTESTED_FAILURE }

    public static final double RATED_PA = Well.BOP_RATED_PA;
    public static final double CLOSE_TIME_S = 30;
    public static final double ANNULAR_CLOSE_M3 = 0.0681, RAM_CLOSE_M3 = 0.022, HCR_OPEN_M3 = 0.0019;
    public static final int WEAR_CLOSURES = 20;
    public static final double WORN_FACTOR = 0.8, OPEN_HOLE_ANNULAR_FACTOR = 0.5;
    public static final double JOINT_M = 9.144, TOOL_JOINT_M = 0.5;
    /** 09 §9.5.7: испытание раз в 14 игровых суток; неиспытанный элемент отказывает с вероятностью 5 % на закрытие. */
    public static final double TEST_INTERVAL_DAYS = 14, UNTESTED_FAILURE_P = 0.05;
    /** Диаметр трубных плашек по умолчанию — под трубы 5″, м. */
    public static final double PIPE_RAM_OD_M = 5 * 0.0254;

    /** Элемент стека. */
    public static final class Element {
        public final Component kind;
        public final double ramOdM;
        int closures;
        double lastTestDay = Double.NEGATIVE_INFINITY;
        boolean closed, sealing;
        double closingS;
        Fault fault = Fault.NONE;

        Element(Component kind, double ramOdM) {
            this.kind = kind;
            this.ramOdM = ramOdM;
        }

        public int closures() {
            return closures;
        }

        public boolean closed() {
            return closed;
        }

        /** Закрыт, ход окончен и держит давление. */
        public boolean sealing() {
            return closed && sealing && closingS <= 0;
        }

        public Fault fault() {
            return fault;
        }

        public double lastTestDay() {
            return lastTestDay;
        }

        /** Давление удержания, Па. */
        public double holdingPa(boolean pipeInHole) {
            double p = RATED_PA;
            if (kind == Component.ANNULAR) {
                if (closures > WEAR_CLOSURES) p *= WORN_FACTOR;
                if (!pipeInHole) p *= OPEN_HOLE_ANNULAR_FACTOR;
            }
            return p;
        }

        double closeVolumeM3() {
            return kind == Component.ANNULAR ? ANNULAR_CLOSE_M3 : RAM_CLOSE_M3;
        }
    }

    private final List<Element> elements = new ArrayList<>();
    private final long seed;
    private boolean pipeSheared;

    public BopStack(long seed) {
        this.seed = seed;
    }

    public Element add(Component kind) {
        Element e = new Element(kind, kind == Component.PIPE_RAM ? PIPE_RAM_OD_M : 0);
        elements.add(e);
        return e;
    }

    /** Стек после монтажа опрессован (API Std 53): испытание засчитано на сутки {@code day}. */
    public void markInstalled(double day) {
        for (Element e : elements) if (Double.isInfinite(e.lastTestDay)) e.lastTestDay = day;
    }

    public List<Element> elements() {
        return List.copyOf(elements);
    }

    public int size() {
        return elements.size();
    }

    public Element get(int i) {
        return elements.get(i);
    }

    /** Колонна срезана срезающими плашками: циркуляции и бурения больше нет. */
    public boolean pipeSheared() {
        return pipeSheared;
    }

    /** Замок трубы в зоне плашек при глубине забоя и длине УБТ (трубы над УБТ, отсчёт от их низа). */
    public static boolean toolJointInRams(double holeDepthM, double bhaLengthM) {
        double along = holeDepthM - bhaLengthM;
        if (along <= 0) return true; // в плашках УБТ — не режется
        double s = along % JOINT_M;
        return s < TOOL_JOINT_M;
    }

    /** Сумма объёмов закрытия всех элементов и открытия гидрозадвижки, м³ (требование API Std 53). */
    public double requiredVolumeM3() {
        double v = HCR_OPEN_M3;
        for (Element e : elements) v += e.closeVolumeM3();
        return v;
    }

    /**
     * Закрыть элемент {@code i}. {@code pipeOdM} — наружный диаметр трубы в стеке (0 — ствол пуст), {@code toolJoint} —
     * замок в зоне плашек, {@code day} — игровые сутки (для срока испытания).
     */
    public Fault close(int i, BopAccumulator acc, double pipeOdM, boolean toolJoint, double day) {
        Element e = elements.get(i);
        if (e.closed) return e.fault;
        boolean pipe = pipeOdM > 0 && !pipeSheared;
        if (e.kind == Component.BLIND_RAM && pipe) return e.fault = Fault.RAM_MISMATCH;
        if (e.kind == Component.SHEAR_RAM && pipe && toolJoint) {
            // плашки упёрлись в замок: жидкость потрачена, ствол не закрыт
            acc.draw(e.closeVolumeM3());
            return e.fault = Fault.TOOL_JOINT_IN_RAM;
        }
        if (!acc.draw(e.closeVolumeM3())) return e.fault = Fault.NO_PRESSURE;
        e.closures++;
        e.closed = true;
        e.closingS = CLOSE_TIME_S;
        e.sealing = true;
        e.fault = Fault.NONE;
        if (e.kind == Component.PIPE_RAM && (!pipe || Math.abs(pipeOdM - e.ramOdM) > 0.003)) {
            e.sealing = false;
            e.fault = Fault.RAM_MISMATCH;
        } else if (e.kind == Component.SHEAR_RAM && pipe) {
            pipeSheared = true;
        }
        if (e.sealing && day - e.lastTestDay > TEST_INTERVAL_DAYS
                && new SplittableRandom(seed ^ (0x9E37_79B9L * (i + 1)) ^ ((long) e.closures << 32)).nextDouble() < UNTESTED_FAILURE_P) {
            e.sealing = false;
            e.fault = Fault.UNTESTED_FAILURE;
        }
        if (e.sealing && e.kind == Component.ANNULAR && e.closures > WEAR_CLOSURES) e.fault = Fault.WORN_ELEMENT;
        return e.fault;
    }

    /** Открыть элемент; срезанную колонну это не вернёт. */
    public Fault open(int i, BopAccumulator acc) {
        Element e = elements.get(i);
        if (!e.closed) return Fault.NONE;
        if (!acc.draw(e.closeVolumeM3())) return Fault.NO_PRESSURE;
        e.closed = false;
        e.sealing = false;
        e.closingS = 0;
        if (e.fault != Fault.WORN_ELEMENT) e.fault = Fault.NONE;
        return Fault.NONE;
    }

    /** Ход плашек. */
    public void step(double dt) {
        for (Element e : elements) if (e.closingS > 0) e.closingS = Math.max(0, e.closingS - dt);
    }

    /** Ствол загерметизирован хотя бы одним элементом. */
    public boolean sealed() {
        for (Element e : elements) if (e.sealing()) return true;
        return false;
    }

    /** Давление удержания стека — по самому прочному загерметизировавшему элементу, Па; 0 — не загерметизирован. */
    public double holdingPa(double pipeOdM) {
        boolean pipe = pipeOdM > 0 && !pipeSheared;
        double p = 0;
        for (Element e : elements) if (e.sealing()) p = Math.max(p, e.holdingPa(pipe));
        return p;
    }

    /**
     * Испытание давлением: каждый открытый элемент закрывается и открывается (два хода жидкости аккумулятора) и
     * получает отметку испытания. Срезающие плашки на трубе не испытываются. Возвращает число испытанных элементов.
     */
    public int pressureTest(BopAccumulator acc, double pipeOdM, double day) {
        boolean pipe = pipeOdM > 0 && !pipeSheared;
        int n = 0;
        for (Element e : elements) {
            if (e.closed) continue;
            if (pipe && (e.kind == Component.BLIND_RAM || e.kind == Component.SHEAR_RAM)) continue;
            if (!acc.canDraw(2 * e.closeVolumeM3(), BopAccumulator.MIN_PA)) continue;
            acc.draw(e.closeVolumeM3());
            acc.draw(e.closeVolumeM3());
            e.closures++;
            e.lastTestDay = day;
            if (e.fault == Fault.UNTESTED_FAILURE) e.fault = Fault.NONE;
            n++;
        }
        return n;
    }

    /** Элемент закрыт и держит без хода и расхода жидкости (перенос старого сохранения, где превентор был закрыт). */
    public void restoreClosed(int i) {
        Element e = elements.get(i);
        e.closed = true;
        e.sealing = true;
        e.closingS = 0;
    }

    /** Заменить уплотнитель кольцевого: счётчик закрытий обнуляется. */
    public void replacePacker(int i) {
        Element e = elements.get(i);
        e.closures = 0;
        if (e.fault == Fault.WORN_ELEMENT) e.fault = Fault.NONE;
    }

    // --- сохранение: на элемент [closures, lastTestDay, closed, sealing, closingS, fault] ---

    public double[] exportState() {
        double[] out = new double[1 + 6 * elements.size()];
        out[0] = pipeSheared ? 1 : 0;
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            int k = 1 + 6 * i;
            out[k] = e.closures;
            out[k + 1] = Double.isInfinite(e.lastTestDay) ? -1e9 : e.lastTestDay;
            out[k + 2] = e.closed ? 1 : 0;
            out[k + 3] = e.sealing ? 1 : 0;
            out[k + 4] = e.closingS;
            out[k + 5] = e.fault.ordinal();
        }
        return out;
    }

    /** Восстановить состояние элементов, совпадающих по порядку; лишние значения игнорируются. */
    public void restore(double[] in) {
        if (in == null || in.length < 1) return;
        pipeSheared = in[0] > 0.5;
        Fault[] faults = Fault.values();
        for (int i = 0; i < elements.size() && 1 + 6 * i + 5 < in.length; i++) {
            Element e = elements.get(i);
            int k = 1 + 6 * i;
            e.closures = (int) in[k];
            e.lastTestDay = in[k + 1] <= -1e8 ? Double.NEGATIVE_INFINITY : in[k + 1];
            e.closed = in[k + 2] > 0.5;
            e.sealing = in[k + 3] > 0.5;
            e.closingS = in[k + 4];
            int f = (int) in[k + 5];
            e.fault = f >= 0 && f < faults.length ? faults[f] : Fault.NONE;
        }
    }
}
