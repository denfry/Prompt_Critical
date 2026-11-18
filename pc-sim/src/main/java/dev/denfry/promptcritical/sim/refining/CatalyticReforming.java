package dev.denfry.promptcritical.sim.refining;

/**
 * Каталитический риформинг тяжёлой нафты на Pt-Re/Al₂O₃: три адиабатических реактора с неподвижным слоем и печами
 * перед каждым (прототип Л-35-11/600). Модель Смита (Smith R.B., «Kinetic analysis of naphtha reforming with platinum
 * catalyst», Chem. Eng. Prog. 55(6), 1959) в лампах: парафины P, нафтены N, ароматика A (все — C₈), водород и газ
 * гидрокрекинга C₁–C₅:
 * <pre>
 * N ⇌ A + 3 H₂          r₁ = k₁(p_N − p_A·p_H₂³/K₁)        ΔH = +210 кДж/моль
 * N + H₂ ⇌ P            r₂ = k₂(p_N·p_H₂ − p_P/K₂)         ΔH = −40 кДж/моль
 * N + n/3 H₂ → газ      r₃ = k₃·p_N/p                       ΔH = −50 кДж/моль
 * P + (n−3)/3 H₂ → газ  r₄ = k₄·p_P/p                       ΔH = −50 кДж/моль
 * </pre>
 * k₁ = 9,87·exp(23,21 − 34 750/T'), k₂ = 9,87·exp(35,98 − 59 600/T'), k₃ = k₄ = exp(42,97 − 62 300/T') (кмоль/(ч·кг
 * кат.), давления в атм), K₁ = exp(46,15 − 46 045/T') атм³, K₂ = exp(8000/T' − 7,12) атм⁻¹,
 * T' = 1,8·T — градусы Ренкина (Smith 1959; в изложении Rase, «Chemical Reactor Design for Process Plants», т. 2).
 * Множитель 9,87 из GDD у констант равновесия опущен: с ним K₁ при 500 °C ≈ 5·10² атм³ и K₂ ≈ 2,5 атм⁻¹, что
 * противоречит термодинамике (C₆H₁₂ → C₆H₆ + 3 H₂: ΔG°(773 К) ≈ −95 кДж/моль, K ≈ 10⁶ атм³; н-C₇ ⇌ МЦГ + H₂ — K₂ ≈
 * 0,2–0,3 атм⁻¹) и при 25 атм H₂ гонит ароматику обратно в нафтены. Константы гидрокрекинга умножены на
 * {@link #HYDROCRACKING_SCALE} [BALANCE: современный Pt-Re крекирует меньше Pt-катализатора 1959 г.].
 *
 * <p><b>Ограничение модели.</b> Лампы Смита с парафиновым сырьём (P 50 %) дают ОЧИ 80–85 при 500–515 °C: медленная
 * дегидроциклизация парафинов во втором и третьем реакторах, где поток уже остыл. ОЧИ 95+ требует нафтенового сырья
 * или более жёсткого режима.
 *
 * <p>ОЧИ реформата — корреляция по ароматике жидкого продукта: RON ≈ 58 + 0,58·A, % об. [BALANCE-аппроксимация GDD
 * 09 §9.9.4: 68 % → 97,4]. Тепло реакций снимается с потока: в первом реакторе температура падает на 50–70 К, и это
 * видно по слою.
 *
 * <p><b>Дезактивация</b>: кокс dC/dt = k_c·e^{−E_c/RT}·p_A/p_H₂, активность a_c = e^{−C}; сера обратимо
 * a_S = 1/(1 + 2·S_ppm); спекание Pt выше 560 °C — необратимая потеря доли активности. Цикл до регенерации при
 * 500 °C, 2,5 МПа и H₂/сырьё 6 — около 9 мес. физ.
 */
public final class CatalyticReforming {
    public static final int REACTORS = 3, CELLS = 400;
    public static final double CARBON = 8, M_P = 114.23, M_N = 112.21, M_A = 106.17, M_H2 = 2.016;
    /** Газ гидрокрекинга C₁–C₅ поровну по молям (Smith): средняя молярная масса. */
    public static final double M_GAS = (16.04 + 30.07 + 44.10 + 58.12 + 72.15) / 5;
    public static final double DH1 = 210e3, DH2 = -40e3, DH34 = -50e3;
    /** Теплоёмкости газа, Дж/(моль·К) при 450–520 °C: C₈ ≈ 250, H₂ 29,5, лёгкие ≈ 80. */
    public static final double CP_HC = 250, CP_H2 = 29.5, CP_GAS = 80;
    public static final double HYDROCRACKING_SCALE = 0.5;
    /** Распределение катализатора по реакторам (типично 15/30/55 %). */
    public static final double[] CATALYST_SPLIT = {0.15, 0.30, 0.55};
    public static final double NAPHTHA_KG_M3 = 750, CATALYST_BULK_KG_M3 = 650;
    public static final double SINTER_K = 560 + 273.15, SULFUR_POISON_PER_PPM = 2;
    /** Плотности жидких ламп для % об., кг/м³. */
    public static final double RHO_P = 703, RHO_N = 770, RHO_A = 867;
    static final double COKE_REF = 1.0 / (9 * 30.4 * 86400), COKE_E = 120e3, R = 8.314, COKE_T_REF = 773.15;

    /** Состав сырья, мольные доли P, N, A. */
    public record Feed(double p, double n, double a) {
        /** Тяжёлая прямогонная нафта 85–180 °C западносибирской нефти: P 50, N 40, A 10 % мол. */
        public static final Feed HEAVY_NAPHTHA = new Feed(0.50, 0.40, 0.10);

        public double molarMass() {
            return p * M_P + n * M_N + a * M_A;
        }
    }

    /**
     * @param ron              ОЧИ реформата
     * @param reformateMass    выход реформата, доля массы сырья
     * @param reformateVolume  выход реформата, доля объёма сырья
     * @param h2Mass           водород, доля массы сырья
     * @param gasMass          газ гидрокрекинга C₁–C₅, доля массы сырья
     * @param aromaticsVol     ароматика в реформате, доля объёма
     * @param outletK          температура на выходе каждого реактора
     */
    public record Result(double ron, double reformateMass, double reformateVolume, double h2Mass, double gasMass, double aromaticsVol,
            double[] outletK) {
    }

    private double coke, sinterLoss;

    public static double k1(double tK) {
        return 9.87 * Math.exp(23.21 - 34_750 / (1.8 * tK));
    }

    public static double k2(double tK) {
        return 9.87 * Math.exp(35.98 - 59_600 / (1.8 * tK));
    }

    public static double k34(double tK) {
        return Math.exp(42.97 - 62_300 / (1.8 * tK));
    }

    public static double eq1(double tK) {
        return Math.exp(46.15 - 46_045 / (1.8 * tK));
    }

    public static double eq2(double tK) {
        return Math.exp(8000 / (1.8 * tK) - 7.12);
    }

    /** ОЧИ по ароматике, % об. */
    public static double ron(double aromaticsVolPct) {
        return 58 + 0.58 * aromaticsVolPct;
    }

    /**
     * Проход через три реактора.
     *
     * @param inletK    температура на входе каждого реактора (после печи)
     * @param pressureMPa давление в реакторах
     * @param h2Ratio   мольное отношение циркулирующего H₂ к сырью
     * @param lhsvPerH  объёмная скорость сырья по всему катализатору, ч⁻¹
     * @param feedSulfurPpm сера в сырье
     */
    public Result run(Feed feed, double inletK, double pressureMPa, double h2Ratio, double lhsvPerH, double feedSulfurPpm) {
        double pAtm = pressureMPa * 1e6 / 101_325;
        double act = activity() / (1 + SULFUR_POISON_PER_PPM * Math.max(0, feedSulfurPpm));
        // на 1 кмоль/ч сырья: масса сырья кг/ч и масса катализатора кг
        double feedKg = feed.molarMass();
        double catKg = feedKg / NAPHTHA_KG_M3 / Math.max(0.1, lhsvPerH) * CATALYST_BULK_KG_M3;
        double fp = feed.p(), fn = feed.n(), fa = feed.a(), fh = h2Ratio, fg = 0, h2Made = 0;
        double[] out = new double[REACTORS];
        for (int r = 0; r < REACTORS; r++) {
            double t = inletK, dw = catKg * CATALYST_SPLIT[r] / CELLS;
            for (int c = 0; c < CELLS; c++) {
                double tot = fp + fn + fa + fh + fg;
                double pp = pAtm * fp / tot, pn = pAtm * fn / tot, pa = pAtm * fa / tot, ph = pAtm * fh / tot;
                double r1 = act * k1(t) * (pn - pa * ph * ph * ph / eq1(t));
                double r2 = act * k2(t) * (pn * ph - pp / eq2(t));
                double r3 = act * HYDROCRACKING_SCALE * k34(t) * pn / pAtm;
                double r4 = act * HYDROCRACKING_SCALE * k34(t) * pp / pAtm;
                // явный шаг с ограничением, чтобы не уйти в отрицательные потоки
                double dn = (r1 + r2 + r3) * dw, scale = dn > 0.9 * fn ? 0.9 * fn / dn : 1;
                double dp = (r4 - r2) * dw;
                if (dp > 0.9 * fp) scale = Math.min(scale, 0.9 * fp / dp);
                if (r1 < 0 && -r1 * dw > 0.9 * fa) scale = Math.min(scale, 0.9 * fa / (-r1 * dw));
                r1 *= scale;
                r2 *= scale;
                r3 *= scale;
                r4 *= scale;
                double h2 = 3 * r1 - r2 - CARBON / 3 * r3 - (CARBON - 3) / 3 * r4;
                double heat = r1 * DH1 + r2 * DH2 + (r3 + r4) * DH34;
                fn -= (r1 + r2 + r3) * dw;
                fa += r1 * dw;
                fp += (r2 - r4) * dw;
                fh += h2 * dw;
                h2Made += h2 * dw;
                fg += CARBON / 3 * (r3 + r4) * dw;
                double cp = (fp + fn + fa) * CP_HC + fh * CP_H2 + fg * CP_GAS;
                t -= heat * dw / cp;
            }
            out[r] = t;
        }
        double kgP = fp * M_P, kgN = fn * M_N, kgA = fa * M_A;
        double liquidKg = kgP + kgN + kgA;
        double volA = kgA / RHO_A, vol = kgP / RHO_P + kgN / RHO_N + volA;
        double feedVol = feed.p() * M_P / RHO_P + feed.n() * M_N / RHO_N + feed.a() * M_A / RHO_A;
        double aromVol = vol > 0 ? volA / vol : 0;
        return new Result(ron(100 * aromVol), liquidKg / feedKg, vol / feedVol, h2Made * M_H2 / feedKg, fg * M_GAS / feedKg, aromVol, out);
    }

    /** Закоксовывание за {@code dtS} физ. секунд: быстрее при высокой T и низком H₂/сырьё. */
    public void age(double dtS, double bedK, double h2Ratio, double aromaticsVol) {
        double rate = COKE_REF * Math.exp(-COKE_E / R * (1 / bedK - 1 / COKE_T_REF)) * (6 / Math.max(1, h2Ratio)) * (aromaticsVol / 0.65);
        coke += rate * Math.max(0, dtS);
        if (bedK > SINTER_K) sinterLoss = Math.min(1, sinterLoss + 1e-4 * dtS * (bedK - SINTER_K) / 10);
    }

    /** Выжиг кокса и оксихлорирование: без хлора дисперсность Pt не восстанавливается (×0,85). */
    public void regenerate(boolean chlorine) {
        coke = 0;
        if (!chlorine) sinterLoss = 1 - (1 - sinterLoss) * 0.85;
    }

    public double activity() {
        return Math.exp(-coke) * (1 - sinterLoss);
    }

    public double coke() {
        return coke;
    }

    public double sinterLoss() {
        return sinterLoss;
    }

    public void restore(double coke, double sinterLoss) {
        this.coke = Math.max(0, coke);
        this.sinterLoss = Math.clamp(sinterLoss, 0, 1);
    }

    public void loadCatalyst() {
        coke = 0;
        sinterLoss = 0;
    }
}
