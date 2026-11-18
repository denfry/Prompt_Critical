package dev.denfry.promptcritical.sim.consequence;

/**
 * Токсичный газ выброса: молярная масса для перевода кг/м³ в ppm, пробит-функция гибели Pr = a + b·ln(Σ Cⁿ·Δt)
 * (C в ppm, t в минутах) и пороги самочувствия.
 * <ul>
 *   <li>Хлор — пробит CCPS (1999): a = −8,29, b = 0,92, n = 2. Запах 0,3 ppm, ERPG-2 3, IDLH 10, AEGL-3 (10 мин) 50.</li>
 *   <li>Фтористый водород — пробит TNO («Green Book», 1992: a = −8,4, b = 1, n = 1,5 в мг/м³), переведённый в ppm:
 *   a = −8,4 + 1,5·ln(20,006/24,45) = −8,70. Запах (заметный) 0,5 ppm, ERPG-2 20, IDLH 30, AEGL-3 (10 мин) 170.
 *   Источник HF — и прямая утечка, и гидролиз UF₆ во влажном воздухе: UF₆ + 2 H₂O → UO₂F₂ + 4 HF.</li>
 *   <li>Сернистый ангидрид — пробит CCPS («Guidelines for Chemical Process Quantitative Risk Analysis», 1989): a = −15,67,
 *   b = 2,10, n = 1. Запах 0,5 ppm, ERPG-2 3, IDLH 100, AEGL-3 (10 мин) 42.</li>
 *   <li>Сероводород — пробит CCPS / TNO (09 §9.11.8): a = −31,42, b = 3,008, n = 1,43 (500 ppm × 10 мин — 0,3 %,
 *   1000 ppm × 10 мин — 59 %). Запах 0,01 ppm, но выше {@value #H2S_ANOSMIA_PPM} ppm обоняние парализовано: запаха нет.
 *   ERPG-2 30, IDLH 100, AEGL-3 (10 мин) 76.</li>
 * </ul>
 */
public enum ToxicGas {
    CHLORINE(70.906, -8.29, 0.92, 2, 0.3, 3, 10, 50),
    HYDROGEN_FLUORIDE(20.006, -8.70, 1.0, 1.5, 0.5, 20, 30, 170),
    SULFUR_DIOXIDE(64.066, -15.67, 2.10, 1.0, 0.5, 3, 100, 42),
    HYDROGEN_SULFIDE(34.081, -31.42, 3.008, 1.43, 0.01, 30, 100, 76);

    /** Паралич обоняния сероводородом, ppm (09 §9.11.8). */
    public static final double H2S_ANOSMIA_PPM = 100;

    /** HF на кг UF₆ при полном гидролизе: 4·20,006/352,02. */
    public static final double HF_PER_UF6 = 4 * 20.006 / 352.02;

    public final double molarMassG, probitA, probitB, probitN, odourPpm, erpg2Ppm, idlhPpm, aegl3Ppm;

    ToxicGas(double molarMassG, double probitA, double probitB, double probitN, double odourPpm, double erpg2Ppm, double idlhPpm,
            double aegl3Ppm) {
        this.molarMassG = molarMassG;
        this.probitA = probitA;
        this.probitB = probitB;
        this.probitN = probitN;
        this.odourPpm = odourPpm;
        this.erpg2Ppm = erpg2Ppm;
        this.idlhPpm = idlhPpm;
        this.aegl3Ppm = aegl3Ppm;
    }

    /** Концентрация в ppm (25 °C, 1 атм). */
    public double ppm(double kgM3) {
        return kgM3 * 1e6 * 24.45 / molarMassG;
    }

    /** Прирост токсической нагрузки за {@code minutes} при концентрации {@code ppm}. */
    public double load(double ppm, double minutes) {
        return Math.pow(Math.max(0, ppm), probitN) * minutes;
    }

    /** Пробит по нагрузке Σ Cⁿ·Δt. */
    public double probit(double load) {
        return load <= 0 ? Double.NEGATIVE_INFINITY : probitA + probitB * Math.log(load);
    }

    /** Вероятность гибели по нагрузке. */
    public double lethality(double load) {
        double pr = probit(load);
        return Double.isInfinite(pr) ? 0 : GasDispersion.normalCdf(pr - 5);
    }

    /** Концентрация, убивающая половину за {@code minutes}, ppm. */
    public double lc50Ppm(double minutes) {
        return Math.pow(Math.exp((5 - probitA) / probitB) / minutes, 1 / probitN);
    }
}
