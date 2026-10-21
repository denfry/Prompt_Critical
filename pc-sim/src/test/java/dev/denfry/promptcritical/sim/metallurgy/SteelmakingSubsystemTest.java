package dev.denfry.promptcritical.sim.metallurgy;

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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Сталь в мире (этап 10б M3): конвертер с кислородом из буфера фурмы и газоотводом, нагревательная печь с садкой,
 * остывание раската на воздухе, термообработка при выгрузке, сохранение.
 */
class SteelmakingSubsystemTest {
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private int x;

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new IronmakingSubsystem());
        worlds.add(w);
        return w;
    }

    private <U extends IronmakingUnit> U unit(SimulationWorld w, String type, Class<U> cls) {
        SimId id = w.topology().addDevice(type, new GridPos(x++, 64, 0));
        return cls.cast(w.subsystem(IronmakingSubsystem.class).unit(w.topology(), id));
    }

    private static double tickPhys(SimulationWorld w) {
        return 0.05 * w.clock().kSlow();
    }

    private static SteelMelt hotMetal(double kg) {
        return new SteelMelt(kg, 1623.15, Map.of(AlloyElement.C, 0.045, AlloyElement.SI, 0.005, AlloyElement.MN, 0.003,
                AlloyElement.P, 0.001, AlloyElement.S, 0.0003));
    }

    /** Плавка 10 т: дуть можно только в вертикали с опущенной фурмой; C до ≤ 0,06 %, выпуск 1600–1720 °C, газ копится. */
    @Test
    void converterBlowsFromTheLanceBufferAndTaps() {
        SimulationWorld w = world();
        ConverterUnit u = unit(w, IronmakingSubsystem.CONVERTER, ConverterUnit.class);
        u.converter().charge(hotMetal(8_000), 2_000, 500);
        u.setTilt(60);
        u.setLance(1.2);
        assertThat(u.oxygenDemandNm3S()).isZero();
        u.setTilt(0);
        u.setLance(ConverterUnit.MAX_LANCE_M);
        assertThat(u.oxygenDemandNm3S()).isZero();
        u.setLance(1.2);
        assertThat(u.oxygenDemandNm3S()).isEqualTo(ConverterUnit.DEFAULT_O2_NM3S);
        double dtp = tickPhys(w), o2 = 0, gas = 0;
        for (int i = 0; i < 20 * 60 && u.converter().carbonFrac() > 0.0006; i++) {
            double give = u.oxygenDemandNm3S() * dtp;
            o2 += give;
            u.supplyOxygen(give);
            w.tick(ExternalInputs.NONE);
            gas += u.takeOffgasNm3(Double.MAX_VALUE);
        }
        assertThat(u.converter().carbonFrac()).isLessThan(0.0006);
        assertThat(u.converter().phase()).isEqualTo(ConverterPhase.BLOWING);
        assertThat(u.converter().o2BlownNm3()).isCloseTo(o2, within(o2 * 0.05 + 2));
        assertThat(gas).isGreaterThan(300);
        u.setLance(ConverterUnit.MAX_LANCE_M);
        w.tick(ExternalInputs.NONE);
        for (int i = 0; i < 40; i++) w.tick(ExternalInputs.NONE);
        assertThat(u.converter().phase()).isEqualTo(ConverterPhase.BLOWN);
        SteelMelt steel = u.converter().tap();
        assertThat(steel.tempK() - 273.15).isBetween(1600.0, 1720.0);
        assertThat(steel.massKg()).isBetween(9_000.0, 10_400.0);
        assertThat(u.log().poll()).isNotNull();
    }

    /** Методическая печь на коксовом газе: кладка выходит на уставку, заготовка 150 мм прогревается до 1200 °C за 0,5–3 ч физ. */
    @Test
    void reheatingFurnaceHeatsABilletOnCokeOvenGas() {
        SimulationWorld w = world();
        BatchFurnaceUnit f = unit(w, IronmakingSubsystem.REHEATING_FURNACE, BatchFurnaceUnit.class);
        double dtp = tickPhys(w);
        Runnable feed = () -> f.setGasSupply(ProcessGas.COKE_OVEN, f.gasDemandNm3S(ProcessGas.COKE_OVEN));
        int ticks = (int) (12 * 3600 / dtp);
        for (int i = 0; i < ticks && f.furnace().tempK() < f.setpointK() - 5; i++) {
            feed.run();
            w.tick(ExternalInputs.NONE);
        }
        assertThat(f.furnace().tempK()).isCloseTo(f.setpointK(), within(10.0));
        assertThat(f.furnace().load(0, 100, 0.15, 293.15)).isTrue();
        assertThat(f.furnace().load(0, 100, 0.15, 293.15)).isFalse();
        int n = 0;
        for (; n < (int) (5 * 3600 / dtp) && f.furnace().pieceTempK(0) < 1473.15; n++) {
            feed.run();
            w.tick(ExternalInputs.NONE);
        }
        double hours = n * dtp / 3600;
        assertThat(hours).isBetween(0.5, 3.0);
        assertThat(f.furnace().tempK()).isCloseTo(f.setpointK(), within(40.0));
        assertThat(f.furnace().unload(0)).isGreaterThanOrEqualTo(1473.15);
        assertThat(f.furnace().occupied(0)).isFalse();
    }

    /** Остывание на воздухе: заготовка 150 мм за 20 мин остаётся выше 500 °C, лист 8 мм за 5 мин — ниже 500 °C. */
    @Test
    void hotStockCoolsInAir() {
        double billet = AirCooling.tempAfter(1473.15, 293.15, 0.15, 20 * 60);
        double plate = AirCooling.tempAfter(1473.15, 293.15, 0.008, 5 * 60);
        assertThat(billet - 273.15).isBetween(500.0, 1000.0);
        assertThat(plate - 273.15).isLessThan(500);
        assertThat(AirCooling.tempAfter(1473.15, 293.15, 0.15, 30)).isGreaterThan(AirCooling.tempAfter(1473.15, 293.15, 0.15, 300));
        assertThat(AirCooling.tempAfter(1473.15, 293.15, 0.15, 1e7)).isEqualTo(293.15);
        assertThat(AirCooling.tempAfter(293.15, 293.15, 0.15, 100)).isEqualTo(293.15);
    }

    /** Выгрузка на воздух: закалённая — отпуск в Q+T, горячекатаная — нормализация, 620 °C — снятие напряжений. */
    @Test
    void airCoolingAppliesTheRightTreatment() {
        SteelGrade rolled = SteelGrade.of(SteelFamily.CARBON, "20", Map.of("C", 0.2f, "Mn", 0.5f), HeatTreatState.AS_ROLLED);
        SteelGrade q = HeatTreatment.quench(rolled, 1173.15);
        assertThat(q.state()).isEqualTo(HeatTreatState.QUENCHED);
        assertThat(HeatTreatment.airCool(q, 873.15).state()).isEqualTo(HeatTreatState.QUENCHED_TEMPERED);
        double ac3 = HeatTreatment.ac3K(rolled);
        assertThat(HeatTreatment.airCool(rolled, ac3 + 50).state()).isEqualTo(HeatTreatState.NORMALIZED);
        assertThat(HeatTreatment.airCool(rolled, 893.15).state()).isEqualTo(HeatTreatState.STRESS_RELIEVED);
        assertThat(HeatTreatment.airCool(rolled, 500).state()).isEqualTo(HeatTreatState.AS_ROLLED);
    }

    /** Сохранение: конвертер посреди продувки и печь с садкой. */
    @Test
    void saveAndLoadKeepConverterAndFurnace() {
        SimulationWorld w = world();
        ConverterUnit u = unit(w, IronmakingSubsystem.CONVERTER, ConverterUnit.class);
        BatchFurnaceUnit f = unit(w, IronmakingSubsystem.HEAT_TREATMENT_FURNACE, BatchFurnaceUnit.class);
        u.converter().charge(hotMetal(8_000), 1_500, 500);
        u.setTilt(0);
        u.setLance(1.0);
        u.setOxygenSetpoint(0.7);
        f.setSetpoint(1150, 300);
        f.furnace().load(3, 80, 0.008, 900);
        double dtp = tickPhys(w);
        for (int i = 0; i < 100; i++) {
            u.supplyOxygen(u.oxygenDemandNm3S() * dtp);
            f.setGasSupply(ProcessGas.CONVERTER, f.gasDemandNm3S(ProcessGas.CONVERTER));
            w.tick(ExternalInputs.NONE);
        }
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        IronmakingSubsystem sub = c.subsystem(IronmakingSubsystem.class);
        ConverterUnit u2 = (ConverterUnit) sub.unit(c.topology(), w.topology().deviceAt(0));
        BatchFurnaceUnit f2 = (BatchFurnaceUnit) sub.unit(c.topology(), w.topology().deviceAt(1));
        assertThat(u2.converter().phase()).isEqualTo(ConverterPhase.BLOWING);
        assertThat(u2.converter().carbonFrac()).isEqualTo(u.converter().carbonFrac());
        assertThat(u2.converter().tempK()).isEqualTo(u.converter().tempK());
        assertThat(u2.converter().slagKg()).isEqualTo(u.converter().slagKg());
        assertThat(u2.oxygenSetpointNm3S()).isEqualTo(0.7);
        assertThat(u2.lanceM()).isEqualTo(1.0);
        assertThat(f2.setpointK()).isEqualTo(1150);
        assertThat(f2.furnace().occupied(3)).isTrue();
        assertThat(f2.furnace().pieceTempK(3)).isEqualTo(f.furnace().pieceTempK(3));
        assertThat(f2.furnace().tempK()).isEqualTo(f.furnace().tempK());
    }
}
