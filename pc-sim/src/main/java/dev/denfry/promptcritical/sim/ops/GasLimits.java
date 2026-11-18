package dev.denfry.promptcritical.sim.ops;

import java.util.EnumSet;
import java.util.Set;

/**
 * Допустимый состав атмосферы для начала работ {@code ops.gas_limits.*} (13 §13.2.3, §13.14): O₂ 19,5–23,5 % (OSHA 29
 * CFR 1910.146), горючие &lt; 10 % НКПР для входа в замкнутое пространство и 0 % НКПР в зоне огневых работ, H₂S &lt; 10
 * ppm, CO &lt; 25 ppm (ACGIH TLV). При огневых работах до 10 % НКПР допустимо только под непрерывным контролем: выше —
 * тревога прибора приостанавливает наряд.
 *
 * @param o2Min    {@code ops.gas_limits.o2_min} (0,195)
 * @param o2Max    {@code ops.gas_limits.o2_max} (0,235)
 * @param lelEntry {@code ops.gas_limits.lel_entry} — доля НКПР для входа и порог тревоги при работах (0,10)
 * @param lelHot   {@code ops.gas_limits.lel_hot} — доля НКПР для начала огневых работ (0,0)
 * @param h2sPpm   {@code ops.gas_limits.h2s_ppm} (10)
 * @param coPpm    {@code ops.gas_limits.co_ppm} (25)
 */
public record GasLimits(double o2Min, double o2Max, double lelEntry, double lelHot, double h2sPpm, double coPpm) {
    public static final GasLimits DEFAULT = new GasLimits(0.195, 0.235, 0.10, 0.0, 10, 25);

    /** Что не так с замером. */
    public enum Fault { NOT_BUMP_TESTED, O2_LOW, O2_HIGH, FLAMMABLE, H2S, CO }

    /**
     * Отклонения замера для вида наряда. {@code enforced} — непроверенный газом прибор не принимается (при
     * {@code ADVISORY} результат с пометкой «прибор не проверен» принимается).
     */
    public Set<Fault> faults(PermitType type, GasTest t, boolean enforced) {
        EnumSet<Fault> out = EnumSet.noneOf(Fault.class);
        if (enforced && !t.bumpTested()) out.add(Fault.NOT_BUMP_TESTED);
        if (t.o2() < o2Min) out.add(Fault.O2_LOW);
        if (t.o2() > o2Max) out.add(Fault.O2_HIGH);
        boolean flammable = type == PermitType.HOT ? t.lelFraction() > lelHot : t.lelFraction() >= lelEntry;
        if (flammable) out.add(Fault.FLAMMABLE);
        if (t.h2sPpm() >= h2sPpm) out.add(Fault.H2S);
        if (t.coPpm() >= coPpm) out.add(Fault.CO);
        return out;
    }

    /** Тревога непрерывного контроля при работах: горючие от {@link #lelEntry} или кислород вне пределов. */
    public boolean alarm(double o2, double lelFraction) {
        return lelFraction >= lelEntry || o2 < o2Min || o2 > o2Max;
    }
}
