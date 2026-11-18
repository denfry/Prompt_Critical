package dev.denfry.promptcritical.sim.chlor;

/**
 * Облако хлора: гауссова модель приземного источника (непрерывный факел и мгновенный клуб) с дисперсиями
 * Бриггса для открытой местности и классами устойчивости Пасквилла (Seinfeld, Pandis, «Atmospheric Chemistry and
 * Physics», гл. 18; CCPS, «Guidelines for Consequence Analysis of Chemical Releases», 1999, разд. 2.3; Briggs 1973).
 * Хлор в 2,5 раза тяжелее воздуха и у источника стелется тяжёлым облаком; гауссова модель этого не учитывает и
 * завышает разбавление вблизи, но на сотнях метров даёт верный порядок — честная минимальная модель, без SLAB/DEGADIS.
 *
 * <p>Токсическая нагрузка — пробит CCPS для хлора: Pr = −8,29 + 0,92·ln(Σ C²·Δt), C в ppm, t в минутах. Пороги:
 * запах — 0,3 ppm; ERPG-2 — 3 ppm; IDLH — 10 ppm; AEGL-3 (10 мин) — 50 ppm.
 *
 * <p>Ветер — метеорологически: {@code fromRad} — откуда дует, 0 — с севера (−Z), π/2 — с востока (+X).
 */
public final class ChlorineCloud {
    public static final double PPM_PER_MG_M3 = 24.45 / 70.906;
    public static final double PROBIT_A = -8.29, PROBIT_B = 0.92;
    public static final double ODOUR_PPM = 0.3, ERPG2_PPM = 3, IDLH_PPM = 10, AEGL3_PPM = 50;

    public enum Stability {
        A(0.22, 0.20), B(0.16, 0.12), C(0.11, 0.08), D(0.08, 0.06), E(0.06, 0.03), F(0.04, 0.016);

        final double ky, kz;

        Stability(double ky, double kz) {
            this.ky = ky;
            this.kz = kz;
        }

        /** σy, м, на расстоянии x по ветру (Briggs, открытая местность). */
        public double sigmaY(double x) {
            return ky * x / Math.sqrt(1 + 0.0001 * x);
        }

        /** σz, м. */
        public double sigmaZ(double x) {
            return switch (this) {
                case A -> kz * x;
                case B -> kz * x;
                case C -> kz * x / Math.sqrt(1 + 0.0002 * x);
                case D -> kz * x / Math.sqrt(1 + 0.0015 * x);
                case E, F -> kz * x / (1 + 0.0003 * x);
            };
        }
    }

    private ChlorineCloud() {
    }

    /**
     * Класс устойчивости по Пасквиллу: днём — по ветру на 10 м и радиации, ночью — по ветру (облачность не
     * моделируется: ночь считается ясной).
     */
    public static Stability stability(double windMs, double solarWm2) {
        if (solarWm2 > 50) {
            boolean strong = solarWm2 > 600, moderate = solarWm2 > 300;
            if (windMs < 2) return strong ? Stability.A : moderate ? Stability.A : Stability.B;
            if (windMs < 3) return strong ? Stability.A : moderate ? Stability.B : Stability.C;
            if (windMs < 5) return strong ? Stability.B : moderate ? Stability.B : Stability.C;
            if (windMs < 6) return strong ? Stability.C : moderate ? Stability.C : Stability.D;
            return strong ? Stability.C : Stability.D;
        }
        if (windMs < 3) return Stability.F;
        if (windMs < 5) return Stability.E;
        return Stability.D;
    }

    /** Координаты точки в системе ветра: x — по ветру от источника, y — поперёк, м. */
    public static double[] windFrame(double dxEast, double dzSouth, double fromRad) {
        // ветер дует в сторону fromRad + π: на юг (+Z) при северном ветре
        double ux = -Math.sin(fromRad), uz = Math.cos(fromRad);
        return new double[] {dxEast * ux + dzSouth * uz, -dxEast * uz + dzSouth * ux};
    }

    /**
     * Приземная концентрация от непрерывного приземного источника, кг/м³.
     *
     * @param qKgS выброс, кг/с
     */
    public static double plumeKgM3(double qKgS, double windMs, Stability s, double x, double y) {
        if (qKgS <= 0 || x <= 0) return 0;
        x = Math.max(x, 1);
        double u = Math.max(1, windMs), sy = s.sigmaY(x), sz = s.sigmaZ(x);
        return qKgS / (Math.PI * sy * sz * u) * Math.exp(-y * y / (2 * sy * sy));
    }

    /**
     * Приземная концентрация от мгновенного приземного выброса через {@code tS} секунд, кг/м³. Центр клуба уходит по
     * ветру; σx принят равным σy (CCPS 2.3.1).
     */
    public static double puffKgM3(double massKg, double windMs, Stability s, double x, double y, double tS) {
        if (massKg <= 0 || tS <= 0) return 0;
        double u = Math.max(1, windMs), xc = u * tS, d = Math.max(1, xc);
        double sx = Math.max(1, s.sigmaY(d)), sy = sx, sz = Math.max(1, s.sigmaZ(d));
        return 2 * massKg / (Math.pow(2 * Math.PI, 1.5) * sx * sy * sz)
                * Math.exp(-(x - xc) * (x - xc) / (2 * sx * sx) - y * y / (2 * sy * sy));
    }

    public static double ppm(double kgM3) {
        return kgM3 * 1e6 * PPM_PER_MG_M3;
    }

    /** Пробит по токсической нагрузке Σ C²·Δt (ppm²·мин). */
    public static double probit(double loadPpm2Min) {
        return loadPpm2Min <= 0 ? Double.NEGATIVE_INFINITY : PROBIT_A + PROBIT_B * Math.log(loadPpm2Min);
    }

    /** Вероятность смертельного исхода по пробиту. */
    public static double lethality(double loadPpm2Min) {
        double pr = probit(loadPpm2Min);
        return Double.isInfinite(pr) ? 0 : normalCdf(pr - 5);
    }

    static double normalCdf(double z) {
        // Abramowitz–Stegun 7.1.26
        double t = 1 / (1 + 0.3275911 * Math.abs(z) / Math.sqrt(2));
        double erf = 1 - t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
                * Math.exp(-z * z / 2);
        return z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
    }
}
