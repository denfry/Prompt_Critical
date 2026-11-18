package dev.denfry.promptcritical.sim.radiation;

/**
 * γ-излучающие нуклиды с периодом полураспада и линиями (ENSDF / Nuclear Data Sheets; таблицы Бекурца, «Нейтронная
 * физика» и Козлов В. Ф., «Справочник по радиационной безопасности», 1999). Керм-постоянная Γ_K, мГр·м²/(ГБк·ч),
 * следует из линий: Co-60 — 0,306, Cs-137 (через Ba-137m) — 0,0779.
 */
public enum Emitter {
    CO60(5.2714 * 365.25 * 86400, new double[] {1.1732, 1.3325}, new double[] {0.9985, 0.9998}),
    CS137(30.08 * 365.25 * 86400, new double[] {0.6617}, new double[] {0.851}),
    I131(8.0252 * 86400, new double[] {0.3645, 0.6370}, new double[] {0.815, 0.0716});

    public final double halfLifeS;
    private final double[] energies, yields;

    Emitter(double halfLifeS, double[] energies, double[] yields) {
        this.halfLifeS = halfLifeS;
        this.energies = energies;
        this.yields = yields;
    }

    /** Активность через {@code t} секунд, Бк. */
    public double activity(double a0Bq, double tS) {
        return a0Bq * Math.pow(2, -tS / halfLifeS);
    }

    /** Источник при активности {@code bq}. */
    public PointSource source(double bq) {
        double[] p = new double[yields.length];
        for (int i = 0; i < p.length; i++) p[i] = bq * yields[i];
        return new PointSource(energies.clone(), p, 0);
    }

    /** Керм-постоянная, мГр·м²/(ГБк·ч), из линий. */
    public double kermaConstant() {
        return DoseRate.gammaGyH(source(1e9), 1, java.util.List.of()) * 1000;
    }
}
