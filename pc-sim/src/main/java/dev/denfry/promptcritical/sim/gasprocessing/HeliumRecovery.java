package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Извлечение гелия из природного газа низкотемпературной конденсацией, как на Оренбургском гелиевом заводе (1978):
 * в газе 0,055 % He; после аминовой очистки и осушки газ охлаждают в криоблоке до −100…−105 °C при 4 МПа — метан и
 * тяжелее конденсируются (ШФЛУ и метан уходят в товарный газ после регенерации холода), в паре остаётся азот с гелием.
 * Вторая ступень при ≈ 95 К (жидкий азот, кипящий под 0,6 МПа) конденсирует почти весь азот — гелиевый концентрат
 * 70–95 % He; гелий, растворённый в жидкости первой ступени, выделяют дросселированием в отпарную колонну и тоже
 * подают во вторую ступень. Доочистка — адсорбция на активированном угле при 77 К (вымораживание остатков N₂, CH₄, Ne, H₂ — как
 * последний шаг ГОСТ 20461) до 99,995 % (Кириллин В. А. и др., «Техническая термодинамика», гл. о сжижении газов;
 * Архаров А. М., «Криогенные системы», т. 2; Kidnay, Parrish, «Fundamentals of Natural Gas Processing», гл. 12
 * «Nitrogen rejection and helium recovery»).
 *
 * <p>Равновесие — Rachford–Rice с константами Уилсона для углеводородов и азота ({@link Flash#wilsonK}); гелий почти
 * не растворяется — K_He задан по закону Генри: растворимость гелия в жидком метане при 4 МПа ≈ 0,07 % (K ≈ 1500),
 * в жидком азоте при 95 К и 2,5 МПа ≈ 1 % (K ≈ 100) [Streett, Hill, данные He–CH₄ и He–N₂; точность — десятки
 * процентов, на извлечение влияет слабо]. Извлечение гелия на заводах — 90–98 %.
 */
public final class HeliumRecovery {
    /** Гелий в газе месторождения, мольная доля [Оренбург — 0,055 %; бывает 0,01–7 %]. */
    public static final double HE_IN_GAS = 0.00055;
    public static final double P1_PA = 4.0e6, P2_PA = 2.5e6, T2_K = 95, K_HE1 = 1500, K_HE2 = 100;
    public static final double MIN_K = 150, MAX_K = 185;
    /** Адсорбер доочистки: извлечение гелия и чистота при полной подаче азота. */
    public static final double PURIFIER_RECOVERY = 0.98, PURITY = 0.99995;
    /** Доля гелия, выделяемого из жидкости первой ступени при её дросселировании в колонну отпарки. */
    public static final double STRIP = 0.9;
    /** Жидкий азот: на нм³ пара первой ступени (конденсация N₂ во второй ступени и охлаждение адсорбера) и на нм³
     * сырья (потери криоблока), кг. */
    public static final double LN2_PER_NM3_VAPOUR = 1.2, LN2_PER_NM3_FEED = 0.004;
    /** Производительность по газу, нм³/с [BALANCE]. */
    public static final double CAPACITY_NM3_S = 10;

    private double t1K = 170;
    private boolean on = true;
    private double heNm3S, crudePurity, recovery, ln2KgS, returnNm3S, nitrogenNm3S, vapour1;

    public double t1K() {
        return t1K;
    }

    public void setT1K(double k) {
        t1K = Math.clamp(k, MIN_K, MAX_K);
    }

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    /** Товарный газ после НТС и аминовой очистки: метан с азотом и остатком этана, пропана. */
    public static double[] feedComposition() {
        double[] z = new double[GasComponent.N];
        z[GasComponent.C1.ordinal()] = 0.925;
        z[GasComponent.N2.ordinal()] = 0.030;
        z[GasComponent.C2.ordinal()] = 0.035;
        z[GasComponent.C3.ordinal()] = 0.010;
        return z;
    }

    /** Двухфазное равновесие с гелием: доля пара и составы; гелий — последний элемент массивов. */
    record Split(double vapour, double[] y, double[] x) {
    }

    static Split flash(double[] zWithHe, double[] k) {
        int n = zWithHe.length;
        double lo = 0, hi = 1;
        if (rr(zWithHe, k, 0) <= 0) return new Split(0, new double[n], zWithHe.clone());
        if (rr(zWithHe, k, 1) >= 0) return new Split(1, zWithHe.clone(), new double[n]);
        for (int i = 0; i < 100; i++) {
            double v = (lo + hi) / 2;
            if (rr(zWithHe, k, v) > 0) lo = v;
            else hi = v;
        }
        double v = (lo + hi) / 2;
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = zWithHe[i] / (1 + v * (k[i] - 1));
            y[i] = k[i] * x[i];
        }
        return new Split(v, y, x);
    }

    private static double rr(double[] z, double[] k, double v) {
        double s = 0;
        for (int i = 0; i < z.length; i++) s += z[i] * (k[i] - 1) / (1 + v * (k[i] - 1));
        return s;
    }

    private static double[] kValues(double t, double p, double kHe) {
        double[] k = new double[GasComponent.N + 1];
        for (GasComponent c : GasComponent.values()) k[c.ordinal()] = Flash.wilsonK(c, t, p);
        k[GasComponent.N] = kHe;
        return k;
    }

    /**
     * Переработка {@code feedNm3S} газа.
     *
     * @param ln2Available жидкий азот, доступный на шаг, кг/с
     */
    public void step(double feedNm3S, double ln2Available) {
        heNm3S = crudePurity = recovery = ln2KgS = returnNm3S = nitrogenNm3S = vapour1 = 0;
        double q = on ? Math.clamp(feedNm3S, 0, CAPACITY_NM3_S) : 0;
        if (q <= 0) return;
        double[] z0 = feedComposition();
        double[] z = new double[GasComponent.N + 1];
        for (int i = 0; i < GasComponent.N; i++) z[i] = z0[i] * (1 - HE_IN_GAS);
        z[GasComponent.N] = HE_IN_GAS;
        Split s1 = flash(z, kValues(t1K, P1_PA, K_HE1));
        vapour1 = s1.vapour;
        Split s2 = flash(s1.y, kValues(T2_K, P2_PA, K_HE2));
        // гелий, растворённый в жидкости первой ступени, выделяется при её дросселировании и отпарке и идёт во вторую
        double heVap1 = s1.vapour * s1.y[GasComponent.N], heLiq1 = (1 - s1.vapour) * s1.x[GasComponent.N];
        double heToStage2 = heVap1 + STRIP * heLiq1;
        double stage2HeShare = s1.y[GasComponent.N] > 0 ? s2.vapour * s2.y[GasComponent.N] / s1.y[GasComponent.N] : 0;
        double crudeMol = s1.vapour * s2.vapour, heCrude = heToStage2 * stage2HeShare;
        crudePurity = crudeMol > 0 ? s2.y[GasComponent.N] : 0;
        // жидкий азот: вторая ступень и адсорбер — по пару первой ступени, криоблок — по сырью
        double needLn2 = (q * s1.vapour * LN2_PER_NM3_VAPOUR + q * LN2_PER_NM3_FEED);
        double ln2Share = needLn2 > 0 ? Math.clamp(ln2Available / needLn2, 0, 1) : 1;
        ln2KgS = needLn2 * ln2Share;
        // без азота вторая ступень не охлаждается: гелиевый концентрат не отделяется
        double he = q * heCrude * PURIFIER_RECOVERY * ln2Share;
        heNm3S = he;
        recovery = he / (q * HE_IN_GAS);
        nitrogenNm3S = q * s1.vapour * (1 - s2.vapour) * ln2Share;
        returnNm3S = q - he - nitrogenNm3S;
    }

    /** Гелий 99,995 %, нм³/с. */
    public double heliumNm3S() {
        return heNm3S;
    }

    /** Чистота концентрата после второй ступени (до адсорбера). */
    public double crudePurity() {
        return crudePurity;
    }

    /** Извлечение гелия из газа. */
    public double recovery() {
        return recovery;
    }

    public double ln2KgS() {
        return ln2KgS;
    }

    /** Газ, возвращаемый в сеть (метан с остатком азота), нм³/с. */
    public double returnNm3S() {
        return returnNm3S;
    }

    /** Азотная фракция второй ступени (на сброс), нм³/с. */
    public double nitrogenNm3S() {
        return nitrogenNm3S;
    }

    /** Доля пара первой ступени. */
    public double vapour1() {
        return vapour1;
    }
}
