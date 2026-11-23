package dev.denfry.promptcritical.sim.manufacturing;

import java.util.Objects;

/**
 * Обмотка статора (18 §18.6.9). Расчёт ведётся для эталонного двигателя пакета {@link LaminationShape#STATOR_112}:
 * трёхфазная однослойная обмотка, {@link #CONDUCTORS_PER_SLOT} проводников в пазу, витков в фазе Z·N_п/6.
 * Коэффициент заполнения паза k = N_п·πd²_из/4 / (S_п·{@link #SLOT_USABLE}); выше предела укладки изоляция
 * сдирается о кромки паза. Эмаль на нагартованной меди трескается при гибке лобовых частей. Тяжесть дефекта изоляции
 * переходит в начальное сопротивление изоляции (вид отказа {@code INSULATION_AGING}).
 */
public final class Winding {
    private Winding() {}

    /** Проводников в пазу эталонного двигателя: E = 4,44·f·w·k_об·Φ даёт w ≈ 174 при 220 В фазы [сверить]. */
    public static final int CONDUCTORS_PER_SLOT = 29;
    /** Доля паза под проводом: остальное — пазовая коробочка и клин [сверить: 0,8–0,9]. */
    public static final double SLOT_USABLE = 0.85;
    /** Предел заполнения паза станком и руками [сверить: всыпная обмотка 0,40–0,45]; выше — повреждения за 0,15. */
    public static final double FILL_LIMIT_MACHINE = 0.44, FILL_LIMIT_HAND = 0.40, FILL_SPAN = 0.15;
    /** Наклёп меди, с которого эмаль трескается, и наклёп полной тяжести [моё: эмалируют отожжённую проволоку]. */
    public static final double HARD_STRAIN_FROM = 0.5, HARD_STRAIN_SPAN = 1.5;
    /** Вылет лобовой части на сторону, м: средняя длина витка 2·(l + 0,15) [сверить]. */
    public static final double END_TURN_M = 0.15;
    /** Длина пакета эталонного двигателя, м. */
    public static final double STACK_LENGTH_M = 0.125;
    /** Медь М1 при 20 °C, Ом·м, и её температурный коэффициент, 1/К; твёрдотянутая на 3 % хуже (IACS 97 %). */
    public static final double COPPER_RHO_20 = 1.724e-8, COPPER_ALPHA = 0.00393, HARD_COPPER_PENALTY = 0.03;

    public static int turnsPerPhase(LaminationShape shape) {
        return shape.slots() * CONDUCTORS_PER_SLOT / 6;
    }

    /** Средняя длина витка, м, для пакета длиной {@code stackM}. */
    public static double meanTurnM(double stackM) {
        return 2 * (stackM + END_TURN_M);
    }

    /** Провода на обмотку пакета, м: три фазы. */
    public static double wireNeededM(CoreStack core) {
        return 3 * turnsPerPhase(core.lamination().shape()) * meanTurnM(core.lengthM());
    }

    /** Коэффициент заполнения паза проводом диаметра {@code bareM} в изоляции {@code ins}. */
    public static double fillFactor(LaminationShape shape, double bareM, WireInsulation ins) {
        double d = ins.insulatedM(bareM);
        return CONDUCTORS_PER_SLOT * Math.PI * d * d / 4 / (shape.slotAreaM2() * SLOT_USABLE);
    }

    /** Тяжесть дефекта изоляции 0..1 от заполнения паза и наклёпа меди под эмалью. */
    public static double flawSeverity(double fill, boolean machine, double strain, WireInsulation ins) {
        double limit = machine ? FILL_LIMIT_MACHINE : FILL_LIMIT_HAND;
        double sFill = clamp01((fill - limit) / FILL_SPAN);
        double sHard = clamp01((strain - HARD_STRAIN_FROM) / HARD_STRAIN_SPAN) * ins.strainSensitivity;
        return 1 - (1 - sFill) * (1 - sHard);
    }

    /** Сопротивление фазы при температуре {@code tempK}, Ом. */
    public static double phaseResistanceOhm(LaminationShape shape, double stackM, Wire wire, double tempK) {
        double rho = COPPER_RHO_20 * (1 + HARD_COPPER_PENALTY * Math.min(1, wire.strain()))
                * (1 + COPPER_ALPHA * (tempK - 293.15));
        return rho * turnsPerPhase(shape) * meanTurnM(stackM) / wire.areaM2();
    }

    /**
     * Намотать пакет медным проводом в изоляции {@code ins} станком или руками. Провод без изоляции и не медный
     * не мотают: {@link IllegalArgumentException}.
     */
    public static Stator wind(CoreStack core, Wire wire, WireInsulation ins, boolean machine) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(wire, "wire");
        Objects.requireNonNull(ins, "insulation");
        if (ins == WireInsulation.NONE || wire.metal() != Wire.Metal.COPPER)
            throw new IllegalArgumentException("winding needs insulated copper wire");
        var shape = core.lamination().shape();
        double fill = fillFactor(shape, wire.diameterM(), ins);
        double sev = flawSeverity(fill, machine, wire.strain(), ins);
        double r = phaseResistanceOhm(shape, core.lengthM(), wire, Stator.WINDING_K);
        return new Stator(core, ins.insulationClass, fill, r, sev);
    }

    static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }
}
