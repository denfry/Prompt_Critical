package dev.denfry.promptcritical.sim.thermal;

import static dev.denfry.promptcritical.sim.thermal.HeatTransferCorrelations.*;

import dev.denfry.promptcritical.sim.hydraulics.FlowLink;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.NodeKind;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.Objects;

/**
 * Поверхность тепловой структуры, омываемая водяным узлом (01 §1.2.3, §1.4.1): выбор режима
 * по кривой кипения, гистерезис DNB, DNBR и полунеявная связь с узлом — свойства и T_fluid
 * берутся со старого шага, тепло уходит в узел через {@link FluidNode#addHeat} на текущий тик.
 *
 * <p>Если задан входной узел сегмента ({@link Builder#inlet}) и связь направлена от него к узлу,
 * температура жидкости — среднее входа и выхода сегмента: 3-сегментная модель ПГ тогда
 * воспроизводит ε-NTU с ошибкой &lt; 1 % (в узле-смесителе с T_out ошибка 9 %).
 *
 * <p>Тепловой поток положителен из стенки в жидкость.
 */
public final class ConvectiveSurface {
    /** Узел осушен: теплоотдача к пару (§1.4.1). */
    static final double VOIDED = 0.999;

    private final WaterProperties water;
    private final ThermalConfig config;
    private final FluidNode node, inlet;
    private final FlowLink link;
    private final double flowArea, hydraulicDiameter, heatedLength, bundleFactor, multiplicity, fixedCoefficient;
    private final boolean poolBoiling, horizontalTubes;

    private double massFluxOverride = Double.NaN, crisisDnbr = 1, noncondensable;

    private final ThermoState bulk = new ThermoState(), scratch = new ThermoState();
    private final double[] satBuf = new double[17];
    private final SaturationState satState = new SaturationState();
    private SaturationState sat; // null выше критического давления
    private double massFlux, tBulk, drift, beta, qChf = Double.POSITIVE_INFINITY, tMin, capacity, energy;

    private HeatTransferMode mode = HeatTransferMode.SINGLE_PHASE_LIQUID, evalMode = mode;
    private boolean dnbLatched, dnbOccurred;
    private double tChf = Double.NaN, qAtChf, dnbr = Double.POSITIVE_INFINITY, flux;

    private ConvectiveSurface(Builder b) {
        water = b.water;
        config = b.config;
        node = b.node;
        inlet = b.inlet;
        link = b.link;
        flowArea = b.flowArea;
        hydraulicDiameter = b.hydraulicDiameter;
        heatedLength = b.heatedLength;
        bundleFactor = b.bundleFactor;
        multiplicity = b.multiplicity;
        fixedCoefficient = b.fixedCoefficient;
        poolBoiling = b.poolBoiling;
        horizontalTubes = b.horizontalTubes;
    }

    /**
     * @param hydraulicDiameter D_h канала (для конденсации на горизонтальных трубах — наружный диаметр)
     * @param heatedLength      обогреваемая высота, м (Бромли, вертикальная конденсация)
     */
    public static Builder builder(FluidNode node, double hydraulicDiameter, double heatedLength) {
        return new Builder(node, hydraulicDiameter, heatedLength);
    }

    public FluidNode node() {
        return node;
    }

    /** G, кг/(м²·с), заданная вручную; NaN — брать из связи. */
    public void setMassFlux(double g) {
        massFluxOverride = g;
    }

    /** ξ_rod: кризис наступает при DNBR ≤ ξ (§1.4.4). */
    public void setCrisisDnbr(double xi) {
        if (!(xi > 0)) throw new IllegalArgumentException("xi");
        crisisDnbr = xi;
    }

    /** Мольная доля неконденсирующихся газов в паре (конденсатор, §1.4.7). */
    public void setNoncondensableFraction(double y) {
        noncondensable = y;
    }

    public HeatTransferMode mode() {
        return mode;
    }

    /** Средний за шаг тепловой поток, Вт/м². */
    public double heatFlux() {
        return flux;
    }

    public double dnbr() {
        return dnbr;
    }

    public double criticalHeatFlux() {
        return qChf;
    }

    public double massFlux() {
        return massFlux;
    }

    public double fluidTemperature() {
        return tBulk;
    }

    /** Флаг DNB взведён: поверхность в переходном или плёночном режиме до повторного смачивания. */
    public boolean dnbLatched() {
        return dnbLatched;
    }

    /** Кризис был хотя бы раз (последствия — 04c). */
    public boolean dnbOccurred() {
        return dnbOccurred;
    }

    // ---------------------------------------------------------------- шаг структуры

    /** Состояние жидкости на старом шаге; вызывается в начале шага структуры. */
    void prepare() {
        drift = 0;
        energy = 0;
        tBulk = node.temperature();
        massFlux = !Double.isNaN(massFluxOverride) ? Math.abs(massFluxOverride)
                : link == null ? 0 : Math.abs(link.massFlow()) / flowArea;
        if (inlet != null && link != null && link.massFlow() > 0) tBulk = 0.5 * (tBulk + inlet.temperature());
        double p = node.pressure(), h = node.enthalpy();
        water.stateFromPressureEnthalpy(p, h, bulk);
        capacity = node.kind() == NodeKind.BOUNDARY || multiplicity == 0 || bulk.has(ThermoState.TWO_PHASE)
                ? Double.POSITIVE_INFINITY : node.mass() * bulk.cp / multiplicity;
        if (fixedCoefficient > 0) return;
        if (p < CRITICAL_PRESSURE * 0.999) {
            water.saturationP(p, satBuf);
            sat = satState.set(p, satBuf);
            tMin = tMinFilmBoiling(p * 1e-6);
            double hIn = inlet != null ? inlet.enthalpy() : h;
            qChf = chf(sat, massFlux, bulk.quality, hydraulicDiameter, sat.hF() - hIn);
        } else {
            sat = null;
            qChf = Double.POSITIVE_INFINITY;
        }
        beta = 0;
        if (sat == null || bulk.quality < 0) {
            water.stateFromPressureEnthalpy(p, water.enthalpyPT(p, bulk.temperature - 1), scratch);
            beta = Math.max(0, (scratch.density - bulk.density) / bulk.density);
        }
    }

    /** Теплоёмкость узла на одну структуру, Дж/К (бесконечна у границы и двухфазного узла). */
    double capacity() {
        return capacity;
    }

    /** q''(T_w), Вт/м², на состоянии жидкости шага; запоминает режим вычисления. */
    double flux(double tw) {
        double tb = tBulk + drift;
        if (fixedCoefficient > 0) {
            evalMode = HeatTransferMode.SINGLE_PHASE_LIQUID;
            return fixedCoefficient * (tw - tb);
        }
        if (sat == null) {
            evalMode = HeatTransferMode.SINGLE_PHASE_LIQUID;
            return singlePhase(tw, tb);
        }
        double ts = sat.tSat();
        if (tw < ts && bulk.quality > 0) {
            evalMode = HeatTransferMode.CONDENSATION;
            double size = horizontalTubes ? hydraulicDiameter : heatedLength;
            return -nusseltCondensation(sat, tw, size, horizontalTubes)
                    * noncondensableFactor(noncondensable, config.noncondensableDegradation()) * (ts - tw);
        }
        if (bulk.voidFraction >= VOIDED) {
            evalMode = HeatTransferMode.SINGLE_PHASE_VAPOR;
            return singlePhase(tw, tb) + (tw > tb ? radiation(tw, tb, config.filmRadEmissivity()) : 0);
        }
        if (tw < ts) {
            evalMode = HeatTransferMode.SINGLE_PHASE_LIQUID;
            return singlePhase(tw, tb);
        }
        if (tw >= tMin) {
            evalMode = HeatTransferMode.FILM_BOILING;
            return film(tw);
        }
        if (dnbLatched && tw > tChf) {
            evalMode = HeatTransferMode.TRANSITION_BOILING;
            double d = (tw - tMin) / (tChf - tMin);
            d *= d;
            return d * qAtChf + (1 - d) * film(tw);
        }
        evalMode = HeatTransferMode.NUCLEATE_BOILING;
        return nucleate(tw, tb);
    }

    /** Энергия, ушедшая в жидкость за подшаг, Дж; сдвигает локальную T жидкости для следующих подшагов. */
    void deposit(double joules) {
        energy += joules;
        drift += joules / capacity;
    }

    /** Конец шага: тепло в узел, режим, гистерезис DNB и DNBR при температуре стенки tw. */
    void finish(double tw, double dt, double area) {
        flux = area > 0 ? energy / (dt * area) : 0;
        if (multiplicity > 0 && node.kind() != NodeKind.BOUNDARY && energy != 0) {
            node.addHeat(energy * multiplicity / dt);
        }
        if (fixedCoefficient > 0 || sat == null) {
            mode = evalMode = HeatTransferMode.SINGLE_PHASE_LIQUID;
            dnbr = Double.POSITIVE_INFINITY;
            return;
        }
        drift = 0;
        flux(tw);
        mode = evalMode;
        dnbr = flux > 0 ? qChf / flux : Double.POSITIVE_INFINITY;
        if (!dnbLatched) {
            if (mode == HeatTransferMode.FILM_BOILING) {
                latchFromFilm();
            } else if ((mode == HeatTransferMode.NUCLEATE_BOILING || mode == HeatTransferMode.SINGLE_PHASE_LIQUID)
                    && flux > 0 && dnbr <= crisisDnbr && bulk.voidFraction < VOIDED) {
                dnbLatched = dnbOccurred = true;
                tChf = tw;
                qAtChf = flux;
            }
        } else if (tw <= tChf && tw < tMin) {
            dnbLatched = false; // повторное смачивание
        }
    }

    /** Поверхность оказалась в плёнке без пройденного CHF (горячая стенка залита водой): оценка T_CHF. */
    private void latchFromFilm() {
        dnbLatched = dnbOccurred = true;
        double target = qChf * crisisDnbr, ts = sat.tSat(), tb = tBulk;
        double lo = ts, hi = tMin;
        if (nucleate(hi, tb) <= target) {
            tChf = tMin;
            qAtChf = nucleate(hi, tb);
            return;
        }
        for (int k = 0; k < 50; k++) {
            double mid = 0.5 * (lo + hi);
            if (nucleate(mid, tb) < target) lo = mid;
            else hi = mid;
        }
        tChf = 0.5 * (lo + hi);
        qAtChf = target;
    }

    private double singlePhase(double tw, double tb) {
        double k = bulk.conductivity, mu = bulk.viscosity, cp = bulk.cp, d = hydraulicDiameter;
        double h = bundleFactor * dittusBoelter(massFlux * d / mu, mu * cp / k, k, d, tw > tb);
        h = Math.max(h, LAMINAR_NUSSELT * k / d);
        if (beta > 0) h = Math.max(h, naturalConvection(k, bulk.density, cp, mu, beta, tw - tb));
        return h * (tw - tb);
    }

    private double nucleate(double tw, double tb) {
        double ts = sat.tSat(), boil;
        if (poolBoiling) {
            boil = cooperPoolFlux(sat.pressure() / CRITICAL_PRESSURE, WATER_MOLAR_MASS, tw - ts);
        } else {
            double x = bulk.quality, d = hydraulicDiameter;
            double pSatWall = water.saturationPressure(Math.min(tw, CRITICAL_TEMPERATURE - 0.1));
            boil = chenMacro(massFlux, x, d, sat) * (tw - tb) + chenMicro(massFlux, x, d, tw, pSatWall, sat) * (tw - ts);
        }
        return Math.max(singlePhase(tw, tb), boil);
    }

    private double film(double tw) {
        double ts = sat.tSat();
        double h = bromley(sat, tw, heatedLength);
        if (massFlux > FORCED_FILM_MASS_FLUX) {
            h = Math.max(h, dougallRohsenow(massFlux, bulk.quality, hydraulicDiameter, sat));
        }
        return h * (tw - ts) + radiation(tw, ts, config.filmRadEmissivity());
    }

    public static final class Builder {
        private final FluidNode node;
        private final double hydraulicDiameter, heatedLength;
        private WaterProperties water = WaterProperties.tables();
        private ThermalConfig config = ThermalConfig.defaults();
        private FluidNode inlet;
        private FlowLink link;
        private double flowArea = 1, bundleFactor = 1, multiplicity = 1, fixedCoefficient;
        private boolean poolBoiling, horizontalTubes;

        private Builder(FluidNode node, double hydraulicDiameter, double heatedLength) {
            this.node = Objects.requireNonNull(node, "node");
            if (!(hydraulicDiameter > 0 && heatedLength > 0)) throw new IllegalArgumentException("geometry");
            this.hydraulicDiameter = hydraulicDiameter;
            this.heatedLength = heatedLength;
        }

        /** Расход канала: G = |ṁ|/flowArea. */
        public Builder flow(FlowLink link, double flowArea) {
            if (!(flowArea > 0)) throw new IllegalArgumentException("flowArea");
            this.link = Objects.requireNonNull(link, "link");
            this.flowArea = flowArea;
            return this;
        }

        /** Входной узел сегмента; связь {@link #flow} должна быть направлена от него к узлу поверхности. */
        public Builder inlet(FluidNode inlet) {
            this.inlet = Objects.requireNonNull(inlet, "inlet");
            return this;
        }

        /** Множитель к Nu однофазной конвекции (Вейсман для пучка ТВЭЛов). */
        public Builder bundleFactor(double c) {
            if (!(c > 0)) throw new IllegalArgumentException("bundleFactor");
            this.bundleFactor = c;
            return this;
        }

        /** Сколько одинаковых структур представляет эта (ТВЭЛы ячейки); 0 — тепло в узел не идёт (горячий ТВЭЛ). */
        public Builder multiplicity(double n) {
            if (!(n >= 0)) throw new IllegalArgumentException("multiplicity");
            this.multiplicity = n;
            return this;
        }

        /** Кипение в большом объёме по Куперу (межтрубье ПГ, бассейны). */
        public Builder poolBoiling() {
            this.poolBoiling = true;
            return this;
        }

        /** Конденсация снаружи горизонтальных труб (D = hydraulicDiameter). */
        public Builder horizontalTubes() {
            this.horizontalTubes = true;
            return this;
        }

        /** Заданный коэффициент теплоотдачи, Вт/(м²·К), без кривой кипения (теплопотери, тесты). */
        public Builder fixedCoefficient(double h) {
            if (!(h > 0)) throw new IllegalArgumentException("h");
            this.fixedCoefficient = h;
            return this;
        }

        public Builder water(WaterProperties water) {
            this.water = Objects.requireNonNull(water, "water");
            return this;
        }

        public Builder config(ThermalConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        public ConvectiveSurface build() {
            return new ConvectiveSurface(this);
        }
    }
}
