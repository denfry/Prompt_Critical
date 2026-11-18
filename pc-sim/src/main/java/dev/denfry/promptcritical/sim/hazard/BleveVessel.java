package dev.denfry.promptcritical.sim.hazard;

/**
 * Ёмкость со сжиженным газом под пожаром (09 §9.8.4, §9.11.6): {@code gas_storage_bullet}. Детерминированная модель
 * с сосредоточенными параметрами:
 * <ul>
 *   <li>Жидкость греется потоком через смоченную стенку; давление — насыщение по Клаузиусу — Клапейрону от нормальной
 *   точки кипения. Дойдя до уставки предохранительного клапана (= расчётное давление), клапан открывается и сбрасывает
 *   пар с расходом Q_смоч/r — горящий «факел» клапана (§9.11.5), жидкость перестаёт греться.</li>
 *   <li>Стенка над жидкостью (паровое пространство) не охлаждается кипением: ρcδ·dT/dt = q − εσ(T⁴ − T₀⁴) − h(T − T_ж).
 *   Сталь теряет предел текучести по EN 1993-1-2 (табл. 3.1: 600 °C — 0,47, «50 % прочности» §9.11.6).</li>
 *   <li>Сосуд с допускаемым напряжением S_y/1,5 (ASME VIII) течёт, когда давление превышает 1,5·p_расч·k_y(T_стенки),
 *   — BLEVE. При клапане на расчётном давлении это k_y &lt; 2/3, около 535 °C.</li>
 *   <li>Орошение водой (нормы NFPA 15: 10,2 л/(мин·м²), испарение снимает ≈ 440 кВт/м²) снимает весь поток пожара и
 *   охлаждает стенку к 100 °C с постоянной {@value #DELUGE_TAU_S} с [BALANCE] — ниже 300 °C, как требует §9.11.6.</li>
 * </ul>
 * Энергия ударной волны — по Броуду для пара E = (p − pₐ)V/(k − 1) плюс то же для пара, вскипевшего из перегретой
 * жидкости (доля вскипания 1 − exp(−c_p(T − T_кип)/r), CCPS), доля в ударную волну 0,4 (§9.11.6).
 */
public final class BleveVessel {
    /** Сжиженный газ: T_кип, К; r при T_кип, Дж/кг; c_p жидкости, Дж/(кг·К); плотность жидкости, кг/м³; k пара; μ, кг/моль (Perry [сверить]). */
    public enum Liquefied {
        PROPANE(231.1, 426e3, 2520, 507, 1.13, 0.044097, Flammable.PROPANE),
        BUTANE(272.7, 385e3, 2400, 575, 1.10, 0.058123, Flammable.BUTANE);

        public final double boilingK, latentJkg, cpJkgK, liquidDensity, k, molarKgMol;
        public final Flammable gas;

        Liquefied(double boilingK, double latentJkg, double cpJkgK, double liquidDensity, double k, double molarKgMol, Flammable gas) {
            this.boilingK = boilingK;
            this.latentJkg = latentJkg;
            this.cpJkgK = cpJkgK;
            this.liquidDensity = liquidDensity;
            this.k = k;
            this.molarKgMol = molarKgMol;
            this.gas = gas;
        }

        /** Давление насыщения при {@code tK}, Па (абс.). */
        public double saturationPa(double tK) {
            return ATM_PA * Math.exp(latentJkg * molarKgMol / Flammable.R * (1 / boilingK - 1 / tK));
        }
    }

    public static final double ATM_PA = 101_325, AMBIENT_K = 293.15;
    /** Сталь стенки: плотность, кг/м³; теплоёмкость при 300–600 °C, Дж/(кг·К); степень черноты. */
    public static final double STEEL_DENSITY = 7850, STEEL_CP = 600, EMISSIVITY = 0.8, SIGMA = 5.670374e-8;
    /** Теплоотдача стенки парового пространства к пару, Вт/(м²·К) [моё: естественная конвекция газа]. */
    public static final double VAPOUR_HTC = 10;
    /** Доля энергии расширения в ударную волну (CCPS, §9.11.6). */
    public static final double BLAST_FRACTION = 0.4;
    /** Запас по текучести допускаемого напряжения (ASME VIII Div. 1). */
    public static final double YIELD_MARGIN = 1.5;
    /** Отвод тепла орошением, кВт/м² (10,2 л/(мин·м²) · (c·80 К + r)); стенка под орошением, К; постоянная, с. */
    public static final double DELUGE_CAPACITY_KW_M2 = 440, DELUGE_WALL_K = 373.15, DELUGE_TAU_S = 30;

    private static final double[] KY_C = {400, 500, 600, 700, 800, 900, 1000, 1100, 1200};
    private static final double[] KY = {1.0, 0.78, 0.47, 0.23, 0.11, 0.06, 0.04, 0.02, 0.0};

    private final Liquefied fluid;
    private final double volumeM3, designPa, wallM, areaM2;
    private double liquidKg, liquidK = AMBIENT_K, wallK = AMBIENT_K, reliefKgS;
    private boolean failed;

    /**
     * @param volumeM3 вместимость (цилиндр L = 4D)
     * @param designPa расчётное давление (абс.) = уставка клапана
     * @param wallM    толщина стенки
     */
    public BleveVessel(Liquefied fluid, double volumeM3, double designPa, double wallM) {
        if (!(volumeM3 > 0 && designPa > ATM_PA && wallM > 0)) throw new IllegalArgumentException("vessel");
        this.fluid = fluid;
        this.volumeM3 = volumeM3;
        this.designPa = designPa;
        this.wallM = wallM;
        double d = Math.cbrt(volumeM3 / Math.PI);
        this.areaM2 = 4.5 * Math.PI * d * d;
    }

    /** Предел текучести стали при {@code tC} в долях от 20 °C (EN 1993-1-2, табл. 3.1). */
    public static double yieldFactor(double tC) {
        if (tC <= KY_C[0]) return 1;
        for (int i = 1; i < KY_C.length; i++)
            if (tC <= KY_C[i]) return KY[i - 1] + (KY[i] - KY[i - 1]) * (tC - KY_C[i - 1]) / (KY_C[i] - KY_C[i - 1]);
        return 0;
    }

    public Liquefied fluid() {
        return fluid;
    }

    public double areaM2() {
        return areaM2;
    }

    public double liquidKg() {
        return liquidKg;
    }

    public void setLiquidKg(double kg) {
        liquidKg = Math.clamp(kg, 0, volumeM3 * fluid.liquidDensity);
    }

    public double capacityKg() {
        return volumeM3 * fluid.liquidDensity;
    }

    public double fillFraction() {
        return liquidKg / capacityKg();
    }

    public double liquidK() {
        return liquidK;
    }

    public double wallK() {
        return wallK;
    }

    /** Стенд и проверки: задать температуру стенки. */
    public void setWallK(double k) {
        wallK = k;
    }

    public void setLiquidK(double k) {
        liquidK = k;
    }

    public boolean failed() {
        return failed;
    }

    /** Сброс через предохранительный клапан на последнем шаге, кг/с. */
    public double reliefKgS() {
        return reliefKgS;
    }

    public boolean reliefOpen() {
        return reliefKgS > 0;
    }

    /** Давление в сосуде (абс.): насыщение, не выше уставки клапана, пока он справляется. */
    public double pressurePa() {
        if (liquidKg <= 0) return ATM_PA;
        return Math.min(fluid.saturationPa(liquidK), designPa);
    }

    /** Давление, при котором стенка парового пространства потечёт, Па. */
    public double failurePa() {
        return YIELD_MARGIN * designPa * yieldFactor(wallK - 273.15);
    }

    /**
     * Шаг {@code dt} секунд.
     *
     * @param fireKwM2 поток пожара на поверхность сосуда, кВт/м² (0 — пожара нет)
     * @param deluge   орошение водой работает
     */
    public void step(double dt, double fireKwM2, boolean deluge) {
        if (failed || dt <= 0) return;
        reliefKgS = 0;
        double fill = Math.clamp(fillFraction(), 0, 1);
        double q = Math.max(0, fireKwM2) * 1000;
        if (deluge && fireKwM2 <= DELUGE_CAPACITY_KW_M2) {
            q = 0;
            wallK += (DELUGE_WALL_K - wallK) * -Math.expm1(-dt / DELUGE_TAU_S);
        } else {
            double loss = EMISSIVITY * SIGMA * (Math.pow(wallK, 4) - Math.pow(AMBIENT_K, 4)) + VAPOUR_HTC * (wallK - liquidK);
            wallK += (q - loss) * dt / (STEEL_DENSITY * STEEL_CP * wallM);
            wallK = Math.max(wallK, Math.min(liquidK, AMBIENT_K));
        }
        if (liquidKg > 0 && q > 0) {
            double wetW = q * areaM2 * fill;
            if (fluid.saturationPa(liquidK) < designPa) {
                liquidK += wetW * dt / (liquidKg * fluid.cpJkgK);
            } else {
                reliefKgS = Math.min(wetW / fluid.latentJkg, liquidKg / dt);
                liquidKg -= reliefKgS * dt;
            }
        } else if (liquidKg > 0 && liquidK > AMBIENT_K) {
            // без пожара жидкость медленно остывает через стенку [моё: та же конвекция]
            liquidK -= Math.min(liquidK - AMBIENT_K, VAPOUR_HTC * areaM2 * (liquidK - AMBIENT_K) * dt / (liquidKg * fluid.cpJkgK));
        }
        if (liquidKg > 0 && pressurePa() >= failurePa()) failed = true;
    }

    /** Доля жидкости, вскипающая при сбросе давления до атмосферного (CCPS). */
    public double flashFraction() {
        return -Math.expm1(-fluid.cpJkgK * Math.max(0, liquidK - fluid.boilingK) / fluid.latentJkg);
    }

    /** Энергия расширения пара и вскипевшей жидкости по Броуду, Дж. */
    public double expansionEnergyJ() {
        double p = pressurePa(), dp = Math.max(0, p - ATM_PA);
        double vapourM3 = volumeM3 * (1 - Math.clamp(fillFraction(), 0, 1));
        double flashM3 = liquidKg * flashFraction() * Flammable.R * liquidK / (p * fluid.molarKgMol);
        return dp * (vapourM3 + flashM3) / (fluid.k - 1);
    }

    /** Механическая энергия ударной волны BLEVE, Дж. */
    public double blastEnergyJ() {
        return BLAST_FRACTION * expansionEnergyJ();
    }

    /** Масса огненного шара: всё содержимое, кг. */
    public double fireballMassKg() {
        return liquidKg;
    }
}
