package dev.denfry.promptcritical.sim.thermal;

import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.VoidFraction;

/**
 * Корреляции теплообмена и теплофизика топлива (01 §1.4) — чистые функции, тестируемые
 * по отдельности. Единицы — СИ, если в параметре не сказано иное; температуры — К.
 */
public final class HeatTransferCorrelations {
    public static final double STEFAN_BOLTZMANN = 5.670374419e-8;
    public static final double GRAVITY = VoidFraction.GRAVITY;
    public static final double CRITICAL_PRESSURE = 22.064e6;
    public static final double CRITICAL_TEMPERATURE = 647.096;
    /** Молярная масса воды, г/моль (Купер). */
    public static final double WATER_MOLAR_MASS = 18.015;
    /** Nu полностью развитого ламинарного течения в трубе при q'' = const. */
    public static final double LAMINAR_NUSSELT = 4.36;

    /** Ниже — кипение в большом объёме (Зубер), §1.4.4. */
    public static final double POOL_MASS_FLUX = 100;
    /** Выше — плёночное кипение по Дугаллу–Розеноу, ниже — по Бромли (§1.4.5). */
    public static final double FORCED_FILM_MASS_FLUX = 200;

    /** Плотность UO₂ при 100 % ТП, кг/м³. */
    public static final double UO2_THEORETICAL_DENSITY = 10_960;
    /** Температура плавления UO₂, К, и теплота плавления, Дж/кг (Fink 2000). */
    public static final double UO2_MELTING_POINT = 3120, UO2_LATENT_HEAT = 259e3;
    /** Массовая доля урана в UO₂: МВт·сут/кгТМ → МВт·сут/кгUO₂. */
    public static final double URANIUM_MASS_FRACTION = 0.8815;
    public static final double ZIRCALOY_DENSITY = 6550;
    /** Излучательные способности таблетки и оболочки в зазоре (§1.4.6). */
    public static final double PELLET_EMISSIVITY = 0.8, CLAD_EMISSIVITY = 0.75;

    // Границы применимости W-3 (§1.4.4).
    private static final double W3_P_MIN = 6.9, W3_P_MAX = 15.9, W3_G_MIN = 1356, W3_G_MAX = 6800;
    private static final double W3_X_MAX = 0.15, W3_DE_MIN = 0.005, W3_DE_MAX = 0.018;

    private HeatTransferCorrelations() {}

    // ---------------------------------------------------------------- однофазная конвекция, §1.4.2

    /** Диттус–Бёлтер: h = 0,023 Re^0,8 Pr^n k/D_h; n = 0,4 при нагреве жидкости, 0,3 при охлаждении. */
    public static double dittusBoelter(double re, double pr, double k, double dh, boolean heating) {
        return 0.023 * Math.pow(re, 0.8) * Math.pow(pr, heating ? 0.4 : 0.3) * k / dh;
    }

    /** Множитель Вейсмана к Nu для треугольной решётки ТВЭЛов: C = 1,130 p/D − 0,2609. */
    public static double weismanFactor(double pitchToDiameter) {
        return 1.130 * pitchToDiameter - 0.2609;
    }

    /**
     * Турбулентная свободная конвекция на вертикальной стенке (Мак-Адамс): Nu = 0,1 (Gr·Pr)^{1/3},
     * h от высоты не зависит. β — объёмный коэффициент расширения, 1/К.
     */
    public static double naturalConvection(double k, double rho, double cp, double mu, double beta, double deltaT) {
        double ra = GRAVITY * Math.abs(beta * deltaT) * rho * rho * cp / (mu * k);
        return 0.1 * k * Math.cbrt(ra);
    }

    // ---------------------------------------------------------------- пузырьковое кипение, §1.4.3

    /** Chen: F·h_l — конвективная часть; x ≤ 0 даёт F = 1 (поверхностное кипение). */
    public static double chenMacro(double g, double x, double dh, SaturationState sat) {
        return chenF(x, sat) * chenLiquid(g, x, dh, sat);
    }

    /** Chen: S·h_FZ — микроконвективная (пузырьковая) часть при перегреве стенки tWall − T_sat. */
    public static double chenMicro(double g, double x, double dh, double tWall, double pSatWall, SaturationState sat) {
        double dT = tWall - sat.tSat(), dP = pSatWall - sat.pressure();
        if (dT <= 0 || dP <= 0) return 0;
        double f = chenF(x, sat);
        double reTp = chenReLiquid(g, x, dh, sat) * Math.pow(f, 1.25);
        double s = 1 / (1 + 2.53e-6 * Math.pow(reTp, 1.17));
        double fz = 0.00122 * Math.pow(sat.kF(), 0.79) * Math.pow(sat.cpF(), 0.45) * Math.pow(sat.rhoF(), 0.49)
                / (Math.sqrt(sat.sigma()) * Math.pow(sat.muF(), 0.29) * Math.pow(sat.latentHeat(), 0.24)
                        * Math.pow(sat.rhoG(), 0.24));
        return s * fz * Math.pow(dT, 0.24) * Math.pow(dP, 0.75);
    }

    /** Полный коэффициент Чена h = h_mac + h_mic (насыщенное кипение, ΔT отсчитывается от T_sat). */
    public static double chen(double g, double x, double dh, double tWall, double pSatWall, SaturationState sat) {
        return chenMacro(g, x, dh, sat) + chenMicro(g, x, dh, tWall, pSatWall, sat);
    }

    private static double chenReLiquid(double g, double x, double dh, SaturationState sat) {
        return Math.abs(g) * (1 - clampQuality(x)) * dh / sat.muF();
    }

    private static double chenLiquid(double g, double x, double dh, SaturationState sat) {
        double pr = sat.muF() * sat.cpF() / sat.kF();
        return dittusBoelter(chenReLiquid(g, x, dh, sat), pr, sat.kF(), dh, true);
    }

    private static double chenF(double x, SaturationState sat) {
        double xc = clampQuality(x);
        if (xc <= 0) return 1;
        double xttInv = 1 / (Math.pow((1 - xc) / xc, 0.9) * Math.sqrt(sat.rhoG() / sat.rhoF())
                * Math.pow(sat.muF() / sat.muG(), 0.1));
        return xttInv <= 0.1 ? 1 : 2.35 * Math.pow(xttInv + 0.213, 0.736);
    }

    private static double clampQuality(double x) {
        return Math.max(0, Math.min(0.999, x));
    }

    /** Купер, кипение в большом объёме: h = 55 p_r^0,12 (−lg p_r)^−0,55 M^−0,5 q''^0,67. */
    public static double cooperPool(double pReduced, double molarMass, double heatFlux) {
        return cooperCoefficient(pReduced, molarMass) * Math.pow(heatFlux, 0.67);
    }

    /** Тепловой поток по Куперу при перегреве стенки dT: q'' = (C·dT)^{1/0,33} (h·dT = C q''^0,67 dT). */
    public static double cooperPoolFlux(double pReduced, double molarMass, double dT) {
        return dT <= 0 ? 0 : Math.pow(cooperCoefficient(pReduced, molarMass) * dT, 1 / 0.33);
    }

    private static double cooperCoefficient(double pReduced, double molarMass) {
        return 55 * Math.pow(pReduced, 0.12) * Math.pow(-Math.log10(pReduced), -0.55) / Math.sqrt(molarMass);
    }

    // ---------------------------------------------------------------- кризис, §1.4.4

    /**
     * W-3 (Tong), СИ-форма. Результат — Вт/м².
     *
     * @param pMPa       давление, МПа
     * @param g          массовая скорость, кг/(м²·с)
     * @param xe         равновесное паросодержание
     * @param de         эквивалентный диаметр, м
     * @param dhInletKJ  недогрев на входе h_f − h_in, кДж/кг
     */
    public static double chfW3(double pMPa, double g, double xe, double de, double dhInletKJ) {
        double a = (2.022 - 0.06238 * pMPa) + (0.1722 - 0.01427 * pMPa) * Math.exp((18.177 - 0.5987 * pMPa) * xe);
        double b = (0.1484 - 1.596 * xe + 0.1729 * xe * Math.abs(xe)) * 2.326 * g + 3271;
        double c = 1.157 - 0.869 * xe;
        double d = 0.2664 + 0.8357 * Math.exp(-124.1 * de);
        double e = 0.8258 + 0.0003413 * dhInletKJ;
        return a * b * c * d * e * 1e3;
    }

    /** Зубер, кипение в большом объёме: q'' = 0,131 h_fg ρ_g^½ [σ g (ρ_f − ρ_g)]^¼, Вт/м². */
    public static double chfZuber(SaturationState sat) {
        return 0.131 * sat.latentHeat() * Math.sqrt(sat.rhoG())
                * Math.pow(sat.sigma() * GRAVITY * (sat.rhoF() - sat.rhoG()), 0.25);
    }

    /**
     * Критический тепловой поток по иерархии §1.4.4, Вт/м²: Зубер при G ≤ 100, W-3 в его области.
     * Таблица Groeneveld 2006 ещё не подключена (нет ресурса {@code chf_lut_2006.bin}), поэтому вне
     * области W-3 аргументы зажимаются на её границу, при x_e &gt; 0,15 поток спадает линейно до нуля
     * при x_e = 1 (осушение), а при 100 &lt; G &lt; 1356 — линейная интерполяция между Зубером и W-3.
     */
    public static double chf(SaturationState sat, double g, double xe, double de, double dhInlet) {
        double pool = chfZuber(sat);
        double gAbs = Math.abs(g);
        if (gAbs <= POOL_MASS_FLUX) return pool;
        double pMPa = Math.max(W3_P_MIN, Math.min(W3_P_MAX, sat.pressure() * 1e-6));
        double xc = Math.max(-W3_X_MAX, Math.min(W3_X_MAX, xe));
        double dc = Math.max(W3_DE_MIN, Math.min(W3_DE_MAX, de));
        double dhKJ = Math.max(0, dhInlet) * 1e-3;
        double forced = chfW3(pMPa, Math.max(W3_G_MIN, Math.min(W3_G_MAX, gAbs)), xc, dc, dhKJ);
        if (gAbs < W3_G_MIN) forced = pool + (forced - pool) * (gAbs - POOL_MASS_FLUX) / (W3_G_MIN - POOL_MASS_FLUX);
        if (xe > W3_X_MAX) forced *= Math.max(0, (1 - xe) / (1 - W3_X_MAX));
        return forced;
    }

    // ---------------------------------------------------------------- закризисный теплообмен, §1.4.5

    /** Минимальная температура плёночного кипения (Groeneveld–Stewart), К; выше 9 МПа — линейно к T_c. */
    public static double tMinFilmBoiling(double pMPa) {
        double pc = CRITICAL_PRESSURE * 1e-6;
        if (pMPa >= pc) return CRITICAL_TEMPERATURE;
        if (pMPa <= 9) return 557.85 + 44.1 * pMPa - 3.72 * pMPa * pMPa + 273.15;
        double t9 = tMinFilmBoiling(9);
        return t9 + (CRITICAL_TEMPERATURE - t9) * (pMPa - 9) / (pc - 9);
    }

    /** Длина волны Тейлора 2π [σ/(g Δρ)]^½, м. */
    public static double taylorWavelength(SaturationState sat) {
        return 2 * Math.PI * Math.sqrt(sat.sigma() / (GRAVITY * (sat.rhoF() - sat.rhoG())));
    }

    /**
     * Плёночное кипение по Бромли на вертикальной поверхности, Вт/(м²·К), свойства пара — на
     * насыщении. Характерная длина не больше длины волны Тейлора (модифицированный Бромли, как в
     * TRACE): у высокой стенки плёнка волнистая, и h перестаёт падать как L^−¼.
     */
    public static double bromley(SaturationState sat, double tWall, double length) {
        double dT = tWall - sat.tSat();
        if (dT <= 0) return 0;
        double l = Math.min(length, taylorWavelength(sat));
        double hfg = sat.latentHeat() + 0.4 * sat.cpG() * dT;
        double kg = sat.kG();
        return 0.943 * Math.pow(kg * kg * kg * sat.rhoG() * (sat.rhoF() - sat.rhoG()) * GRAVITY * hfg
                / (sat.muG() * l * dT), 0.25);
    }

    /** Дугалл–Розеноу, плёночное кипение при вынужденном течении, Вт/(м²·К). */
    public static double dougallRohsenow(double g, double x, double dh, SaturationState sat) {
        double xc = Math.max(0, Math.min(1, x));
        double re = Math.abs(g) * dh / sat.muG() * (xc + sat.rhoG() / sat.rhoF() * (1 - xc));
        double pr = sat.muG() * sat.cpG() / sat.kG();
        return 0.023 * Math.pow(re, 0.8) * Math.pow(pr, 0.4) * sat.kG() / dh;
    }

    /** Излучение стенки на жидкость/пар: ε σ (T_w⁴ − T⁴), Вт/м². */
    public static double radiation(double tWall, double tSink, double emissivity) {
        double w2 = tWall * tWall, s2 = tSink * tSink;
        return emissivity * STEFAN_BOLTZMANN * (w2 * w2 - s2 * s2);
    }

    // ---------------------------------------------------------------- конденсация, §1.4.7

    /**
     * Плёночная конденсация Нуссельта, Вт/(м²·К): снаружи горизонтальных труб диаметра {@code size}
     * (0,725) или на вертикальной стенке высотой {@code size} (0,943).
     */
    public static double nusseltCondensation(SaturationState sat, double tWall, double size, boolean horizontalTube) {
        double dT = sat.tSat() - tWall;
        if (dT <= 0) return 0;
        double kf = sat.kF();
        double grp = sat.rhoF() * (sat.rhoF() - sat.rhoG()) * GRAVITY * sat.latentHeat() * kf * kf * kf
                / (sat.muF() * size * dT);
        return (horizontalTube ? 0.725 : 0.943) * Math.pow(grp, 0.25);
    }

    /** Множитель деградации конденсации по доле неконденсирующихся газов (Колберн–Хоуген, грубо). */
    public static double noncondensableFactor(double moleFraction, double coefficient) {
        double y = Math.max(0, Math.min(0.5, moleFraction));
        return 1 - coefficient * Math.pow(y, 0.3);
    }

    // ---------------------------------------------------------------- топливо, §1.4.6

    /**
     * Теплопроводность UO₂ (Fink 2000, поправка NFI на выгорание, пористость по Максвеллу–Ойкену),
     * Вт/(м·К). burnup — МВт·сут/кгТМ, porosity — доля.
     */
    public static double uo2Conductivity(double t, double burnup, double porosity) {
        double tt = t / 1000;
        double k95 = 1 / (0.0754 + 0.1769 * tt + 0.0361 * tt * tt + 1.87e-3 * burnup)
                + 6400 / Math.pow(tt, 2.5) * Math.exp(-16.35 / tt);
        return k95 * ((1 - porosity) / (1 + 2 * porosity)) / (0.95 / 1.10);
    }

    /** Теплоёмкость UO₂ (Fink 2000), Дж/(кг·К). */
    public static double uo2HeatCapacity(double t) {
        double tt = t / 1000;
        double molar = 52.1743 + 87.951 * tt - 84.2411 * tt * tt + 31.542 * tt * tt * tt
                - 2.6334 * tt * tt * tt * tt - 0.71391 / (tt * tt);
        return molar / 0.27003;
    }

    /** Теплопроводность циркалоя (MATPRO), Вт/(м·К). */
    public static double zircaloyConductivity(double t) {
        return 7.51 + 2.09e-2 * t - 1.45e-5 * t * t + 7.67e-9 * t * t * t;
    }

    /**
     * Теплоёмкость циркалоя, Дж/(кг·К): 281 (300 К), 318 (600 К), 360 (1000 К) и размазанный
     * треугольный пик α→β на 1090–1250 К площадью 44 кДж/кг.
     */
    public static double zircaloyHeatCapacity(double t) {
        double base;
        if (t <= 300) base = 281;
        else if (t <= 600) base = 281 + (318 - 281) * (t - 300) / 300;
        else if (t <= 1000) base = 318 + (360 - 318) * (t - 600) / 400;
        else base = 360;
        double lo = 1090, hi = 1250, peak = 2 * 44e3 / (hi - lo), mid = 0.5 * (lo + hi);
        if (t > lo && t < hi) base += peak * (1 - Math.abs(t - mid) / (mid - lo));
        return base;
    }

    /** Теплопроводность газа в зазоре (MATPRO GTHCON), смесь — среднее геометрическое по мольным долям. */
    public static double gapGasConductivity(double t, double heFraction, double xeFraction, double krFraction) {
        double sum = heFraction + xeFraction + krFraction;
        double lnK = (heFraction * Math.log(2.639e-3 * Math.pow(t, 0.7085))
                + xeFraction * Math.log(4.351e-5 * Math.pow(t, 0.8616))
                + krFraction * Math.log(8.247e-5 * Math.pow(t, 0.8363))) / sum;
        return Math.exp(lnK);
    }

    /**
     * Проводимость зазора таблетка–оболочка, Вт/(м²·К): газ через δ + δ_jump и излучение.
     * Температуры — поверхности таблетки и внутренней поверхности оболочки.
     */
    public static double gapConductance(double tPellet, double tClad, double gapWidth, double jumpDistance,
            double heFraction, double xeFraction, double krFraction) {
        double k = gapGasConductivity(0.5 * (tPellet + tClad), heFraction, xeFraction, krFraction);
        double rad = STEFAN_BOLTZMANN * (tPellet * tPellet + tClad * tClad) * (tPellet + tClad)
                / (1 / PELLET_EMISSIVITY + 1 / CLAD_EMISSIVITY - 1);
        return k / (gapWidth + jumpDistance) + rad;
    }

    /** Порог газовыделения Витанцы, К; burnupUO2 — МВт·сут/кгUO₂. */
    public static double vitanzaThreshold(double burnupUO2) {
        if (burnupUO2 <= 0.005) return Double.POSITIVE_INFINITY;
        return 9800 / Math.log(burnupUO2 / 0.005) + 273.15;
    }
}
