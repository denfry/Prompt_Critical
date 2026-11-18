package dev.denfry.promptcritical.sim.petrochem;

/**
 * Молекулярная схема термического крекинга этана Sundaram и Froment («Modeling of thermal cracking kinetics — I.
 * Thermal cracking of ethane, propane and their mixtures», Chem. Eng. Sci. 32, 1977, 601–608; приведена в Froment,
 * Bischoff, «Chemical Reactor Analysis and Design», 2-е изд., 1990, гл. 9): семь реакций, константы
 * k = A·exp(−E/RT), A — в с⁻¹ или л/(моль·с):
 * <ol>
 *   <li>C₂H₆ → C₂H₄ + H₂ — 4,652·10¹³, 272,8 кДж/моль;</li>
 *   <li>C₂H₄ + H₂ → C₂H₆ — 8,75·10⁸, 136,8;</li>
 *   <li>2 C₂H₆ → C₃H₈ + CH₄ — 3,85·10¹¹, 273,2 (первый порядок по этану);</li>
 *   <li>C₃H₆ → C₂H₂ + CH₄ — 9,81·10⁸, 154,4;</li>
 *   <li>C₂H₂ + CH₄ → C₃H₆ — 5,87·10⁴, 29,7;</li>
 *   <li>C₂H₂ + C₂H₄ → C₄H₆ — 1,03·10¹², 172,6;</li>
 *   <li>C₂H₄ + C₂H₆ → C₃H₆ + CH₄ — 7,08·10¹³, 253,0.</li>
 * </ol>
 * Змеевик — реактор идеального вытеснения с линейным профилем температуры газа от входа в радиантную секцию до
 * выхода (COT) и водяным паром разбавления как инертом; объём — по номинальному времени пребывания при средней
 * температуре. Тяжёлые продукты (C5+) схема не описывает — их у этана меньше 2 %.
 */
public final class EthaneCracking {
    static final double R = 8.314462;
    private static final double[] A = {4.652e13, 8.75e8, 3.85e11, 9.81e8, 5.87e4, 1.03e12, 7.08e13};
    private static final double[] E = {272.8e3, 136.8e3, 273.2e3, 154.4e3, 29.7e3, 172.6e3, 253.0e3};
    static final int STEPS = 400;

    private EthaneCracking() {
    }

    /**
     * Крекинг этана в змеевике.
     *
     * @param ethaneMolS    этан на входе, моль/с
     * @param steamMolS     пар разбавления, моль/с
     * @param inletK        температура на входе в радиантную секцию, К
     * @param outletK       температура на выходе (COT), К
     * @param pressurePa    среднее давление в змеевике, Па
     * @param residenceS    номинальное время пребывания при средней температуре и входном количестве молей, с
     * @return потоки компонентов на выходе, моль/с (индексы {@link PyroComponent})
     */
    public static double[] crack(double ethaneMolS, double steamMolS, double inletK, double outletK, double pressurePa,
            double residenceS) {
        double[] f = new double[PyroComponent.N];
        f[PyroComponent.C2H6.ordinal()] = ethaneMolS;
        double tMean = 0.5 * (inletK + outletK);
        double volume = residenceS * (ethaneMolS + steamMolS) * R * tMean / pressurePa;
        double dv = volume / STEPS;
        double[] k = new double[7];
        for (int s = 0; s < STEPS; s++) {
            double t = inletK + (outletK - inletK) * (s + 0.5) / STEPS;
            for (int j = 0; j < 7; j++) k[j] = A[j] * Math.exp(-E[j] / (R * t));
            double total = steamMolS;
            for (double x : f) total += x;
            // концентрации, моль/л: y·P/(RT) в моль/м³, /1000
            double cTot = pressurePa / (R * t) / 1000;
            double c2h6 = cTot * f[PyroComponent.C2H6.ordinal()] / total, c2h4 = cTot * f[PyroComponent.C2H4.ordinal()] / total;
            double h2 = cTot * f[PyroComponent.H2.ordinal()] / total, c3h6 = cTot * f[PyroComponent.C3H6.ordinal()] / total;
            double c2h2 = cTot * f[PyroComponent.C2H2.ordinal()] / total, ch4 = cTot * f[PyroComponent.CH4.ordinal()] / total;
            // скорости, моль/(л·с)
            double r1 = k[0] * c2h6, r2 = k[1] * c2h4 * h2, r3 = k[2] * c2h6, r4 = k[3] * c3h6, r5 = k[4] * c2h2 * ch4,
                    r6 = k[5] * c2h2 * c2h4, r7 = k[6] * c2h4 * c2h6;
            double d = dv * 1000; // л
            f[PyroComponent.C2H6.ordinal()] += d * (-r1 + r2 - 2 * r3 - r7);
            f[PyroComponent.C2H4.ordinal()] += d * (r1 - r2 - r6 - r7);
            f[PyroComponent.H2.ordinal()] += d * (r1 - r2);
            f[PyroComponent.C3H8.ordinal()] += d * r3;
            f[PyroComponent.CH4.ordinal()] += d * (r3 + r4 - r5 + r7);
            f[PyroComponent.C3H6.ordinal()] += d * (-r4 + r5 + r7);
            f[PyroComponent.C2H2.ordinal()] += d * (r4 - r5 - r6);
            f[PyroComponent.C4H6.ordinal()] += d * r6;
            for (int i = 0; i < f.length; i++) f[i] = Math.max(0, f[i]);
        }
        return f;
    }
}
