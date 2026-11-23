package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatment;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.ArrayList;
import java.util.List;

/**
 * Закалка: охлаждающая среда, критический диаметр по семейству стали, неполная прокаливаемость
 * толстых сечений и закалочные трещины легированной и высокоуглеродистой стали в воде.
 */
public final class Quenching {
    private Quenching() {}

    /** Порог по углероду, %, ниже которого сталь не закаливается (мартенсит не набирает твёрдости) [сверить]. */
    public static final double MIN_HARDENING_C_PCT = 0.25;
    /** Температура охлаждённой в воде или масле детали, К (20 °C). */
    public static final double QUENCHED_TEMP_K = 293.15;
    /** Остывание на воздухе при закалке в AIR, с [BALANCE]. */
    public static final double AIR_COOL_S = 600;
    /** Тяжесть закалочной трещины в воде [BALANCE]. */
    public static final double QUENCH_CRACK_SEVERITY = 0.7;
    /** Доля Cr (0,8 %), с которой вода трещит легированную сталь [BALANCE]. */
    public static final double CRACK_CR_FRAC = 0.008;
    /** Углерод, %, с которого вода трещит сталь [BALANCE]. */
    public static final double CRACK_C_PCT = 0.6;

    /** Закалочная среда и коэффициент Гроссмана H, 1/дюйм (ASM Handbook vol. 4 [сверить]). */
    public enum Medium {
        WATER(1.0), OIL(0.3), AIR(0.02);

        public final double grossmannH;

        Medium(double grossmannH) {
            this.grossmannH = grossmannH;
        }
    }

    /** Критический диаметр полной прокаливаемости, м: в воде и в масле. В воздухе он 0 для всех семейств MF1. */
    public record Critical(double waterM, double oilM) {
        double forMedium(Medium m) {
            return switch (m) {
                case WATER -> waterM;
                case OIL -> oilM;
                case AIR -> 0;
            };
        }
    }

    /**
     * Критические диаметры по семейству («Марочник сталей и сплавов», Зубченко, 2003 [сверить]).
     * Не прокаливаются Ст3, кричное железо и неферритные классы (аустенитные, мартенситно-стареющие,
     * инструментальные: в MF1 вручную не закаливаются).
     */
    public static Critical critical(SteelFamily f) {
        return switch (f) {
            case MEDIUM_CARBON -> new Critical(0.015, 0.006);
            case CHROMIUM, BEARING -> new Critical(0.030, 0.020);
            case LOW_ALLOY, HEAT_RESISTANT, SOUR_SERVICE, RAIL_WIRE -> new Critical(0.010, 0.004);
            default -> new Critical(0, 0);
        };
    }

    /**
     * Закалка нагретой заготовки. Не закалилась (холодная, в том числе уже закалённая и остывшая, C &lt; 0,25 % или критический диаметр среды 0):
     * твёрдость прежняя, NOT_HARDENED. Иначе при {@code aM} выше критического твёрдость hn + 0,5·(hq − hn)
     * и NOT_THROUGH_HARDENED. В воде Cr ≥ 0,8 % или C ≥ 0,6 % дают CRACK 0,7 и QUENCH_CRACKED.
     * OK ставится, только когда других событий нет.
     */
    public static Forging.Result quench(Workpiece w, Medium m) {
        SteelGrade grade = w.grade();
        SteelGrade hot = HeatTreatment.quench(grade, w.tempK());
        double critical = critical(grade.family()).forMedium(m);
        if (w.tempK() < HeatTreatment.ac3K(grade) + HeatTreatment.AC3_MARGIN_K
                || hot.state() != HeatTreatState.QUENCHED || grade.carbonPct() < MIN_HARDENING_C_PCT || !(critical > 0)) {
            return new Forging.Result(cooled(w, m), List.of(ForgeEvent.NOT_HARDENED));
        }
        double hq = hot.hardnessHrc();
        double hn = grade.withState(HeatTreatState.NORMALIZED).hardnessHrc();
        var events = new ArrayList<ForgeEvent>();
        double hardness = hq;
        if (w.aM() > critical) {
            hardness = hn + 0.5 * (hq - hn);
            events.add(ForgeEvent.NOT_THROUGH_HARDENED);
        }
        Workpiece p = w.withGrade(hot).withHardness(hardness);
        if (m == Medium.WATER && (grade.frac(AlloyElement.CR) >= CRACK_CR_FRAC
                || grade.carbonPct() >= CRACK_C_PCT)) {
            p = p.withDefect(PartDefect.CRACK, QUENCH_CRACK_SEVERITY);
            events.add(ForgeEvent.QUENCH_CRACKED);
        }
        if (events.isEmpty()) {
            events.add(ForgeEvent.OK);
        }
        return new Forging.Result(cooled(p, m), List.copyOf(events));
    }

    private static Workpiece cooled(Workpiece w, Medium m) {
        return m == Medium.AIR
                ? ForgeHeating.cool(w, QUENCHED_TEMP_K, AIR_COOL_S)
                : w.withTemp(QUENCHED_TEMP_K);
    }
}
