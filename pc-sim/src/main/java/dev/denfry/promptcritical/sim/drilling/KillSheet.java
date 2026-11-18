package dev.denfry.promptcritical.sim.drilling;

/**
 * Лист глушения скважины методом бурильщика или «ожидания и утяжеления» (IADC WellSharp, Driller level, раздел
 * «Kill sheet»; API RP 59 «Well Control Operations», §6; Bourgoyne et al., §4.11). В СИ:
 *
 * <pre>
 * ρ_k  = ρ₀ + P_ИДТ / (g·H)              плотность раствора глушения
 * P_нач = P_мед + P_ИДТ                   начальное давление циркуляции (ICP)
 * P_кон = P_мед · ρ_k / ρ₀                конечное давление циркуляции (FCP)
 * МДДК = (ρ_гр − ρ₀)·g·H_баш              максимально допустимое давление в затрубье (MAASP)
 * </pre>
 *
 * P_ИДТ — избыточное давление в трубах после закрытия (SIDPP), P_мед — давление прокачки на скорости глушения,
 * измеренное заранее (SCR). Пока раствор глушения идёт к долоту, давление в трубах снижают от ICP до FCP
 * линейно по ходам насоса, затем держат FCP.
 *
 * @param tvdM           вертикальная глубина забоя, м
 * @param mudKgM3        плотность раствора в скважине, кг/м³
 * @param sidppPa        SIDPP, Па
 * @param sicpPa         SICP (давление в затрубье после закрытия), Па
 * @param scrPa          давление прокачки на скорости глушения, Па
 * @param killMudKgM3    плотность раствора глушения, кг/м³
 * @param icpPa          ICP, Па
 * @param fcpPa          FCP, Па
 * @param strokesToBit   ходов насоса от поверхности до долота
 * @param strokesBitToSurface ходов от долота до устья по затрубью
 * @param maaspPa        МДДК при исходном растворе, Па
 */
public record KillSheet(double tvdM, double mudKgM3, double sidppPa, double sicpPa, double scrPa, double killMudKgM3,
        double icpPa, double fcpPa, double strokesToBit, double strokesBitToSurface, double maaspPa) {
    public static final double G = 9.806_65;
    /** Плотность барита (API 13A: не менее 4,20 г/см³). */
    public static final double BARITE_KGM3 = 4_200;

    /** Составить лист глушения по замерам после закрытия скважины. */
    public static KillSheet of(double tvdM, double mudKgM3, double sidppPa, double sicpPa, double scrPa,
            double stringVolumeM3, double annulusVolumeM3, double m3PerStroke, double shoeDepthM, double shoeFractureKgM3) {
        if (!(tvdM > 0 && mudKgM3 > 0 && m3PerStroke > 0)) throw new IllegalArgumentException("kill sheet inputs");
        double kmw = mudKgM3 + Math.max(0, sidppPa) / (G * tvdM);
        double icp = scrPa + Math.max(0, sidppPa);
        double fcp = scrPa * kmw / mudKgM3;
        double maasp = (shoeFractureKgM3 - mudKgM3) * G * shoeDepthM;
        return new KillSheet(tvdM, mudKgM3, sidppPa, sicpPa, scrPa, kmw, icp, fcp, stringVolumeM3 / m3PerStroke,
                annulusVolumeM3 / m3PerStroke, maasp);
    }

    /** Давление в трубах по графику второго цикла после {@code strokes} ходов раствора глушения, Па. */
    public double drillPipePressurePa(double strokes) {
        if (strokes <= 0) return icpPa;
        if (strokes >= strokesToBit) return fcpPa;
        return icpPa + (fcpPa - icpPa) * strokes / strokesToBit;
    }

    public double totalStrokes() {
        return strokesToBit + strokesBitToSurface;
    }

    /**
     * Масса барита для утяжеления объёма раствора от ρ₁ до ρ₂ без разбавления водой: m = V·ρ_b(ρ₂ − ρ₁)/(ρ_b − ρ₂)
     * (Bourgoyne et al., уравнение 2.4; промысловая форма — 1470(W₂ − W₁)/(35 − W₂) фунтов на баррель).
     */
    public static double bariteKg(double volumeM3, double fromKgM3, double toKgM3) {
        if (toKgM3 <= fromKgM3) return 0;
        if (toKgM3 >= BARITE_KGM3) throw new IllegalArgumentException("cannot weight up past barite density");
        return volumeM3 * BARITE_KGM3 * (toKgM3 - fromKgM3) / (BARITE_KGM3 - toKgM3);
    }
}
