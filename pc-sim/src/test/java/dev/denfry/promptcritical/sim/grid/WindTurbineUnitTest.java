package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.wind.WindTurbine;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ВЭУ Д-30 параллельно с сетью: генератор на валу Create 0,4 кВ держит частоту и напряжение шины, потребитель
 * берёт 150 кВт. Ветряк включается у синхронной скорости, отдаёт активную мощность при отрицательном скольжении и
 * берёт реактивную; выше номинального ветра шаг лопастей держит 100 кВт; потеря сети не даёт разноса.
 */
class WindTurbineUnitTest {
    private static final long BUS = 21, GEN_SIDE = 22;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private final List<GeneratorUnit> shafts = new ArrayList<>();
    private int x;

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        worlds.add(w);
        return w;
    }

    private static GridSubsystem grid(SimulationWorld w) {
        return w.subsystem(GridSubsystem.class);
    }

    private SimId device(SimulationWorld w, String type) {
        return w.topology().addDevice("promptcritical:" + type, new GridPos(x++, 64, 0));
    }

    private record Plant(SimulationWorld w, GeneratorUnit gen, SwitchUnit breaker, ConsumerUnit load, WindTurbineUnit wind) {
    }

    /** Генератор 0,4 кВ → автомат → шина с потребителем 150 кВт и ВЭУ. */
    private Plant plant(double windMs) {
        SimulationWorld w = world();
        GeneratorUnit g = grid(w).generator(device(w, "shaft_generator"));
        g.setNodes(GEN_SIDE);
        g.setExciterPresent(true);
        g.setFieldOn(true);
        shafts.add(g);
        SwitchUnit br = grid(w).switchUnit(device(w, "lv_switchboard"), SwitchUnit.Kind.LV_BREAKER);
        br.setNodes(GEN_SIDE, BUS);
        br.operate(true);
        ConsumerUnit c = grid(w).consumer(device(w, "grid_consumer"));
        c.setNodes(BUS);
        c.setDemandKw(150);
        WindTurbineUnit t = grid(w).windTurbine(device(w, "wind_turbine"));
        t.setNodes(BUS);
        t.setWind(windMs, 0, 1.225);
        t.setEnabled(true);
        return new Plant(w, g, br, c, t);
    }

    private void run(Plant p, double windMs, double seconds) {
        int n = (int) Math.round(seconds * 20);
        for (int i = 0; i < n; i++) {
            for (GeneratorUnit g : shafts) if (g.owner == grid(p.w)) g.shaft.sample(64, true, p.w.clock().tick());
            p.wind.setWind(windMs, 0, 1.225);
            p.w.tick(ExternalInputs.NONE);
            p.w.drainEvents(e -> { });
        }
    }

    @Test
    void moderateWindExportsActivePowerAtNegativeSlipAndDrawsReactive() {
        Plant p = plant(7);
        run(p, 7, 240);
        WindTurbineUnit t = p.wind;
        assertThat(t.turbine().state()).isEqualTo(WindTurbine.State.GENERATING);
        assertThat(t.contactorClosed()).isTrue();
        // ½ρAV³·C_P(λ ≈ 6,8) ≈ 66 кВт на колесе; минус мультипликатор и генератор
        assertThat(t.exportW()).isBetween(45e3, 65e3);
        assertThat(t.slip()).isNegative().isGreaterThan(-0.03);
        assertThat(t.reactiveVar()).isPositive();
        assertThat(t.generatorRpm()).isCloseTo(600, within(20.0));
        // генератор станции разгружен на мощность ветряка
        assertThat(p.gen.electricalPowerW()).isCloseTo(p.load.powerW() - t.exportW(), within(15e3));
        assertThat(t.energyKWh()).isPositive();
    }

    @Test
    void strongWindIsLimitedToRatedPowerByPitch() {
        Plant p = plant(14);
        run(p, 14, 240);
        WindTurbineUnit t = p.wind;
        assertThat(t.turbine().state()).isEqualTo(WindTurbine.State.GENERATING);
        assertThat(t.exportW()).isCloseTo(WindTurbineUnit.SPEC.ratedPowerW(), within(8e3));
        assertThat(t.turbine().pitchDeg()).isGreaterThan(3);
        assertThat(t.turbine().thrustN()).isLessThan(WindTurbineUnit.SPEC.thrustLimitN());
    }

    @Test
    void aGustAboveTheCutOutStopsTheLoadedRotorWithoutTrippingProtection() {
        Plant p = plant(14);
        run(p, 14, 240);
        // экстремальный рабочий порыв нарастает за секунды (IEC 61400-1, EOG: 10,5 с на весь порыв)
        for (int k = 1; k <= 100; k++) run(p, 14 + 12 * k / 100.0, 0.05);
        assertThat(p.wind.turbine().state()).as("fault %s", p.wind.turbine().fault()).isEqualTo(WindTurbine.State.STORM);
        double peak = 0;
        for (int i = 0; i < 120 * 20; i++) {
            run(p, 26, 0.05);
            peak = Math.max(peak, p.wind.rotorRpm());
        }
        // генератор держит колесо у синхронной скорости, пока лопасти уходят во флюгер
        assertThat(peak).isLessThan(WindTurbineUnit.SPEC.overspeed() * 30);
        assertThat(p.wind.turbine().fault()).isEqualTo(WindTurbine.Fault.NONE);
        assertThat(p.wind.contactorClosed()).isFalse();
        assertThat(p.wind.turbine().pitchDeg()).isEqualTo(90);
        assertThat(p.wind.rotorRpm()).isLessThan(1);
    }

    @Test
    void calmKeepsTheGeneratorOffTheGrid() {
        Plant p = plant(3);
        run(p, 3, 120);
        assertThat(p.wind.contactorClosed()).isFalse();
        assertThat(p.wind.exportW()).isZero();
        assertThat(p.wind.turbine().state()).isEqualTo(WindTurbine.State.PARKED);
    }

    @Test
    void lossOfTheGridFeathersTheRotorWithoutRunaway() {
        Plant p = plant(12);
        run(p, 12, 240);
        assertThat(p.wind.exportW()).isGreaterThan(80e3);
        p.breaker.operate(false); // шина без синхронной машины: асинхронный генератор не возбуждается
        double peak = 0;
        for (int i = 0; i < 60 * 20; i++) {
            run(p, 12, 0.05);
            peak = Math.max(peak, p.wind.rotorRpm());
        }
        assertThat(peak).isLessThan(WindTurbineUnit.SPEC.overspeed() * 30);
        assertThat(p.wind.contactorClosed()).isFalse();
        assertThat(p.wind.turbine().pitchDeg()).isEqualTo(90);
        assertThat(p.wind.turbine().state()).isEqualTo(WindTurbine.State.PARKED);
        assertThat(p.wind.rotorRpm()).isLessThan(1);
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        Plant p = plant(9);
        run(p, 9, 120);
        MutableStateTree saved = p.w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(p.w.stateHash());
        WindTurbineUnit t = (WindTurbineUnit) grid(c).unit(p.wind.id());
        assertThat(t.turbine().state()).isEqualTo(p.wind.turbine().state());
        assertThat(t.rotorRpm()).isCloseTo(p.wind.rotorRpm(), within(1e-9));
        assertThat(t.energyKWh()).isCloseTo(p.wind.energyKWh(), within(1e-9));
    }
}
