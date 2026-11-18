package dev.denfry.promptcritical.sim.drilling;

import dev.denfry.promptcritical.sim.mining.WorldHash;

/**
 * Сейсморазведка отражённых волн (МОГТ): площадная съёмка видит структуру кровли коллектора — свод, амплитуду,
 * радиус замыкания — во времени двойного пробега и после пересчёта в глубину по скоростной модели. Содержимое
 * ловушки сейсмика не видит, кроме «яркого пятна» над газом (прямой признак углеводородов: газонасыщенный песчаник
 * резко снижает акустический импеданс; Sheriff &amp; Geldart, «Exploration Seismology», 2-е изд., §10.4).
 *
 * <p>Скорость уплотнения осадков растёт с глубиной линейно, v(z) = v₀ + kz (Sheriff &amp; Geldart, §5.4); время
 * двойного пробега до глубины z — t = (2/k)·ln(1 + kz/v₀), обратный пересчёт z = (v₀/k)(e^{kt/2} − 1). Ошибка
 * скоростной модели ±3 % (своя у каждой ловушки) даёт ошибку глубины того же порядка.
 *
 * <p>Прогноз пластового давления — по методу Итона (Eaton, SPE 5544, 1975): P = S − (S − Pₙ)(v/vₙ)³, где S —
 * геостатическое давление (градиент 22,6 кПа/м, 1 psi/ft), Pₙ — гидростатическое, v — интервальная скорость в
 * глине над ловушкой (в зоне АВПД ниже тренда уплотнения), vₙ — скорость по тренду. Та же ошибка скорости
 * в кубе делает прогноз эквивалентной плотности неточным на ±5–10 %.
 */
public final class SeismicSurvey {
    public static final double V0_MS = 1_800, K_PER_S = 0.6;
    public static final double OVERBURDEN_PA_M = 22_600;
    /** Неопределённость скоростной модели, доли. */
    public static final double VELOCITY_ERROR = 0.03;
    /** Площадь съёмки: радиус без сейсмоприёмников и прибавка на каждый приёмник расстановки, блоки. */
    public static final int BASE_APERTURE = 64, APERTURE_PER_GEOPHONE = 16, MAX_GEOPHONES = 16;
    private static final int SALT = 0x5E15;

    /**
     * Отражение от кровли ловушки, как его даёт интерпретация.
     *
     * @param crestX          свод, X
     * @param crestZ          свод, Z
     * @param crestTwtMs      время двойного пробега до свода, мс
     * @param crestDepthM     глубина свода после пересчёта, м
     * @param closureM        амплитуда замыкания, м
     * @param radiusM         радиус замкнутого контура, м
     * @param brightSpot      аномалия амплитуды над газом
     * @param porePressureKgM3 прогноз эквивалентной плотности пластового давления на своде, кг/м³
     */
    public record Reading(int crestX, int crestZ, double crestTwtMs, double crestDepthM, double closureM, double radiusM,
            boolean brightSpot, double porePressureKgM3) {
    }

    private SeismicSurvey() {
    }

    /** Время двойного пробега до глубины, с. */
    public static double twoWayTimeS(double depthM) {
        return 2 / K_PER_S * Math.log1p(K_PER_S * depthM / V0_MS);
    }

    /** Глубина по времени двойного пробега, м. */
    public static double depthM(double twoWayTimeS) {
        return V0_MS / K_PER_S * Math.expm1(K_PER_S * twoWayTimeS / 2);
    }

    /** Радиус площади съёмки при числе сейсмоприёмников расстановки, блоки. */
    public static int apertureBlocks(int geophones) {
        return BASE_APERTURE + APERTURE_PER_GEOPHONE * Math.clamp(geophones, 0, MAX_GEOPHONES);
    }

    /** Ближайшая к точке (x, z) ловушка, свод которой попал в площадь съёмки; {@code null} — структур нет. */
    public static Reading survey(PetroleumField field, long seed, int x, int z, int apertureBlocks) {
        int c0x = PetroleumField.cellOf(x - apertureBlocks), c1x = PetroleumField.cellOf(x + apertureBlocks);
        int c0z = PetroleumField.cellOf(z - apertureBlocks), c1z = PetroleumField.cellOf(z + apertureBlocks);
        Trap best = null;
        double bestD = Double.MAX_VALUE;
        for (int cx = c0x; cx <= c1x; cx++)
            for (int cz = c0z; cz <= c1z; cz++) {
                Trap t = field.trap(cx, cz);
                if (t == null) continue;
                double d = Math.hypot(t.crestX() - x, t.crestZ() - z);
                if (d <= apertureBlocks && d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        return best == null ? null : image(best, seed);
    }

    /** Интерпретация ловушки: время, пересчёт в глубину с ошибкой скорости, яркое пятно, прогноз давления. */
    public static Reading image(Trap t, long seed) {
        double err = VELOCITY_ERROR * (2 * WorldHash.unit(seed, t.crestX(), 0, t.crestZ(), SALT) - 1);
        double twt = twoWayTimeS(t.crestDepthM());
        // интерпретатор берёт скорость с ошибкой: время то же, глубина и амплитуда — в его скоростях
        double depth = depthM(twt) * (1 + err);
        double closure = t.closureM() * (1 + err);
        double p = t.gradientPaM() * t.crestDepthM();
        double s = OVERBURDEN_PA_M * t.crestDepthM(), pn = PetroleumField.HYDROSTATIC_PA_M * t.crestDepthM();
        // интервальная скорость глины над сводом по Итону и её замер с той же ошибкой
        double vRatio = Math.cbrt((s - p) / (s - pn)) * (1 + err);
        double predicted = s - (s - pn) * vRatio * vRatio * vRatio;
        return new Reading(t.crestX(), t.crestZ(), twt * 1000, depth, closure, t.radiusM(),
                t.fluid() == FormationFluid.GAS, predicted / (Trap.G * t.crestDepthM()));
    }
}
