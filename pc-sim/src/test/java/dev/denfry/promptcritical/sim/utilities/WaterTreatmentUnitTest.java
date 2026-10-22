package dev.denfry.promptcritical.sim.utilities;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Блоки водоподготовки в мире (этап 11 M3): сорта воды, известь, проскок и регенерация колонны, сохранение. */
class WaterTreatmentUnitTest {
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

    private static WaterTreatmentUnit unit(SimulationWorld w, String type) {
        SimId id = w.topology().addDevice(type, new GridPos(0, 64, 0));
        return (WaterTreatmentUnit) w.subsystem(UtilitiesSubsystem.class).unit(w.topology(), id);
    }

    /** Осветлитель: с известью — осветлённая вода и расход CaO по карбонатной жёсткости; без извести — сырая. */
    @Test
    void clarifierNeedsLime() {
        SimulationWorld w = world();
        WaterTreatmentUnit c = unit(w, UtilitiesSubsystem.WATER_CLARIFIER);
        assertThat(c.accepts(WaterGrade.RAW)).isTrue();
        assertThat(c.accepts(WaterGrade.FILTERED)).isFalse();
        assertThat(c.outlet(WaterGrade.RAW)).isEqualTo(WaterGrade.RAW);
        c.setLime(true);
        for (int i = 0; i < 400; i++) {
            c.pass(WaterGrade.RAW, 0.01);
            w.tick(ExternalInputs.NONE);
        }
        assertThat(c.outlet(WaterGrade.RAW)).isEqualTo(WaterGrade.CLARIFIED);
        // 4 м³ сырой воды: снято 3,4 мг-экв/л жёсткости + CO₂ — ≈ 0,1 кг CaO на м³
        assertThat(c.limeDueKg() / 4).isBetween(0.08, 0.2);
    }

    /** Колонна: умягчает до проскока, после 1,3 рабочей ёмкости отдаёт входную воду; регенерация солью. */
    @Test
    void ionExchangerBreaksThroughAndRegenerates() {
        SimulationWorld w = world();
        WaterTreatmentUnit x = unit(w, UtilitiesSubsystem.ION_EXCHANGE_COLUMN);
        assertThat(x.accepts(WaterGrade.CLARIFIED)).isFalse();
        assertThat(x.outlet(WaterGrade.FILTERED)).isEqualTo(WaterGrade.SOFTENED);
        // 500 г-экв ÷ 1,6 г-экв/м³ ≈ 312 м³ до проскока
        double m3 = 0;
        while (x.outlet(WaterGrade.FILTERED) == WaterGrade.SOFTENED && m3 < 2000) {
            x.pass(WaterGrade.FILTERED, 1);
            m3 += 1;
            w.tick(ExternalInputs.NONE);
        }
        assertThat(m3).isBetween(312.0, 420.0);
        assertThat(x.train().resin(0).exhausted()).isTrue();
        double salt = x.train().regenerate(0);
        assertThat(salt).isBetween(70.0, 100.0);                    // NaCl 0,15 кг/г-экв × ≈ 1,15 × 500 г-экв
        x.train().stepSlow(0);
        assertThat(x.outlet(WaterGrade.FILTERED)).isEqualTo(WaterGrade.SOFTENED);
    }

    /** Сохранение: загрузка смолы и фильтра переживают перезагрузку. */
    @Test
    void saveAndLoadKeepLoading() {
        SimulationWorld w = world();
        WaterTreatmentUnit f = unit(w, UtilitiesSubsystem.SAND_FILTER);
        WaterTreatmentUnit x = unit(w, UtilitiesSubsystem.ION_EXCHANGE_COLUMN);
        for (int i = 0; i < 200; i++) {
            f.pass(WaterGrade.CLARIFIED, 2);
            x.pass(WaterGrade.RAW, 0.5);
            w.tick(ExternalInputs.NONE);
        }
        assertThat(f.train().filterLoadingFrac()).isPositive();
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        UtilitiesSubsystem u = c.subsystem(UtilitiesSubsystem.class);
        WaterTreatmentUnit f2 = (WaterTreatmentUnit) u.unit(c.topology(), w.topology().deviceAt(0));
        WaterTreatmentUnit x2 = (WaterTreatmentUnit) u.unit(c.topology(), w.topology().deviceAt(1));
        assertThat(f2.train().filterLoadingFrac()).isEqualTo(f.train().filterLoadingFrac());
        assertThat(x2.train().resin(0).loadingFrac()).isEqualTo(x.train().resin(0).loadingFrac());
    }
}
