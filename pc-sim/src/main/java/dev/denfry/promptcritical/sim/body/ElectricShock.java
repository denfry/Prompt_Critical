package dev.denfry.promptcritical.sim.body;

/**
 * Поражение током при касании голой токоведущей части (шины) стоя на земле.
 *
 * <ul>
 *   <li>Напряжение прикосновения — фазное U_л/√3: в сети с заземлённой нейтралью ток идёт через тело в землю; в сетях
 *   6–35 кВ с изолированной нейтралью его ограничивает ёмкость сети на землю, но её ток — амперы, то есть для тела
 *   то же фазное напряжение [допущение].</li>
 *   <li>Полный импеданс тела Z_T — IEC 60479-1, табл. 1: путь рука–рука, сухая кожа, большая площадь касания,
 *   50 % людей; от 3250 Ом при 25 В до внутренних 775 Ом выше 700 В (кожа пробита). Путь рука–ноги на 20 % ниже
 *   (там же: на 10–30 %). Мокрая кожа (вода, дождь) — только внутренний импеданс [допущение].</li>
 *   <li>Ток отпускания — Dalziel: 16 мА у мужчин, 10,5 мА у женщин (50 % людей). Выше него руку не разжать, и
 *   касание длится, пока не оттащат.</li>
 *   <li>Фибрилляция — Dalziel: I = K/√t, K = 0,116 А·√с при 50 кг и 0,157 при 70 кг, между ними и дальше — по
 *   линии; t от 8,3 мс до 5 с. Порог растёт с массой тела.</li>
 * </ul>
 */
public final class ElectricShock {
    private static final double[] TABLE_V = {25, 50, 75, 100, 125, 150, 175, 200, 225, 400, 500, 700, 1000};
    private static final double[] TABLE_Z = {3250, 2500, 2000, 1725, 1550, 1400, 1325, 1275, 1225, 950, 850, 775, 775};
    /** Внутренний импеданс тела (асимптота табл. 1), Ом, путь рука–рука. */
    public static final double INTERNAL_OHM = 775;
    /** Путь рука–ноги к пути рука–рука. */
    public static final double HAND_TO_FEET = 0.8;
    /** Порог ощущения, А (IEC 60479-1). */
    public static final double PERCEPTION_A = 0.5e-3;
    /** Окно формулы Дальзила, с. */
    public static final double DALZIEL_MIN_S = 0.0083, DALZIEL_MAX_S = 5;
    /** Изолирующий коврик (шерсть) держит до 1 кВ, как диэлектрический ковёр. */
    public static final double MAT_RATED_V = 1000;

    public enum Effect { NONE, PERCEPTIBLE, CANNOT_LET_GO, FIBRILLATION }

    private ElectricShock() {
    }

    /** Фазное напряжение прикосновения при линейном U, В. */
    public static double touchVoltageV(double lineV) {
        return lineV / Math.sqrt(3);
    }

    /** Полный импеданс тела рука–ноги, Ом. */
    public static double bodyImpedanceOhm(double touchV, boolean wet) {
        if (wet) return INTERNAL_OHM * HAND_TO_FEET;
        double v = Math.abs(touchV);
        double z;
        if (v <= TABLE_V[0]) {
            z = TABLE_Z[0];
        } else if (v >= TABLE_V[TABLE_V.length - 1]) {
            z = INTERNAL_OHM;
        } else {
            int k = 1;
            while (TABLE_V[k] < v) k++;
            double a = (v - TABLE_V[k - 1]) / (TABLE_V[k] - TABLE_V[k - 1]);
            z = TABLE_Z[k - 1] + a * (TABLE_Z[k] - TABLE_Z[k - 1]);
        }
        return z * HAND_TO_FEET;
    }

    /** Ток через тело, А. */
    public static double bodyCurrentA(double touchV, boolean wet) {
        return Math.abs(touchV) / bodyImpedanceOhm(touchV, wet);
    }

    /** Ток отпускания, А. */
    public static double letGoA(BodyProfile.Sex sex) {
        return sex == BodyProfile.Sex.FEMALE ? 0.0105 : 0.016;
    }

    /** Порог фибрилляции при длительности {@code seconds}, А. */
    public static double fibrillationA(double massKg, double seconds) {
        double k = 0.116 + (0.157 - 0.116) * (massKg - 50) / 20;
        double t = Math.max(DALZIEL_MIN_S, Math.min(DALZIEL_MAX_S, seconds));
        return k / Math.sqrt(t);
    }

    /** Касание одного человека: длительность растёт, пока ток идёт, и сбрасывается, когда касания нет. */
    public static final class Contact {
        private double seconds;

        /**
         * Шаг касания.
         *
         * @param currentA ток через тело, А (0 — касания нет)
         */
        public Effect step(BodyProfile body, double currentA, double dtS) {
            if (currentA < PERCEPTION_A) {
                seconds = 0;
                return Effect.NONE;
            }
            seconds += dtS;
            if (currentA >= fibrillationA(body.massKg(), seconds)) return Effect.FIBRILLATION;
            if (currentA >= letGoA(body.sex())) return Effect.CANNOT_LET_GO;
            return Effect.PERCEPTIBLE;
        }

        public double seconds() {
            return seconds;
        }
    }
}
