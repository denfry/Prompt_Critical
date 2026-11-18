package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Шероховатость и квалитет обработанной поверхности как следствие режима (08 §8.10, станочный парк).
 *
 * <p>Кинематическая высота гребешка: резец с радиусом при вершине r оставляет R_t = f²/(8r), фреза диаметром D при
 * подаче на оборот f_o — R_t = f_o²/(4D) (биение кромок больше гребешка от одного зуба, поэтому след оставляет один
 * зуб за оборот). Для параболического гребешка Ra ≈ R_t/4 (Shaw M.C. «Metal Cutting Principles», 2005, гл. 21;
 * Боровский Г.В. и др. «Справочник инструментальщика», 1987). Реальная поверхность хуже теоретической из-за вибраций и
 * нароста: снизу — {@link #RA_FLOOR_UM}; изношенная кромка рвёт гребешки — множитель 1 + 0,5·W [калибровка].
 *
 * <p>Квалитет по Ra — экономическая точность методов обработки: чистовое точение и фрезерование IT8–IT9, строгание
 * IT9–IT11, шлифование IT6–IT7; по таблицам «Справочника технолога-машиностроителя», т. 1 (под ред. Косиловой А.Г.,
 * Мещерякова Р.К., 1985), где Ra ≈ 0,05 допуска размера [сверить по таблице соответствия].
 */
public final class SurfaceFinish {
    /** Нижний предел Ra лезвийной обработки на универсальном станке (чистовое фрезерование и строгание Ra 1,6–3,2), мкм. */
    public static final double RA_FLOOR_UM = 1.6;
    /** Пороги Ra (мкм) и квалитеты IT. */
    private static final double[] RA = {0.2, 0.4, 0.8, 1.6, 3.2, 6.3, 12.5, 25};
    private static final int[] IT = {5, 6, 7, 8, 9, 11, 12, 14};
    /** Грубее Ra 25 — черновая обработка, IT16. */
    public static final int IT_ROUGH = 16;

    private SurfaceFinish() {
    }

    private static double wearFactor(double wear) {
        return 1 + 0.5 * Math.max(0, Math.min(1, wear));
    }

    /** Ra однолезвийного резца (строгание, точение), мкм: R_t = f²/(8r), Ra = R_t/4. */
    public static double singlePointRaUm(double feedMm, double noseRadiusMm, double wear) {
        double rt = feedMm * feedMm / (8 * noseRadiusMm) * 1000;
        return Math.max(RA_FLOOR_UM, rt / 4) * wearFactor(wear);
    }

    /** Ra цилиндрического и дискового фрезерования, мкм: R_t = f_o²/(4D), f_o = f_z·z. */
    public static double millingRaUm(double feedPerToothMm, int teeth, double cutterDiameterMm, double wear) {
        double fo = feedPerToothMm * teeth;
        double rt = fo * fo / (4 * cutterDiameterMm) * 1000;
        return Math.max(RA_FLOOR_UM, rt / 4) * wearFactor(wear);
    }

    /** Квалитет IT, достижимый при шероховатости Ra. */
    public static int itGrade(double raUm) {
        for (int i = 0; i < RA.length; i++) if (raUm <= RA[i] + 1e-9) return IT[i];
        return IT_ROUGH;
    }
}
