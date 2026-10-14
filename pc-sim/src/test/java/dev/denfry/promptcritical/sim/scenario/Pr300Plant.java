package dev.denfry.promptcritical.sim.scenario;

import dev.denfry.promptcritical.sim.hydraulics.FlowLink;
import dev.denfry.promptcritical.sim.hydraulics.FlowLinkSpec;
import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.FluidNodeSpec;
import dev.denfry.promptcritical.sim.hydraulics.HomologousCurve;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicsConfig;
import dev.denfry.promptcritical.sim.hydraulics.LinkKind;
import dev.denfry.promptcritical.sim.hydraulics.PumpModel;
import dev.denfry.promptcritical.sim.hydraulics.StateCarryOver;
import dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.protection.DnbrCalculator;
import dev.denfry.promptcritical.sim.protection.HotChannelModel;
import dev.denfry.promptcritical.sim.protection.PlantNominals;
import dev.denfry.promptcritical.sim.protection.ProtectionConfig;
import dev.denfry.promptcritical.sim.protection.ProtectionEventSink;
import dev.denfry.promptcritical.sim.protection.ProtectionInputs;
import dev.denfry.promptcritical.sim.protection.ProtectionSystem;
import dev.denfry.promptcritical.sim.protection.TripReason;
import dev.denfry.promptcritical.sim.thermal.ConvectiveSurface;
import dev.denfry.promptcritical.sim.thermal.FuelRod;
import dev.denfry.promptcritical.sim.thermal.FuelRodSpec;
import dev.denfry.promptcritical.sim.thermal.RodRole;
import dev.denfry.promptcritical.sim.thermal.ThermalConfig;
import dev.denfry.promptcritical.sim.thermal.TubeBundle;
import dev.denfry.promptcritical.sim.thermal.WallMaterial;
import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Горячий цикл одного ПР-300 (01 §1.14.1) из готовых моделей M1 — сценарий бюджета 01 §1.12.5 для
 * одного реактора ({@code ReactorFastBench.referenceCore}, 05 §5.7.6) и тестов T-1.12. Сборки зоны
 * {@code ReactorCore} ещё нет, поэтому связь моделей — здесь: кинетика с доплером и плотностью
 * теплоносителя → ТВЭЛы → гидросеть с ГЦН и КД → ПГ на межтрубье с постоянным давлением →
 * DNBR (10 Гц) → {@link ProtectionSystem}.
 */
public final class Pr300Plant {
    public static final double DT = 0.05, NOMINAL_POWER = 300e6;
    static final WaterProperties W = WaterProperties.tables();
    static final double P = 15.5e6, MDOT = 1778, LOOP = MDOT / 2, RHO = 745, G = 9.80665;
    static final double CORE_AREA = 1.137, LOOP_AREA = 0.3, SG_FLOW_AREA = 1650 * Math.PI * 6.5e-3 * 6.5e-3;
    static final int CELLS = 5, RODS_PER_CELL = 6000;
    static final double ALPHA_DOPPLER = -3.8e-5, ALPHA_COOLANT = -40e-5, SCRAM_WORTH = -0.08, SCRAM_TIME = 0.95;
    static final long LOWER = 1, CORE = 2, HOT_A = 3, HOT_B = 4, SG_A = 5, SG_B = 6, COLD_A = 7, COLD_B = 8, PRZ = 9;
    static final long CORE_LINK = 10, PUMP_A = 17, PUMP_B = 18;

    public final FluidNetwork primary;
    private final FluidNode lower, core, hotA, hotB, prz;
    private final FlowLink pumpA, pumpB;
    private final FuelRod[] rods = new FuelRod[2 * CELLS];
    private final TubeBundle[] sg = new TubeBundle[2];
    public final PointKinetics kinetics;
    public final ProtectionSystem protection;
    private final DnbrCalculator dnbr;
    private final Inputs inputs = new Inputs();
    private long tick;
    private double fuelRef, coolantRef, rodReactivity, lastDnbr = Double.POSITIVE_INFINITY;
    private boolean feedback;

    public Pr300Plant() {
        primary = FluidNetwork.create(WaterPackage.of(CriticalFlowTable.Model.HEM), HydraulicsConfig.defaults());
        double hCold = W.enthalpyPT(P, 563.15), hHot = W.enthalpyPT(P, 593.15);
        PumpModel.Spec pump = new PumpModel.Spec(157, LOOP / RHO, 0.40e6 / (RHO * G), 617e3 / 157, RHO, 300,
                HomologousCurve.DEFAULT_ID);
        double zCore = zeta(CORE_AREA, 0.15e6, MDOT), zLoop = zeta(LOOP_AREA, 0.0625e6, LOOP);
        primary.rebuild(List.of(
                        FluidNodeSpec.hem(LOWER, 4, -1, 1),
                        FluidNodeSpec.hem(CORE, 6, 0, 2),
                        FluidNodeSpec.hem(HOT_A, 1.5, 2, 1), FluidNodeSpec.hem(HOT_B, 1.5, 2, 1),
                        FluidNodeSpec.hem(SG_A, 2.65, 6, 2), FluidNodeSpec.hem(SG_B, 2.65, 6, 2),
                        FluidNodeSpec.hem(COLD_A, 2.25, 2, 1), FluidNodeSpec.hem(COLD_B, 2.25, 2, 1),
                        FluidNodeSpec.separated(PRZ, 4, 4, 1.5)),
                List.of(pipe(CORE_LINK, LOWER, CORE, 2, CORE_AREA, zCore, -0.2, 0.2),
                        pipe(11, CORE, HOT_A, 10, LOOP_AREA, zLoop, 2, 2), pipe(12, CORE, HOT_B, 10, LOOP_AREA, zLoop, 2, 2),
                        pipe(13, HOT_A, SG_A, 20, LOOP_AREA, zLoop, 2.5, 7), pipe(14, HOT_B, SG_B, 20, LOOP_AREA, zLoop, 2.5, 7),
                        pipe(15, SG_A, COLD_A, 20, LOOP_AREA, zLoop, 7, 2.5), pipe(16, SG_B, COLD_B, 20, LOOP_AREA, zLoop, 7, 2.5),
                        link(PUMP_A, LinkKind.PUMP, COLD_A, LOWER, 10, LOOP_AREA, zLoop, 2, -0.5, pump),
                        link(PUMP_B, LinkKind.PUMP, COLD_B, LOWER, 10, LOOP_AREA, zLoop, 2, -0.5, pump),
                        pipe(19, HOT_A, PRZ, 200, 0.05, 2, 2.5, 4)),
                new StateCarryOver() {
                    @Override
                    public double initialPressure(long id) {
                        return P;
                    }

                    @Override
                    public double initialEnthalpy(long id) {
                        if (id == PRZ) return liquidVolumeEnthalpy(6.0, 3.5);
                        return id == CORE || id == HOT_A || id == HOT_B ? hHot : hCold;
                    }

                    @Override
                    public double initialMassFlow(long id) {
                        return id == 19 ? 0 : id == CORE_LINK ? MDOT : LOOP;
                    }

                    @Override
                    public double initialPumpSpeed(long id) {
                        return 157;
                    }
                });
        primary.link(PUMP_A).pump().setDrivingTorque(617e3 / 157);
        primary.link(PUMP_B).pump().setDrivingTorque(617e3 / 157);
        lower = primary.node(LOWER);
        core = primary.node(CORE);
        hotA = primary.node(HOT_A);
        hotB = primary.node(HOT_B);
        prz = primary.node(PRZ);
        pumpA = primary.link(PUMP_A);
        pumpB = primary.link(PUMP_B);

        ThermalConfig tc = ThermalConfig.defaults();
        for (int c = 0; c < CELLS; c++) {
            for (int role = 0; role < 2; role++) {
                ConvectiveSurface s = ConvectiveSurface.builder(core, 0.0106, 1.0).flow(primary.link(CORE_LINK), CORE_AREA)
                        .inlet(lower).multiplicity(role == 0 ? RODS_PER_CELL : 0).build();
                rods[2 * c + role] = new FuelRod(100 + 2 * c + role, FuelRodSpec.pr300(4),
                        role == 0 ? RodRole.AVERAGE : RodRole.HOT, s, tc);
                rods[2 * c + role].setUniformTemperature(600 + 300 * role);
            }
        }
        SaturatedPool secondary = new SaturatedPool(W, 6.4e6);
        for (int k = 0; k < 2; k++) {
            FluidNode node = primary.node(k == 0 ? SG_A : SG_B), inlet = k == 0 ? hotA : hotB;
            List<ConvectiveSurface> in = new ArrayList<>(), out = new ArrayList<>();
            for (int seg = 0; seg < 3; seg++) {
                in.add(ConvectiveSurface.builder(node, 0.013, 10.15 / 3).flow(primary.link(13 + k), SG_FLOW_AREA)
                        .inlet(inlet).build());
                out.add(ConvectiveSurface.builder(secondary, 0.016, 10.15 / 3).poolBoiling().build());
            }
            sg[k] = TubeBundle.of(200 + k, 6.5e-3, 8e-3, 10.15, 1650, 3, WallMaterial.STAINLESS_STEEL, in, out, tc);
            sg[k].setUniformTemperature(560);
        }

        kinetics = new PointKinetics(DelayedNeutronData.u235(0.0068), 2e-5, 1.0);
        dnbr = new DnbrCalculator(new HotChannelModel(NOMINAL_POWER, MDOT, CORE_AREA, 0.0106, 350e3, 2.0 / 1.55, 1.55, 8),
                ProtectionConfig.defaults(), W);
        protection = ProtectionSystem.pressurizedWater(ProtectionConfig.defaults(),
                PlantNominals.of(W, P, 593.15, 17.2e6, 12.5e6), dnbr, ProtectionEventSink.NONE);

        // прогрев на 100 % без обратных связей: температуры приходят к балансу с ПГ, затем они — опорные
        for (int k = 0; k < 1200; k++) physics();
        fuelRef = meanFuelTemperature();
        coolantRef = core.temperature();
        feedback = true;
        // пуск: на 15 % мощности оператор снял пусковую уставку HIGH_FLUX (§1.11.3)
        inputs.indicatedPower = 0.15;
        protection.step(DT, inputs, -1);
        if (!protection.setLowFluxSetpointBlocked(true)) throw new IllegalStateException("low flux setpoint");
        inputs.indicatedPower = Double.NaN;
    }

    /** Один FAST-тик: кинетика → ТВЭЛы и ПГ → гидросеть → DNBR (10 Гц) → защита. */
    public void tick() {
        physics();
        if (tick % 2 == 0) {
            double flow = (pumpA.massFlow() + pumpB.massFlow()) / MDOT;
            lastDnbr = dnbr.calculate(prz.pressure(), lower.temperature(), flow, kinetics.power(), Double.NaN);
        }
        protection.step(DT, inputs, tick);
        tick++;
    }

    private void physics() {
        if (feedback) {
            if (protection.rodsReleased()) rodReactivity = Math.max(SCRAM_WORTH, rodReactivity + SCRAM_WORTH * DT / SCRAM_TIME);
            double rho = rodReactivity + ALPHA_DOPPLER * (meanFuelTemperature() - fuelRef)
                    + ALPHA_COOLANT * (core.temperature() - coolantRef);
            kinetics.step(DT, rho, 0);
        }
        double linear = kinetics.power() * NOMINAL_POWER / (CELLS * RODS_PER_CELL);
        for (int c = 0; c < CELLS; c++) {
            rods[2 * c].setLinearPower(linear);
            rods[2 * c + 1].setLinearPower(2.0 * linear); // F_q = 2,0
        }
        for (FuelRod r : rods) r.step(DT);
        for (TubeBundle b : sg) b.step(DT);
        primary.stepFast(DT);
    }

    private double meanFuelTemperature() {
        double s = 0;
        for (int c = 0; c < CELLS; c++) s += rods[2 * c].centerlineTemperature();
        return s / CELLS;
    }

    public double power() {
        return kinetics.power();
    }

    public double pressurizerPressure() {
        return prz.pressure();
    }

    public double hotLegTemperature() {
        return hotA.temperature();
    }

    public double lastDnbr() {
        return lastDnbr;
    }

    public double sgHeatRate() {
        double q = 0;
        for (TubeBundle b : sg) q += b.heatRate(1);
        return q;
    }

    /** Состояние для сравнения прогонов (T-1.12-04). */
    public int snapshot(double[] out, int at) {
        out[at++] = kinetics.power();
        for (long id = LOWER; id <= PRZ; id++) {
            FluidNode n = primary.node(id);
            out[at++] = n.pressure();
            out[at++] = n.internalEnergy();
        }
        for (long id = CORE_LINK; id <= 19; id++) out[at++] = primary.link(id).massFlow();
        for (FuelRod r : rods) out[at++] = r.centerlineTemperature();
        for (TubeBundle b : sg) out[at++] = b.heatRate(1);
        out[at++] = lastDnbr;
        out[at++] = protection.mode().ordinal();
        return at;
    }

    public static final int SNAPSHOT_SIZE = 1 + 18 + 10 + 2 * CELLS + 2 + 2;

    private static double zeta(double area, double dp, double mdot) {
        return 2 * RHO * area * area * dp / (mdot * mdot);
    }

    private static double liquidVolumeEnthalpy(double volume, double vLiquid) {
        SaturationState s = W.saturationP(P);
        double mf = vLiquid / s.vF(), mg = (volume - vLiquid) / s.vG();
        return s.hF() + mg / (mf + mg) * (s.hG() - s.hF());
    }

    private static FlowLinkSpec link(long id, LinkKind kind, long from, long to, double inertia, double area,
            double zetaSum, double zFrom, double zTo, PumpModel.Spec pump) {
        return new FlowLinkSpec(id, kind, from, to, inertia, 0, Math.sqrt(4 * area / Math.PI), area, zetaSum, 0, zFrom,
                zTo, pump, null);
    }

    private static FlowLinkSpec pipe(long id, long from, long to, double inertia, double area, double zetaSum,
            double zFrom, double zTo) {
        return link(id, LinkKind.PIPE, from, to, inertia, area, zetaSum, zFrom, zTo, null);
    }

    /** Измерения для защиты — без датчиков 03b: показания равны состоянию модели. */
    private final class Inputs implements ProtectionInputs {
        double indicatedPower = Double.NaN;

        private double power() {
            return Double.isNaN(indicatedPower) ? kinetics.power() : indicatedPower;
        }

        @Override
        public int channelCount(TripReason r) {
            return switch (r) {
                case HIGH_FLUX, SHORT_PERIOD, HIGH_PRIMARY_PRESSURE, LOW_PRIMARY_PRESSURE, HIGH_COOLANT_TEMP,
                        HIGH_CONTAINMENT_PRESSURE, SEISMIC -> 3;
                case LOW_COOLANT_FLOW, LOW_SG_LEVEL, LOSS_OF_DRIVE_POWER -> 6;
                case TURBINE_TRIP -> 2;
                default -> 0;
            };
        }

        @Override
        public int groupCount(TripReason r) {
            return r == TripReason.LOW_COOLANT_FLOW || r == TripReason.LOW_SG_LEVEL || r == TripReason.LOSS_OF_DRIVE_POWER
                    ? 2 : 1;
        }

        @Override
        public double channelValue(TripReason r, int ch) {
            return switch (r) {
                case HIGH_FLUX -> power();
                case SHORT_PERIOD -> kinetics.periodInstant();
                case HIGH_PRIMARY_PRESSURE, LOW_PRIMARY_PRESSURE -> prz.pressure();
                case LOW_COOLANT_FLOW -> (ch < 3 ? pumpA : pumpB).massFlow() / LOOP;
                case HIGH_COOLANT_TEMP -> Math.max(hotA.temperature(), hotB.temperature());
                case LOW_SG_LEVEL -> 0.5;
                case HIGH_CONTAINMENT_PRESSURE -> 1.0e5;
                case LOSS_OF_DRIVE_POWER -> 1.0;
                default -> 0;
            };
        }

        @Override
        public double measuredPowerFrac() {
            return power();
        }

        @Override
        public double measuredPeriod() {
            return kinetics.periodInstant();
        }

        @Override
        public boolean luaTripRequested() {
            return false;
        }

        @Override
        public boolean manualTrip() {
            return false;
        }

        @Override
        public boolean driveTorqueLost() {
            return false;
        }
    }
}
