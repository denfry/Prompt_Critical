package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.EnumSet;
import java.util.Set;

/**
 * Контроль детали (GDD 18 §18.6.10, §18.15 п. 5): паспорт получает отметку «проверено», когда выполнены все замеры,
 * которые эта деталь требует. Размер — штангенциркулем или микрометром, твёрдость — твердомером после
 * термообработки, внутренние дефекты отливок и швов — гамма-дефектоскопом. Изоляцию обмоток мегаомметром здесь не
 * ведём: у статора и машины она уже в своём паспорте. Отметки замеров копятся отдельно от паспорта; сам паспорт
 * хранит один флаг.
 */
public final class PartInspection {
    /** Вид замера. Порядок — номер бита в маске прогресса, менять нельзя. */
    public enum Check { DIMENSION, HARDNESS, INTERNAL }

    /** Измерительный инструмент. */
    public enum Tool {
        /**
         * Штангенциркуль ШЦ-I, отсчёт 0,1 мм (ГОСТ 166): погрешность ±0,1 мм. По ГОСТ 8.051 погрешность замера не
         * больше трети допуска, поэтому он годится до IT12 (допуск 0,25–0,30 мм на валах 30–80 мм).
         */
        CALIPER,
        /** Микрометр МК, отсчёт 0,01 мм, погрешность ±4 мкм (ГОСТ 6507): годится до IT6 на тех же размерах. */
        MICROMETER,
        /** Переносной твердомер (ударный, по отпечатку): шкала HRC. */
        HARDNESS_TESTER,
        /** Гамма-дефектоскоп (12 §12.4): поры, шлак, непровар внутри отливки или шва. */
        RADIOGRAPHY
    }

    /** Самый точный квалитет, который ещё проверяет штангенциркуль. */
    public static final int CALIPER_FINEST_IT = 12;
    /** Самый точный квалитет, который ещё проверяет микрометр. */
    public static final int MICROMETER_FINEST_IT = 6;

    private PartInspection() {}

    /** Замеры, которые нужны детали для отметки «проверено». */
    public static Set<Check> required(PartSpec p) {
        EnumSet<Check> out = EnumSet.of(Check.DIMENSION);
        if (p.heatTreat() != HeatTreatState.AS_ROLLED) out.add(Check.HARDNESS);
        if (p.route() == PartRoute.CAST || p.route() == PartRoute.WELDED) out.add(Check.INTERNAL);
        return out;
    }

    /**
     * Какой замер этот инструмент делает на этой детали; {@code null} — инструмент для неё не годится (не та
     * точность, нечего мерить).
     */
    public static Check measure(Tool tool, PartSpec p) {
        Set<Check> need = required(p);
        Check c = switch (tool) {
            case CALIPER -> p.itGrade() == 0 || p.itGrade() >= CALIPER_FINEST_IT ? Check.DIMENSION : null;
            case MICROMETER -> p.itGrade() == 0 || p.itGrade() >= MICROMETER_FINEST_IT ? Check.DIMENSION : null;
            case HARDNESS_TESTER -> Check.HARDNESS;
            case RADIOGRAPHY -> Check.INTERNAL;
        };
        return c != null && need.contains(c) ? c : null;
    }

    /** Маска сделанных замеров после замера {@code c}. */
    public static int record(int mask, Check c) {
        return mask | 1 << c.ordinal();
    }

    public static boolean done(int mask, Check c) {
        return (mask & 1 << c.ordinal()) != 0;
    }

    /** Все нужные замеры сделаны. */
    public static boolean complete(PartSpec p, int mask) {
        for (Check c : required(p)) if (!done(mask, c)) return false;
        return true;
    }
}
