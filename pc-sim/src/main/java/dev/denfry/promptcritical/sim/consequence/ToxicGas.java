package dev.denfry.promptcritical.sim.consequence;

/**
 * Токсичный газ выброса: молярная масса для перевода кг/м³ в ppm, пробит-функция гибели Pr = a + b·ln(Σ Cⁿ·Δt)
 * (C в ppm, t в минутах) и пороги самочувствия.
 * <ul>
 *   <li>Хлор — пробит CCPS (1999): a = −8,29, b = 0,92, n = 2. Запах 0,3 ppm, ERPG-2 3, IDLH 10, AEGL-3 (10 мин) 50.</li>
 *   <li>Фтористый водород — пробит TNO («Green Book», 1992: a = −8,4, b = 1, n = 1,5 в мг/м³), переведённый в ppm:
 *   a = −8,4 + 1,5·ln(20,006/24,45) = −8,70. Запах (заметный) 0,5 ppm, ERPG-2 20, IDLH 30, AEGL-3 (10 мин) 170.
 *   Источник HF — и прямая утечка, и гидролиз UF₆ во влажном воздухе: UF₆ + 2 H₂O → UO₂F₂ + 4 HF.</li>
 * </ul>
 */
public enum ToxicGas {
    CHLORINE(70.906, -8.29, 0.92, 2, 0.3, 3, 10, 50),
    HYDROGEN_FLUORIDE(20.006, -8.70, 1.0, 1.5, 0.5, 20, 30, 170);

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
