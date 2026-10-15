package dev.denfry.promptcritical.sim.mining;

/**
 * Закон содержания руды одной роли жилы (08 §8.1.3, 02 §2.1.3): g = clamp(g_med·e^{σ_ln N}, g_min, g_max).
 * Попутный компонент коррелирован с основным: g_b = k_b·g·e^{σ_b N'} (Co = 0,035·g_Ni·e^{0,3N'}).
 *
 * @param mineral        ID руды ({@code magnetite_ore}…)
 * @param component      что измеряет g: металл ({@code Fe}, {@code Ni}…) или оксид ({@code Al2O3}, {@code SiO2})
 * @param gMed           медиана, массовая доля
 * @param gMin           нижняя граница
 * @param gMax           верхняя граница
 * @param sigmaLn        σ_ln: 0,5 — выдержанные (железо, бокситы), 0,9 — сульфиды и ЭПГ (закон де Вийса)
 * @param sulfurPerGrade сера сульфида на единицу g (стехиометрия минерала), 0 — не сульфид
 * @param byproduct      попутный компонент ({@code Co}, {@code PGM}, {@code Mo}, {@code Ag}) или {@code null}
 * @param byproductRatio k_b
 * @param byproductSigma σ_b
 */
public record OreGrade(String mineral, String component, double gMed, double gMin, double gMax, double sigmaLn,
        double sulfurPerGrade, String byproduct, double byproductRatio, double byproductSigma) {
    /** Число классов содержания G0–G5 (02 §2.0.3). */
    public static final int CLASSES = 6;

    public OreGrade {
        if (mineral == null || component == null) throw new IllegalArgumentException("mineral");
        if (!(gMin > 0 && gMin <= gMed && gMed <= gMax && gMax <= 1)) throw new IllegalArgumentException(mineral + ": grade range");
        if (!(sigmaLn >= 0 && sulfurPerGrade >= 0)) throw new IllegalArgumentException(mineral);
        if (byproduct != null && !(byproductRatio > 0 && byproductSigma >= 0)) throw new IllegalArgumentException(mineral);
    }

    /** Руда без попутного компонента. */
    public static OreGrade of(String mineral, String component, double gMed, double gMin, double gMax, double sigmaLn,
            double sulfurPerGrade) {
        return new OreGrade(mineral, component, gMed, gMin, gMax, sigmaLn, sulfurPerGrade, null, 0, 0);
    }

    /** Та же руда с попутным компонентом. */
    public OreGrade withByproduct(String id, double ratio, double sigma) {
        return new OreGrade(mineral, component, gMed, gMin, gMax, sigmaLn, sulfurPerGrade, id, ratio, sigma);
    }

    /** Содержание при нормированном шуме n. {@link StrictMath} — чтобы результат не зависел от JIT. */
    public double grade(double n) {
        return Math.max(gMin, Math.min(gMax, gMed * StrictMath.exp(sigmaLn * n)));
    }

    /**
     * Класс G0–G5: шкала [g_min, g_max] делится на 6 равных долей в логарифме (у руд 08 нет урановой таблицы
     * 02 §2.1.3, а логнормальное содержание естественно квантовать по логарифму).
     */
    /** Середина класса k в логарифме — представительное содержание квантованного предмета (02 §2.0.3). */
    public double classCenter(int k) {
        if (gMax <= gMin) return gMax;
        return gMin * StrictMath.pow(gMax / gMin, (Math.max(0, Math.min(CLASSES - 1, k)) + 0.5) / CLASSES);
    }

    public int gradeClass(double g) {
        if (gMax <= gMin) return CLASSES - 1;
        double x = StrictMath.log(g / gMin) / StrictMath.log(gMax / gMin);
        return Math.max(0, Math.min(CLASSES - 1, (int) (x * CLASSES)));
    }
}
