package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Фрезерование на консольно-фрезерном станке типа 6Р82 (горизонтальный, главный привод 7,5 кВт, шпиндель
 * 31,5–1600 об/мин) дисковой фрезой из быстрорежущей стали.
 *
 * <p>Средняя толщина среза при периферийном фрезеровании (Sandvik Coromant, «Technical Guide», раздел Milling):
 * h_m = 360°·sin κ·f_z·a_e / (π·D·arccos(1 − 2a_e/D)), где arccos — в градусах. Удельная сила — по Кинцле от h_m
 * ({@link Machining#kienzleKc}), мощность P_c = Q·k_c, где Q = a_e·a_p·v_f — съём, v_f = f_z·z·n.
 *
 * <p>Зуб режет только на дуге контакта: доля времени в резании φ = arccos(1 − 2a_e/D)/(2π). Износ — правило Майнера
 * по Тейлору с этой долей (стойкость считается по машинному времени резания одного зуба).
 */
public final class Milling {
    /** Дисковая (модульная, трёхсторонняя) фреза Ø80 мм, 12 зубьев, Р6М5. */
    public static final double CUTTER_D_MM = 80;
    public static final int CUTTER_TEETH = 12;

    private Milling() {
    }

    /** Угол контакта, рад. */
    public static double engagementAngle(double aeMm, double dMm) {
        return Math.acos(1 - 2 * Math.min(aeMm, dMm) / dMm);
    }

    /** Доля оборота, которую зуб режет. */
    public static double engagementShare(double aeMm, double dMm) {
        return engagementAngle(aeMm, dMm) / (2 * Math.PI);
    }

    /** Средняя толщина среза h_m, мм (κ = 90° у дисковой фрезы). */
    public static double meanChipThicknessMm(double fzMm, double aeMm, double dMm) {
        double angleDeg = Math.toDegrees(engagementAngle(aeMm, dMm));
        return 360 * fzMm * aeMm / (Math.PI * dMm * angleDeg);
    }

    /** Минутная подача стола v_f = f_z·z·n, мм/мин. */
    public static double tableFeedMmMin(double fzMm, int teeth, double rpm) {
        return fzMm * teeth * Math.abs(rpm);
    }

    /** Съём Q = a_e·a_p·v_f, мм³/с. */
    public static double removalRateMm3S(double aeMm, double apMm, double vfMmMin) {
        return aeMm * apMm * vfMmMin / 60;
    }

    /** Мощность резания P_c = Q·k_c, Вт (Q в мм³/с, k_c в Н/мм² → Н·мм/с = мВт). */
    public static double cuttingPowerW(double removalMm3S, double kcNmm2) {
        return removalMm3S * kcNmm2 / 1000;
    }
}
