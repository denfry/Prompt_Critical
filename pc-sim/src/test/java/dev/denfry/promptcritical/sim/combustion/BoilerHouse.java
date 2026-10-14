package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.hydraulics.FlowLinkSpec;
import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.FluidNodeSpec;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicEventSink;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicsConfig;
import dev.denfry.promptcritical.sim.hydraulics.LinkKind;
import dev.denfry.promptcritical.sim.hydraulics.StateCarryOver;
import dev.denfry.promptcritical.sim.hydraulics.ValveModel;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.List;

/**
 * Котельная I0 так, как её соберёт биндинг (этап 6): котёл — газовая сторона; водяной объём — узел {@code SEPARATED}
 * по эквивалентной призме из {@link FireTubeBoilerSpec}; ПК на атмосферу; паропровод с дроссельной
 * заслонкой регулятора ({@code VALVE_CONTROL}, равнопроцентная, сечение по {@link SteamEngineUnit#throttleAreaM2}) в
 * золотниковую коробку ({@code HEM}, 0,1 м³), из которой пар берёт
 * машина; выхлоп в атмосферу; питательный насос с П-регулятором уровня.
 */
final class BoilerHouse {
    static final WaterProperties W = WaterProperties.tables();
    static final double DT = 0.05, ATM = 101_325, EXHAUST = 0.11e6;
    static final long DRUM = 1, CHEST = 2, AIR = 3, THROTTLE = 1, RELIEF = 2;
    static final double FEED_ENTHALPY = W.enthalpyPT(1.4e6, 293.15);

    final FireTubeBoilerSpec spec = FireTubeBoilerSpec.defaults();
    final FireTubeBoiler boiler;
    final SteamEngineUnit engine;
    final FluidNetwork net;
    final BoilerInputs in = new BoilerInputs();
    final BoilerOutputs out = new BoilerOutputs();
    double loadTorque, feedSetLevel = spec.normalLevelM(), feedKgS;
    boolean feedPump = true, engineValve = true;
    int reliefLifts;

    BoilerHouse(double pressure, double level, int cylinders, FlyballGovernor governor) {
        FuelSpec coal = CoalAssay.typical(0.37, 0.213, 0.06, 0.016, 10).toFuel();
        SaturationState s = W.saturationP(pressure);
        boiler = new FireTubeBoiler(spec, coal, s.tSat());
        engine = new SteamEngineUnit(
                SteamEngineUnit.Spec.reference(cylinders, cylinders * SteamEngineUnit.FLYWHEEL_INERTIA), governor);
        net = FluidNetwork.create(WaterPackage.of(CriticalFlowTable.Model.HEM), HydraulicsConfig.defaults());
        net.setEventSink(new HydraulicEventSink() {
            @Override
            public void reliefLift(long linkId, boolean open) {
                if (open) reliefLifts++;
            }
        });
        double throttle = engine.throttleAreaM2(W.saturationP(1.1e6).rhoG()), valve = Math.PI / 4 * 0.05 * 0.05;
        double height = spec.waterNodeHeightM(), area = spec.waterNodeAreaM2(), top = height - 0.05;
        net.rebuild(List.of(FluidNodeSpec.separated(DRUM, 0, height, area), FluidNodeSpec.hem(CHEST, 0.1, 2, 0.5),
                        FluidNodeSpec.boundary(AIR, 2)),
                List.of(link(THROTTLE, LinkKind.VALVE_CONTROL, DRUM, CHEST, 10 / throttle, throttle, 0, top, 2.25,
                                ValveModel.Spec.control(ValveModel.Characteristic.EQUAL_PERCENTAGE,
                                        SteamEngineUnit.THROTTLE_ZETA, 0)),
                        link(RELIEF, LinkKind.VALVE_RELIEF, DRUM, AIR, 2 / valve, valve, 1, top, 2,
                                ValveModel.Spec.relief(spec.reliefSetPressure(), 1))),
                new StateCarryOver() {
                    @Override
                    public double initialPressure(long id) {
                        return id == AIR ? ATM : pressure;
                    }

                    @Override
                    public double initialEnthalpy(long id) {
                        if (id == AIR) return W.enthalpyPT(ATM, 300);
                        if (id == CHEST) return s.hG();
                        double vl = level * area, mf = vl / s.vF(), mg = (height * area - vl) / s.vG();
                        return s.hF() + mg / (mf + mg) * (s.hG() - s.hF());
                    }
                });
        engine.shaft().setOmega(engine.nominalOmega());
        governor.settle(engine.nominalOmega(), engine.nominalOmega());
        loadTorque = 0.9 * engine.engine().meanIndicatedTorqueNm(1.1e6, EXHAUST, 0.25);
    }

    private static FlowLinkSpec link(long id, LinkKind kind, long from, long to, double inertia, double area,
            double zeta, double zFrom, double zTo, ValveModel.Spec valve) {
        return new FlowLinkSpec(id, kind, from, to, inertia, 0, Math.sqrt(4 * area / Math.PI), area, zeta, 0, zFrom,
                zTo, null, valve);
    }

    /** Котёл на режиме: слой под подачу, топка горит. */
    BoilerHouse firing(double feedCoalKgS) {
        boiler.chargeFuel(feedCoalKgS * spec.bedBurnoutTimeS());
        boiler.ignite();
        in.fuelFeedKgS = feedCoalKgS;
        return this;
    }

    FluidNode drum() {
        return net.node(DRUM);
    }

    FluidNode chest() {
        return net.node(CHEST);
    }

    void tick() {
        FluidNode drum = drum(), chest = chest();
        in.waterTempK = drum.temperature();
        in.waterPressurePa = drum.pressure();
        in.waterLevelM = drum.mixtureLevel();
        boiler.stepFast(DT, in, out);
        drum.addHeat(out.heatToWaterW);
        engine.step(DT, chest.pressure(), chest.density(), EXHAUST, loadTorque);
        chest.addSource(-engine.steamFlowKgS(), 0);
        net.link(THROTTLE).setValvePosition(engineValve ? engine.throttle() : 0);
        double demand = engine.steamFlowKgS() + 2 * (feedSetLevel - drum.collapsedLevel());
        feedKgS = feedPump ? Math.max(0, Math.min(6, demand)) : 0;
        drum.addSource(feedKgS, FEED_ENTHALPY);
        net.stepFast(DT);
    }

    void run(double seconds) {
        for (int k = 0, n = (int) Math.round(seconds / DT); k < n; k++) tick();
    }
}
