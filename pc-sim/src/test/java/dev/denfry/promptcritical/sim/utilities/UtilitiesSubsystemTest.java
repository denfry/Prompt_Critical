package dev.denfry.promptcritical.sim.utilities;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ВРУ в мире (этап 10б M3): без сборки не работает, захолаживание SLOW, кислород — по реальной мощности, сохранение. */
class UtilitiesSubsystemTest {
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new UtilitiesSubsystem());
        worlds.add(w);
        return w;
    }

    private static AsuUnit asu(SimulationWorld w) {
        SimId id = w.topology().addDevice(UtilitiesSubsystem.AIR_SEPARATOR, new GridPos(0, 64, 0));
        return (AsuUnit) w.subsystem(UtilitiesSubsystem.class).unit(w.topology(), id);
    }

    /** Несобранная ВРУ стоит; собранная захолаживается за ≈ 36 ч физ. и даёт O₂ = W/0,42 кВт·ч в реальную секунду. */
    @Test
    void asuCoolsDownAndMakesOxygenByRealPower() {
        SimulationWorld w = world();
        AsuUnit u = asu(w);
        double watts = AsuUnit.SPEC.ratedCompressorW();
        u.setCompressor(watts, AsuUnit.coolingWaterNeedKgS(watts));
        for (int i = 0; i < 200; i++) w.tick(ExternalInputs.NONE);
        assertThat(u.asu().state()).isEqualTo(AsuState.OFF);
        u.setFormed(true);
        double dtp = 0.05 * w.clock().kSlow();
        int n = 0;
        for (; n < (int) (60 * 3600 / dtp) && u.asu().state() != AsuState.RUNNING; n++) w.tick(ExternalInputs.NONE);
        assertThat(u.asu().state()).isEqualTo(AsuState.RUNNING);
        assertThat(n * dtp / 3600).isBetween(25.0, 45.0);
        for (int i = 0; i < 40; i++) w.tick(ExternalInputs.NONE);
        assertThat(u.productRate("oxygen")).isCloseTo(watts / (0.42 * 3.6e6), within(0.01));
        assertThat(u.productRate("nitrogen")).isGreaterThan(u.productRate("oxygen"));
        assertThat(u.productRate("argon")).isPositive();
        u.setFormed(false);
        for (int i = 0; i < 40; i++) w.tick(ExternalInputs.NONE);
        assertThat(u.productRate("oxygen")).isZero();
    }

    /** Сохранение: холодный блок, адсорберы и уставки переживают перезагрузку. */
    @Test
    void saveAndLoadKeepTheColdBox() {
        SimulationWorld w = world();
        AsuUnit u = asu(w);
        u.setFormed(true);
        u.setCompressor(8e5, AsuUnit.coolingWaterNeedKgS(8e5));
        u.asu().setRegenerationHeater(false);
        u.asu().setLiquidFraction(0.2, false);
        for (int i = 0; i < 4000; i++) w.tick(ExternalInputs.NONE);
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        AsuUnit u2 = (AsuUnit) c.subsystem(UtilitiesSubsystem.class).unit(c.topology(), w.topology().deviceAt(0));
        assertThat(u2.formed()).isTrue();
        assertThat(u2.asu().coldBoxK()).isEqualTo(u.asu().coldBoxK());
        assertThat(u2.asu().state()).isEqualTo(u.asu().state());
        assertThat(u2.asu().molsieveLoading()).isEqualTo(u.asu().molsieveLoading());
        assertThat(u2.asu().regenerationHeater()).isFalse();
        assertThat(u2.asu().liquidOxygen()).isFalse();
        assertThat(u2.asu().liquidFraction()).isEqualTo(0.2);
    }
}
