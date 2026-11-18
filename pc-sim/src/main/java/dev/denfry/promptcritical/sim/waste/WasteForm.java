package dev.denfry.promptcritical.sim.waste;

/**
 * Форма отходов (матрица) и её долговременная устойчивость в воде после разрушения контейнера.
 *
 * <p>Выход из матрицы — первого порядка: доля в год k = NR·S·f_тр / M, где NR — нормализованная скорость
 * выщелачивания, г/(см²·сут) (доля нуклида, ушедшая с см² поверхности за сутки, умноженная на массу образца); S —
 * поверхность упаковки, f_тр — множитель трещиноватости (стекло растрескивается при остывании в канистре в 10–40 раз,
 * взят 10), M — масса. Значения NR — долговременные, не первичные из стандартного теста:
 * <ul>
 *   <li>цементный компаунд — 10⁻⁴ для Cs (первичная ≈ 10⁻³, норматив ГОСТ Р 51883-2002 — не выше 10⁻³);</li>
 *   <li>битумный — 10⁻⁵ (IAEA TRS-352, 1993: на один-два порядка лучше цемента, пока битум не набух);</li>
 *   <li>боросиликатное стекло — 10⁻⁸ (остаточная скорость R7T7/SON68 ≈ 10⁻⁴ г/(м²·сут) при 50 °C; первичная по
 *   ГОСТ Р 50926-96 для Cs — не выше 10⁻⁶);</li>
 *   <li>алюмофосфатное стекло ПО «Маяк» — 10⁻⁷: стойкое, пока не закристаллизуется;</li>
 *   <li>прессованные отходы без матрицы — нуклиды на поверхности: 10 % в год после разрушения бочки.</li>
 * </ul>
 * Геометрия: бочка 200 л — цилиндр H/D = 1,5; канистра стекла — H/D = 3 (CSD-V: Ø 0,43 × 1,34 м).
 */
public enum WasteForm {
    CEMENT(1.9, 1e-4, 1, 1.5),
    BITUMEN(1.3, 1e-5, 1, 1.5),
    COMPACTED(1.5, Double.NaN, 1, 1.5),
    BOROSILICATE_GLASS(2.75, 1e-8, 10, 3),
    PHOSPHATE_GLASS(2.6, 1e-7, 10, 3);

    /** Выход прессованных отходов без матрицы, доля в год. */
    public static final double FREE_RELEASE_PER_Y = 0.1;

    /** Плотность, кг/л. */
    public final double densityKgL;
    /** Долговременная нормализованная скорость выщелачивания, г/(см²·сут). */
    public final double leachGCm2Day;
    public final double crackFactor, heightToDiameter;

    WasteForm(double densityKgL, double leachGCm2Day, double crackFactor, double heightToDiameter) {
        this.densityKgL = densityKgL;
        this.leachGCm2Day = leachGCm2Day;
        this.crackFactor = crackFactor;
        this.heightToDiameter = heightToDiameter;
    }

    public boolean glass() {
        return this == BOROSILICATE_GLASS || this == PHOSPHATE_GLASS;
    }

    /** Радиус цилиндра массой {@code massKg}, м. */
    public double radiusM(double massKg) {
        double v = massKg / (densityKgL * 1000);
        return Math.cbrt(v / (2 * Math.PI * heightToDiameter));
    }

    /** Поверхность цилиндра массой {@code massKg}, м². */
    public double surfaceM2(double massKg) {
        double r = radiusM(massKg);
        return 2 * Math.PI * r * (2 * r * heightToDiameter) + 2 * Math.PI * r * r;
    }

    /** Доля активности, уходящая из одной упаковки массой {@code packageKg} в год после разрушения контейнера. */
    public double releasePerYear(double packageKg) {
        if (this == COMPACTED) return FREE_RELEASE_PER_Y;
        if (packageKg <= 0) return 0;
        double sCm2 = surfaceM2(packageKg) * 1e4 * crackFactor;
        return Math.min(1, leachGCm2Day * sCm2 / (packageKg * 1000) * 365.25);
    }
}
