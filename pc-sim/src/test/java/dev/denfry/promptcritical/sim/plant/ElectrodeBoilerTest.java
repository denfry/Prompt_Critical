package dev.denfry.promptcritical.sim.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.thermal.ElectrodeBoiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Электродный котёл: мощность ∝ погружению электродов, уровень ходит с конечной скоростью, задание по частоте —
 * от 50,2 Гц до полной при 51 Гц; тепло на коллекторе греет воду котла и поднимает давление пара.
 */
class ElectrodeBoilerTest {
    private final WorkerPool pool = WorkerPool.direct();
    private SimulationWorld w;

    @AfterEach
    void close() {
        if (w != null) w.close();
        pool.close();
    }

    @Test
    void frequencyDemandStartsAtFiftyPointTwoAndIsFullAtFiftyOne() {
        assertThat(ElectrodeBoiler.frequencyDemand(50.0, 50)).isZero();
        assertThat(ElectrodeBoiler.frequencyDemand(50.2, 50)).isCloseTo(0, within(1e-9));
        assertThat(ElectrodeBoiler.frequencyDemand(50.6, 50)).isCloseTo(0.5, within(1e-9));
        assertThat(ElectrodeBoiler.frequencyDemand(51.5, 50)).isEqualTo(1);
    }

    @Test
    void levelRisesAtItsRateAndSetsThePower() {
        ElectrodeBoiler b = new ElectrodeBoiler(1e6);
        b.setMode(ElectrodeBoiler.Mode.MANUAL);
        b.setManualSetpoint(1);
        for (int i = 0; i < 30; i++) b.step(1, 50, 50, true);
        assertThat(b.level()).isCloseTo(0.5, within(1e-9)); // полный ход — минута
        assertThat(b.powerAtRatedVoltageW()).isCloseTo(0.5e6, within(1.0));
        b.step(1, 50, 50, false); // пар некуда отдать — электроды осушаются
        assertThat(b.demandFraction()).isZero();
        for (int i = 0; i < 60; i++) b.step(1, 50, 50, false);
        assertThat(b.level()).isCloseTo(0, within(1e-9));
    }

    @Test
    void electricHeatOnTheHeaderRaisesSteamPressure() {
        w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new SteamPlant(SteamPlantConfig.defaults()));
        SteamPlant plant = w.subsystem(SteamPlant.class);
        SimId heated = w.topology().addDevice("promptcritical:fire_tube_boiler", new GridPos(0, 64, 0));
        SimId cold = w.topology().addDevice("promptcritical:fire_tube_boiler", new GridPos(20, 64, 0));
        plant.addBoiler(heated, 288.15).setChimneyHeight(20);
        plant.addBoiler(cold, 288.15).setChimneyHeight(20);
        w.tick(ExternalInputs.NONE);
        double level = plant.boiler(heated).spec().normalLevelM();
        plant.presetWater(heated, 0.2e6, level);
        plant.presetWater(cold, 0.2e6, level);
        for (int i = 0; i < 20 * 600; i++) { // 1 МВт 10 минут — ≈ 600 МДж в воду котла
            assertThat(plant.setElectricHeatW(heated, 1e6)).isTrue();
            w.tick(ExternalInputs.NONE);
        }
        assertThat(plant.boiler(heated).pressurePa()).isGreaterThan(plant.boiler(cold).pressurePa() + 0.05e6);
        assertThat(plant.setElectricHeatW(SimId.NONE, 1)).isFalse();
    }
}
