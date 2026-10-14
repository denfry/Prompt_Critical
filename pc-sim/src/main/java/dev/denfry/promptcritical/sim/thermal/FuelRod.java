package dev.denfry.promptcritical.sim.thermal;

import static dev.denfry.promptcritical.sim.thermal.HeatTransferCorrelations.*;

import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Представительный ТВЭЛ (01 §1.2.3, §1.4.6): N_p колец таблетки равной площади, зазор,
 * два узла оболочки (внутренняя и наружная поверхность), наружная поверхность — к узлу
 * теплоносителя. Температуры колец — средние по кольцу; проводимость между кольцами
 * выведена из точного профиля при равномерном q''' (G = 4πkL·j для j-й границы), поэтому
 * при постоянном k схема точна в стационаре для любого N_p.
 *
 * <p>Выгорание — МВт·сут/кгТМ. Газовыделение — порог Витанцы, давление под оболочкой —
 * идеальный газ в свободном объёме {@value #FREE_VOLUME_FRACTION} объёма таблетки.
 */
public final class FuelRod extends ConductionStructure {
    /** Свободный объём (зазор + компенсационный объём) в долях объёма таблетки. */
    public static final double FREE_VOLUME_FRACTION = 0.1;
    /** Газообразные продукты деления Xe + Kr, моль на МВт·сут (≈ 0,29 атома на деление). */
    public static final double FISSION_GAS_PER_MWD = 1.3e-3;
    /** σ_ln множителя неопределённости CHF горячего ТВЭЛа (§1.4.4). */
    public static final double CRISIS_DNBR_SIGMA = 0.08;

    private static final double GAS_CONSTANT = 8.314462618, FILL_TEMPERATURE = 293.15;
    private static final double ALPHA_UO2 = 1.0e-5, ALPHA_ZR = 6.0e-6, MIN_GAP = 2e-6, SWELLING = 2.3e-4;
    private static final double XE_SHARE = 0.86;

    private final FuelRodSpec spec;
    private final RodRole role;
    private final int np;
    private final double ringMass, ringVolume, cladMassInner, cladMassOuter, heavyMetalMass, freeVolume, fillMoles;
    private final double[] kRing, melt;

    private double burnup, volumetricPower, fgr, pelletSurfaceConductance, hGap, gapWidth;

    /**
     * @param surface наружная поверхность оболочки; у горячего ТВЭЛа — с кратностью 0
     *                (в баланс узла не входит, §1.2.3)
     */
    public FuelRod(long id, FuelRodSpec spec, RodRole role, ConvectiveSurface surface, ThermalConfig config) {
        super(id, Geometry.FUEL_ROD, spec.pelletRings() + 2, config);
        this.spec = spec;
        this.role = Objects.requireNonNull(role, "role");
        np = spec.pelletRings();
        double rp = spec.pelletRadius(), ri = spec.cladInnerRadius(), ro = spec.cladOuterRadius(), l = spec.activeLength();
        double rm = 0.5 * (ri + ro);
        double pelletVolume = Math.PI * rp * rp * l;
        ringVolume = pelletVolume / np;
        ringMass = UO2_THEORETICAL_DENSITY * spec.theoreticalDensityFraction() * ringVolume;
        heavyMetalMass = ringMass * np * URANIUM_MASS_FRACTION;
        cladMassInner = ZIRCALOY_DENSITY * Math.PI * (rm * rm - ri * ri) * l;
        cladMassOuter = ZIRCALOY_DENSITY * Math.PI * (ro * ro - rm * rm) * l;
        freeVolume = FREE_VOLUME_FRACTION * pelletVolume;
        fillMoles = spec.fillGasPressure() * freeVolume / (GAS_CONSTANT * FILL_TEMPERATURE);
        kRing = new double[np];
        melt = new double[np];
        area[1] = 2 * Math.PI * ro * l;
        surfaces[1] = Objects.requireNonNull(surface, "surface");
    }

    /** ξ_rod горячего ТВЭЛа ТВС: логнормальный, медиана 1, σ_ln = 0,08; сид — хэш UUID ТВС (§1.4.4). */
    public static double sampleCrisisDnbr(long seed) {
        return Math.exp(CRISIS_DNBR_SIGMA * new SplittableRandom(seed).nextGaussian());
    }

    public RodRole role() {
        return role;
    }

    public FuelRodSpec spec() {
        return spec;
    }

    /** Выгорание, МВт·сут/кгТМ. */
    public void setBurnup(double mwdPerKgHM) {
        if (!(mwdPerKgHM >= 0)) throw new IllegalArgumentException("burnup");
        burnup = mwdPerKgHM;
    }

    public double burnup() {
        return burnup;
    }

    @Override
    public void setVolumetricPower(double wattsPerM3) {
        volumetricPower = wattsPerM3;
    }

    /** Линейная мощность q', Вт/м (q''' = q'/(π r_p²)). */
    public void setLinearPower(double wattsPerMeter) {
        volumetricPower = wattsPerMeter / (Math.PI * spec.pelletRadius() * spec.pelletRadius());
    }

    public double linearPower() {
        return volumetricPower * Math.PI * spec.pelletRadius() * spec.pelletRadius();
    }

    /** Доля освобождённых газообразных продуктов деления. */
    public double fissionGasRelease() {
        return fgr;
    }

    public double gapWidth() {
        return gapWidth;
    }

    @Override
    void updateProperties() {
        double l = spec.activeLength(), porosity = spec.porosity();
        for (int i = 0; i < np; i++) {
            kRing[i] = uo2Conductivity(t[i], burnup, porosity);
            cap[i] = ringMass * uo2HeatCapacity(t[i]);
            source[i] = volumetricPower * ringVolume;
        }
        for (int j = 0; j < np - 1; j++) cond[j] = 4 * Math.PI * l * (j + 1) * 0.5 * (kRing[j] + kRing[j + 1]);
        double pelletSurface = pelletSurfaceTemperature();
        pelletSurfaceConductance = 8 * Math.PI * l * kRing[np - 1] * np;

        double rp = spec.pelletRadius(), ri = spec.cladInnerRadius(), ro = spec.cladOuterRadius();
        double tPellet = 0;
        for (int i = 0; i < np; i++) tPellet += t[i];
        tPellet /= np;
        double tClad = 0.5 * (t[np] + t[np + 1]);
        double gap0 = ri - rp;
        double thermal = rp * ALPHA_UO2 * (tPellet - 300) - ri * ALPHA_ZR * (tClad - 300);
        gapWidth = Math.max(MIN_GAP, gap0 * (1 - config.gapRelocationFraction()) - thermal - SWELLING * rp * burnup);
        double released = releasedMoles(), total = fillMoles + released;
        hGap = HeatTransferCorrelations.gapConductance(pelletSurface, t[np], gapWidth, config.gapJumpDistance(),
                fillMoles / total, XE_SHARE * released / total, (1 - XE_SHARE) * released / total);
        double gapConductanceW = hGap * 2 * Math.PI * rp * l;
        cond[np - 1] = 1 / (1 / pelletSurfaceConductance + 1 / gapConductanceW);
        cond[np] = 2 * Math.PI * l * zircaloyConductivity(tClad) / Math.log(ro / ri);
        cap[np] = cladMassInner * zircaloyHeatCapacity(t[np]);
        cap[np + 1] = cladMassOuter * zircaloyHeatCapacity(t[np + 1]);
        source[np] = source[np + 1] = 0;
    }

    /** Энтальпийный метод плавления: перегрев выше T_m уходит в долю расплава, без роста T. */
    @Override
    void afterSubstep() {
        double latent = ringMass * UO2_LATENT_HEAT;
        for (int i = 0; i < np; i++) {
            double excess = cap[i] * (t[i] - UO2_MELTING_POINT); // Дж относительно T_m
            if (excess > 0 && melt[i] < 1 || excess < 0 && melt[i] > 0) {
                double f = melt[i] + excess / latent;
                if (f > 1) {
                    melt[i] = 1;
                    t[i] = UO2_MELTING_POINT + (f - 1) * latent / cap[i];
                } else if (f < 0) {
                    melt[i] = 0;
                    t[i] = UO2_MELTING_POINT + f * latent / cap[i];
                } else {
                    melt[i] = f;
                    t[i] = UO2_MELTING_POINT;
                }
            }
        }
    }

    /** Газовыделение по порогу Витанцы; доля не убывает. */
    @Override
    void afterStep(double dt) {
        double tc = centerlineTemperature(), tv = vitanzaThreshold(burnup * URANIUM_MASS_FRACTION);
        if (tc > tv) fgr = Math.max(fgr, Math.min(0.3, 0.01 + config.fgrSlopePer100K() * (tc - tv) / 100));
    }

    private double releasedMoles() {
        return fgr * FISSION_GAS_PER_MWD * burnup * heavyMetalMass;
    }

    /** Центр таблетки: параболический профиль внутри первого кольца, T(0) − T̄₀ = Q₀₁/(8πk₀L). */
    @Override
    public double centerlineTemperature() {
        return t[0] + cond[0] * (t[0] - t[1]) / (8 * Math.PI * kRing[0] * spec.activeLength());
    }

    /** Поверхность таблетки: T̄ последнего кольца минус перепад до поверхности (G = 8πkL·N_p). */
    public double pelletSurfaceTemperature() {
        if (pelletSurfaceConductance == 0) return t[np - 1];
        return t[np - 1] - cond[np - 1] * (t[np - 1] - t[np]) / pelletSurfaceConductance;
    }

    public double cladInnerTemperature() {
        return t[np];
    }

    @Override
    public double gapConductance() {
        return hGap;
    }

    @Override
    public double meltFraction() {
        double s = 0;
        for (double m : melt) s += m;
        return s / np;
    }

    @Override
    public double rodInternalPressure() {
        double tGas = 0.5 * (t[np] + t[np + 1]);
        return (fillMoles + releasedMoles()) * GAS_CONSTANT * tGas / freeVolume;
    }

    @Override
    public double dnbr() {
        return role == RodRole.HOT ? surfaces[1].dnbr() : Double.NaN;
    }

    /** ξ_rod для горячего ТВЭЛа (см. {@link #sampleCrisisDnbr}). */
    public void setCrisisDnbr(double xi) {
        surfaces[1].setCrisisDnbr(xi);
    }
}
