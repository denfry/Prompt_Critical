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
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Литейка и цех в мире симуляции (08 §8.9–8.10, этап 8 M2). */
class FoundryShopTest {
    private WorkerPool pool;
    private SimulationWorld world;
    private FoundrySubsystem shop;

    @BeforeEach
    void setUp() {
        pool = WorkerPool.direct();
        world = newWorld();
        shop = world.subsystem(FoundrySubsystem.class);
    }

    @AfterEach
    void tearDown() {
        world.close();
        pool.close();
    }

    private SimulationWorld newWorld() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new FoundrySubsystem());
        return w;
    }

    private SimId add(String type, int x) {
        return world.topology().addDevice(type, new GridPos(x, 64, 0));
    }

    @SuppressWarnings("unchecked")
    private <U extends FoundryUnit> U unit(SimId id) {
        return (U) shop.unit(world.topology(), id);
    }

    /** {@code seconds} реального времени; перед каждым тиком — запись входов. */
    private void run(double seconds, LongConsumer beforeTick) {
        int ticks = (int) Math.round(seconds * 20);
        for (int i = 0; i < ticks; i++) {
            beforeTick.accept(world.clock().tick());
            world.tick(ExternalInputs.NONE);
        }
    }

    private static LongConsumer spin(ShaftInput shaft, double rpm) {
        return t -> shaft.sample(rpm, true, t);
    }

    @Test
    void unitsFollowTopologyInIdOrderAndLeaveWithTheDevice() {
        SimId a = add(FoundrySubsystem.CUPOLA, 0);
        SimId b = add(FoundrySubsystem.MACHINE_LATHE, 1);
        add("promptcritical:grate_stoker", 2);
        world.tick(ExternalInputs.NONE);
        assertThat(shop.unit(a)).isInstanceOf(CupolaUnit.class);
        assertThat(shop.unit(b)).isInstanceOf(MachineToolUnit.class);
        world.topology().removeDevice(a);
        world.tick(ExternalInputs.NONE);
        assertThat(shop.unit(a)).isNull();
        assertThat(shop.unit(world.topology(), a)).isNull();
    }

    @Test
    void cupolaMeltsOnlyUnderBlastAndPoursSoundGrayIron() {
        CupolaUnit cupola = unit(add(FoundrySubsystem.CUPOLA, 0));
        SandMoldUnit mold = unit(add(FoundrySubsystem.SAND_MOLD, 1));
        assertThat(cupola.charge(400, 48)).isTrue();                 // кокс 12 %
        run(5, t -> { });
        assertThat(cupola.furnace().wellKg()).isZero();               // без дутья не плавит

        run(30, spin(cupola.shaft, 64));
        assertThat(cupola.furnace().wellKg()).isGreaterThan(300);
        // 12 % кокса → ≈ 1450 °C на выпуске (CupolaFurnace), горн остывает ≈ 3 К/с реальных
        assertThat(cupola.superheatK()).isBetween(150.0, 260.0);
        assertThat(cupola.powerW()).isCloseTo(CupolaUnit.BLOWER_NOMINAL_W, within(1e-6));
        assertThat(cupola.lit()).isTrue();

        mold.form(CastingKind.VALVE_BODY, true, SandMoldUnit.DEFAULT_MOISTURE);
        double before = cupola.furnace().wellKg();
        assertThat(shop.pour(cupola, mold)).isTrue();
        assertThat(cupola.furnace().wellKg()).isCloseTo(before - 35, within(1e-9));
        assertThat(mold.stage()).isEqualTo(SandMoldUnit.Stage.LIQUID);
        assertThat(shop.pour(cupola, mold)).isFalse();                // форма уже залита
        // чугун: B ≈ 1,36 B стали при перегреве ~200 К → t_s ≈ 7 мин физ. (корпус DN 100, M = 1,5 см)
        assertThat(mold.solidificationS() / 60).isBetween(6.0, 8.5);

        run(mold.solidificationS() / 72 + 1, t -> { });
        assertThat(mold.stage()).isEqualTo(SandMoldUnit.Stage.SOLID);
        assertThat(mold.readyForShakeout()).isFalse();
        run(2 * mold.solidificationS() / 72 + 1, t -> { });
        assertThat(mold.readyForShakeout()).isTrue();
        CastingSpec c = mold.casting();
        assertThat(c.alloyId()).isEqualTo("gray_iron");
        assertThat(c.defect()).isEqualTo(CastingDefect.NONE);
        assertThat(c.dn()).isEqualTo(100);
    }

    @Test
    void blowerPowerFollowsFanLaws() {
        CupolaUnit cupola = unit(add(FoundrySubsystem.CUPOLA, 0));
        run(0.2, spin(cupola.shaft, 128));
        assertThat(cupola.airNm3S()).isCloseTo(2 * CupolaUnit.AIR_NOMINAL_NM3S, within(1e-12));
        assertThat(cupola.powerW()).isCloseTo(8 * CupolaUnit.BLOWER_NOMINAL_W, within(1e-9));
        run(0.2, spin(cupola.shaft, 0));
        assertThat(cupola.powerW()).isZero();
    }

    @Test
    void crucibleMeltsCopperOnCoalAndRefusesAnotherAlloy() {
        CrucibleUnit crucible = unit(add(FoundrySubsystem.CRUCIBLE, 0));
        assertThat(crucible.addFuel(100)).isTrue();
        assertThat(crucible.charge(CastAlloy.COPPER, 100)).isTrue();
        assertThat(crucible.charge(CastAlloy.ALUMINIUM, 1)).isFalse();
        run(15, t -> { });
        assertThat(crucible.liquidKg()).isZero();                    // ≈ 27 с до полного расплавления
        run(45, t -> { });
        assertThat(crucible.liquidKg()).isEqualTo(100);
        assertThat(crucible.superheatK()).isBetween(50.0, 435.0);      // предел 60 кВт / 40 Вт/К
        // 400 кВт по топливу, уголь 25 МДж/кг: 60 с реальных = 4320 с физ. → ≈ 69 кг
        assertThat(crucible.fuelKg()).isCloseTo(100 - 400e3 / 25e6 * 60 * 72, within(1.0));
        assertThat(crucible.lit()).isTrue();
    }

    @Test
    void moldLeftInTheRainCastsPorousCopper() {
        CrucibleUnit crucible = unit(add(FoundrySubsystem.CRUCIBLE, 0));
        SandMoldUnit dry = unit(add(FoundrySubsystem.SAND_MOLD, 1));
        SandMoldUnit wet = unit(add(FoundrySubsystem.SAND_MOLD, 2));
        crucible.addFuel(100);
        crucible.charge(CastAlloy.COPPER, 100);
        run(60, t -> { });
        dry.form(CastingKind.BEARING_HOUSING, true, SandMoldUnit.DEFAULT_MOISTURE);
        wet.form(CastingKind.BEARING_HOUSING, true, SandMoldUnit.DEFAULT_MOISTURE);
        wet.wet(40);                                                  // 5 % + 40 с × 0,05 %/с = 7 % > 6 %
        assertThat(shop.pour(crucible, dry)).isTrue();
        assertThat(shop.pour(crucible, wet)).isTrue();
        run(30, t -> { });
        assertThat(dry.casting().defect()).isEqualTo(CastingDefect.NONE);
        assertThat(wet.casting().defect()).isEqualTo(CastingDefect.GAS_POROSITY);
        assertThat(wet.casting().serial()).isNotEqualTo(dry.casting().serial());
    }

    @Test
    void moldWithoutRiserShrinksAndThinVanesTearInAluminium() {
        CrucibleUnit crucible = unit(add(FoundrySubsystem.CRUCIBLE, 0));
        SandMoldUnit noRiser = unit(add(FoundrySubsystem.SAND_MOLD, 1));
        SandMoldUnit impeller = unit(add(FoundrySubsystem.SAND_MOLD, 2));
        crucible.addFuel(100);
        crucible.charge(CastAlloy.ALUMINIUM, 100);
        run(60, t -> { });
        noRiser.form(CastingKind.GEAR_BLANK, false, SandMoldUnit.DEFAULT_MOISTURE);
        impeller.form(CastingKind.IMPELLER, true, SandMoldUnit.DEFAULT_MOISTURE);
        assertThat(shop.pour(crucible, noRiser)).isTrue();
        assertThat(shop.pour(crucible, impeller)).isTrue();
        run(30, t -> { });
        assertThat(noRiser.casting().defect()).isEqualTo(CastingDefect.SHRINKAGE_CAVITY);
        assertThat(impeller.casting().defect()).isEqualTo(CastingDefect.HOT_TEAR);
    }

    @Test
    void latheTurnsAtModerateSpeedAndBurnsTheToolAtHighSpeed() {
        MachineToolUnit lathe = unit(add(FoundrySubsystem.MACHINE_LATHE, 0));
        CastingSpec body = new CastingSpec(CastingKind.VALVE_BODY, "gray_iron", 100, CastingDefect.NONE, 0, 1);
        assertThat(lathe.insertTool(0)).isTrue();
        assertThat(lathe.load(body)).isTrue();
        // D 220 мм, 64 об/мин → v_c 44 м/мин; снятие 2·0,25·44 = 368 мм³/с → 15 000 мм³ за ≈ 41 с
        run(35, spin(lathe.shaft, 64));
        assertThat(lathe.done()).isFalse();
        assertThat(lathe.powerW()).isCloseTo(1100 * 2 * 0.25 * Machining.cuttingSpeed(220, 64) / 60 + 7_500 * 0.02, within(1.0));
        run(7, spin(lathe.shaft, 64));
        assertThat(lathe.done()).isTrue();
        // T = (63/44,2)^8 ≈ 17 мин → износ за 41 с ≈ 0,04
        assertThat(lathe.toolWear()).isBetween(0.03, 0.05);
        CastingSpec turned = lathe.unload();
        assertThat(turned.machinedMask()).isEqualTo(CastingSpec.TURNED);
        assertThat(lathe.load(turned)).isFalse();                     // уже точёная

        MachineToolUnit fast = unit(add(FoundrySubsystem.MACHINE_LATHE, 1));
        fast.insertTool(0);
        fast.load(body);
        run(5, spin(fast.shaft, 256));                                // v_c 177 м/мин: стойкость доли секунды
        assertThat(fast.toolWear()).isEqualTo(1.0);
        assertThat(fast.done()).isFalse();
        assertThat(fast.running()).isFalse();
    }

    @Test
    void drillPressGearsUpTheShaftAndChilledIronIsRejected() {
        MachineToolUnit drill = unit(add(FoundrySubsystem.DRILL_PRESS, 0));
        drill.insertTool(0);
        assertThat(drill.load(new CastingSpec(CastingKind.WHEEL, "gray_iron", 0, CastingDefect.CHILL, 0, 1))).isFalse();
        assertThat(drill.load(new CastingSpec(CastingKind.INGOT_MOLD, "gray_iron", 0, CastingDefect.NONE, 0, 2))).isFalse();
        assertThat(drill.load(new CastingSpec(CastingKind.VALVE_BODY, "gray_iron", 100, CastingDefect.NONE, 0, 3))).isTrue();
        run(1, spin(drill.shaft, 256));
        assertThat(drill.spindleRpm()).isEqualTo(1024);
        // Ø18, f 0,2: снятие (d/2)(f/2)v_c = 9·0,1·57,9·1000/60 ≈ 869 мм³/с → 20 000 мм³ за ≈ 23 с
        run(25, spin(drill.shaft, 256));
        assertThat(drill.done()).isTrue();
        assertThat(drill.unload().machinedMask()).isEqualTo(CastingSpec.DRILLED);
    }

    @Test
    void moldingStationCyclesWithShaftSpeed() {
        CyclicMachine station = unit(add(FoundrySubsystem.MOLDING_STATION, 0));
        station.setLoaded(true);
        run(4, spin(station.shaft, 64));
        assertThat(station.takeCycle()).isFalse();
        run(4.05, spin(station.shaft, 64));
        assertThat(station.takeCycle()).isTrue();
        run(4.05, spin(station.shaft, 128));
        assertThat(station.takeCycle()).isTrue();                    // вдвое быстрее
        assertThat(station.powerW()).isCloseTo(2 * 7_500, within(1e-9));
        station.setLoaded(false);
        run(0.1, spin(station.shaft, 64));
        assertThat(station.powerW()).isCloseTo(7_500 * 0.02, within(1e-9));
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        CupolaUnit cupola = unit(add(FoundrySubsystem.CUPOLA, 0));
        SandMoldUnit mold = unit(add(FoundrySubsystem.SAND_MOLD, 1));
        MachineToolUnit lathe = unit(add(FoundrySubsystem.MACHINE_LATHE, 2));
        cupola.charge(400, 48);
        lathe.insertTool(0.25);
        lathe.load(new CastingSpec(CastingKind.GEAR_BLANK, "gray_iron", 0, CastingDefect.NONE, 0, 9));
        run(20, t -> {
            cupola.shaft.sample(64, true, t);
            lathe.shaft.sample(48, true, t);
        });
        mold.form(CastingKind.WHEEL, true, 0.055);
        shop.pour(cupola, mold);
        run(1, t -> { });
        MutableStateTree saved = world.saveTree();
        long hash = world.stateHash();

        SimulationWorld copy = newWorld();
        copy.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(copy.stateHash()).isEqualTo(hash);
        FoundrySubsystem shop2 = copy.subsystem(FoundrySubsystem.class);
        SandMoldUnit mold2 = (SandMoldUnit) shop2.unit(world.topology().deviceAt(1));
        assertThat(mold2.stage()).isEqualTo(SandMoldUnit.Stage.LIQUID);
        assertThat(mold2.moisture()).isEqualTo(0.055);
        MachineToolUnit lathe2 = (MachineToolUnit) shop2.unit(world.topology().deviceAt(2));
        assertThat(lathe2.toolWear()).isEqualTo(lathe.toolWear());
        assertThat(lathe2.progress()).isEqualTo(lathe.progress());
        copy.close();
    }
}
