package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Компонент {@code casting_spec} отливки (08 §8.17). {@code SteelGrade} появится с прокатом и сталью (M3) — пока
 * марка не хранится; дефект — {@link CastingDefect} вместо {@code PipeDefect} 08 §8.7.4 (тоже M3).
 *
 * @param kind          модель
 * @param alloyId       сплав ({@link CastAlloy#id})
 * @param dn            условный проход корпуса, мм (0 — не корпус)
 * @param defect        дефект (скрыт до контроля)
 * @param inspectedMask биты пройденного контроля {@link #RT}, {@link #UT}, {@link #HYDRO}
 * @param serial        заводской номер
 */
public record CastingSpec(CastingKind kind, String alloyId, int dn, CastingDefect defect, int inspectedMask,
        long serial) {
    /** Радиография (12), УЗК, гидроиспытание (13). */
    public static final int RT = 1, UT = 2, HYDRO = 4;
    /** Пробное давление гидроиспытания — 1,5 рабочего [сверить]. */
    public static final double HYDROTEST_FACTOR = 1.5;

    public CastingSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(alloyId, "alloyId");
        Objects.requireNonNull(defect, "defect");
        if (dn < 0) throw new IllegalArgumentException("dn: " + dn);
    }

    /** Отметка контроля. */
    public CastingSpec inspected(int method) {
        return new CastingSpec(kind, alloyId, dn, defect, inspectedMask | method, serial);
    }

    /** Дефект выявлен пройденным контролем. */
    public boolean defectFound() {
        return defect != CastingDefect.NONE && ((inspectedMask & RT) != 0 && defect.radiography
                || (inspectedMask & UT) != 0 && defect.ultrasonic
                || (inspectedMask & HYDRO) != 0 && defect.leaksOnHydrotest);
    }

    /** Корпус выдерживает гидроиспытание 1,5 × рабочего. */
    public boolean passesHydrotest() {
        return !defect.leaksOnHydrotest;
    }
}
