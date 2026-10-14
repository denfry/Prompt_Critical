package dev.denfry.promptcritical.sim.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Аппаратная защита: T-1.11-01…06 (01 §1.16) и автомат §1.11.5 на уставках ПР-300. */
class ProtectionSystemTest {
    static final WaterProperties W = WaterProperties.tables();
    static final double DT = 0.05;
    static final ProtectionConfig CFG = ProtectionConfig.defaults();
    static final PlantNominals PR300 = PlantNominals.of(W, 15.5e6, 593.15, 17.2e6, 12.5e6);

    /** Запись событий защиты. */
    static final class Recorder implements ProtectionEventSink {
        final List<TripReason> trips = new ArrayList<>();
        final List<Boolean> firstOuts = new ArrayList<>();
        final List<TripSource> sources = new ArrayList<>();
        final List<Set<TripReason>> denied = new ArrayList<>();

        @Override
        public void reactorTrip(TripReason reason, TripSource source, boolean firstOut, long tick) {
            trips.add(reason);
            firstOuts.add(firstOut);
            sources.add(source);
        }

        @Override
        public void resetDenied(Set<TripReason> reasons) {
            denied.add(reasons);
        }
    }

    /** Шкаф и счётчик тиков: шаг защиты — в начале тика по измерениям конца прошлого. */
    static final class Driver {
        final FakeInputs in;
        final Recorder events = new Recorder();
        final ProtectionSystem ps;
        long tick;

        Driver(FakeInputs in, DnbrCalculator dnbr) {
            this.in = in;
            this.ps = ProtectionSystem.pressurizedWater(CFG, PR300, dnbr, events);
        }

        double time() {
            return tick * DT;
        }

        void step() {
            ps.step(DT, in, tick++);
        }

        void run(double seconds) {
            for (long n = Math.round(seconds / DT); n > 0; n--) step();
        }

        /** Пуск: пусковую уставку снимают на 20 %, затем выход на номинал. */
        Driver atPower() {
            double nominal = in.power;
            in.power = 0.2;
            in.all(TripReason.HIGH_FLUX, 3, 0.2);
            step();
            assertThat(ps.setLowFluxSetpointBlocked(true)).isTrue();
            in.power = nominal;
            in.all(TripReason.HIGH_FLUX, 3, nominal);
            step();
            assertThat(ps.mode()).isEqualTo(ProtectionMode.NORMAL);
            return this;
        }
    }

    static Driver pr300() {
        return new Driver(FakeInputs.pr300Nominal(), null).atPower();
    }

    @Test
    void setpointsFollowPr300Table() {
        ProtectionSystem ps = pr300().ps;
        assertThat(ps.setpointValue(TripReason.HIGH_PRIMARY_PRESSURE)).isCloseTo(16.6e6, within(0.05e6));
        assertThat(ps.setpointValue(TripReason.LOW_PRIMARY_PRESSURE)).isCloseTo(13.7e6, within(0.05e6));
        assertThat(ps.setpointValue(TripReason.HIGH_COOLANT_TEMP) - 273.15).isCloseTo(330, within(0.5));
        assertThat(ps.setpointValue(TripReason.HIGH_FLUX)).isEqualTo(1.07);
    }

    @Test
    void t1_11_01_faultedChannelVotesForTrip() {
        Driver d = pr300();
        d.in.set(TripReason.HIGH_FLUX, Double.NaN, 1.0, 1.0);
        d.step();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);
        assertThat(d.ps.channelState(TripReason.HIGH_FLUX, 0)).isEqualTo(ChannelState.FAULT);

        d.in.set(TripReason.HIGH_FLUX, Double.NaN, 1.2, 1.0);
        d.step();
        assertThat(d.ps.isTripped()).isTrue();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.HIGH_FLUX);
        assertThat(d.ps.channelState(TripReason.HIGH_FLUX, 1)).isEqualTo(ChannelState.TRIP);
        assertThat(d.ps.channelState(TripReason.HIGH_FLUX, 2)).isEqualTo(ChannelState.OK);
        assertThat(d.events.trips).containsExactly(TripReason.HIGH_FLUX);
    }

    @Test
    void t1_11_02_bypassTurnsTwoOfThreeIntoTwoOfTwo() {
        Driver d = pr300();
        assertThat(d.ps.setBypass(TripReason.HIGH_FLUX, 0, true)).isTrue();
        d.in.set(TripReason.HIGH_FLUX, 1.2, 1.2, 1.0);
        d.step();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);
        assertThat(d.ps.channelState(TripReason.HIGH_FLUX, 0)).isEqualTo(ChannelState.BYPASS);

        // Второй байпас той же причины и байпас в логике 2 из 2 запрещены.
        assertThat(d.ps.setBypass(TripReason.HIGH_FLUX, 2, true)).isFalse();
        assertThat(d.ps.setBypass(TripReason.TURBINE_TRIP, 0, true)).isFalse();

        d.in.set(TripReason.HIGH_FLUX, 1.2, 1.2, 1.2);
        d.step();
        assertThat(d.ps.isTripped()).isTrue();
    }

    @Test
    void t1_11_03_resetDeniedWhileCauseActive() {
        Driver d = pr300();
        d.in.all(TripReason.HIGH_FLUX, 3, 1.2);
        d.step();
        assertThat(d.ps.isTripped()).isTrue();

        assertThat(d.ps.reset()).isFalse();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.TRIPPED);
        assertThat(d.events.denied).hasSize(1);
        assertThat(d.events.denied.get(0)).contains(TripReason.HIGH_FLUX);

        // Причина ушла — но выдержка reset_delay_s (03b) ещё не прошла.
        d.in.power = 0.05;
        d.in.all(TripReason.HIGH_FLUX, 3, 0.05);
        d.run(5);
        assertThat(d.ps.reset()).isFalse();
        assertThat(d.events.denied.get(1)).isEmpty();
        d.run(5);
        assertThat(d.ps.reset()).isTrue();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.RESET_ARMED);

        d.in.withdrawal = true;
        d.step();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);
    }

    @Test
    void t1_11_04_setpointCanOnlyBeTightened() {
        Driver d = pr300();
        assertThat(d.ps.tightenSetpoint(TripReason.HIGH_FLUX, 1.10)).isFalse();
        assertThat(d.ps.setpointValue(TripReason.HIGH_FLUX)).isEqualTo(1.07);
        assertThat(d.ps.tightenSetpoint(TripReason.LOW_COOLANT_FLOW, 0.85)).isFalse();

        assertThat(d.ps.tightenSetpoint(TripReason.HIGH_FLUX, 1.05)).isTrue();
        d.in.all(TripReason.HIGH_FLUX, 3, 1.06);
        d.step();
        assertThat(d.ps.isTripped()).isTrue();
    }

    @Test
    void t1_11_05_responseTimeFromConditionToRodMotion() {
        Driver d = pr300();
        double sensorDelay = 0.1;
        long start = d.tick;
        double motion = Double.NaN;
        for (int i = 0; i < 40 && Double.isNaN(motion); i++) {
            // Измерение конца прошлого тика; датчик — чистое запаздывание τ, истинная мощность 1,2 с t = 0.
            double t = (d.tick - start) * DT;
            d.in.all(TripReason.HIGH_FLUX, 3, t - sensorDelay >= -1e-9 ? 1.2 : 1.0);
            d.step();
            if (d.ps.rodsReleased()) motion = (d.tick - start) * DT; // стержни идут уже в этом тике
        }
        assertThat(motion).isCloseTo(0.1 + 0.05 + 0.15, within(DT));
        assertThat(d.ps.pendingActions()).containsExactly(new ProtectionAction.Trip(TripReason.HIGH_FLUX, true));
    }

    @Test
    void t1_11_06_commonCauseFailureToTrip() {
        Driver d = pr300();
        d.ps.injectCommonCauseFailure(true);
        d.in.all(TripReason.HIGH_FLUX, 3, 1.5);
        d.in.manual = true;
        d.run(2);
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.FAILED_ARMED);
        assertThat(d.ps.isTripped()).isFalse();
        assertThat(d.ps.rodsReleased()).isFalse();
        assertThat(d.ps.pendingActions()).isEmpty();
        assertThat(d.events.trips).isEmpty();
    }

    @Test
    void delayedFunctionClearsBeforeDelayExpires() {
        Driver d = pr300();
        d.in.loops(TripReason.LOW_COOLANT_FLOW, 0.85);
        d.run(0.2);
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.TRIP_PENDING);
        d.in.loops(TripReason.LOW_COOLANT_FLOW, 1.0);
        d.step();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);
        assertThat(d.events.trips).isEmpty();
    }

    /** 01 §1.14.3: выбег обеих ГЦН, ṁ = ṁ₀/(1 + t/12); освобождение стержней ≈ 1,85 с. */
    @Test
    void coastdownTripTimingMatchesExample2() {
        Driver d = pr300();
        d.in.remove(TripReason.LOSS_OF_DRIVE_POWER); // в примере защита — только по расходу
        long start = d.tick;
        double motion = Double.NaN;
        for (int i = 0; i < 100 && Double.isNaN(motion); i++) {
            double t = (d.tick - start) * DT;
            d.in.loops(TripReason.LOW_COOLANT_FLOW, 1 / (1 + t / 12));
            d.step();
            if (d.ps.rodsReleased()) motion = (d.tick - start) * DT;
        }
        assertThat(motion).isCloseTo(1.85, within(DT));
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.LOW_COOLANT_FLOW);
    }

    @Test
    void votingIsPerLoop() {
        Driver d = pr300();
        // По одному каналу в каждой петле — ни одна петля не набрала 2 из 3.
        d.in.set(TripReason.LOW_COOLANT_FLOW, 0.5, 1, 1, 0.5, 1, 1);
        d.run(1);
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);
        d.in.set(TripReason.LOW_COOLANT_FLOW, 1, 1, 1, 0.5, 0.5, 1);
        d.run(0.35);
        assertThat(d.ps.isTripped()).isTrue();
    }

    @Test
    void missingSensorsAreFaultsButNoSensorsMeansNoProtection() {
        Driver d = pr300();
        d.in.remove(TripReason.SEISMIC);
        d.step();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);

        d.in.set(TripReason.SEISMIC, 0.0); // один акселерометр из трёх: два недостающих — FAULT
        d.step();
        assertThat(d.ps.channelState(TripReason.SEISMIC, 2)).isEqualTo(ChannelState.FAULT);
        assertThat(d.ps.isTripped()).isTrue();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.SEISMIC);
    }

    @Test
    void permissivesBlockLowPowerFunctions() {
        Driver d = new Driver(FakeInputs.pr300Nominal(), null);
        d.in.power = 0.4;
        d.in.all(TripReason.HIGH_FLUX, 3, 0.2);
        d.in.all(TripReason.TURBINE_TRIP, 2, 1); // турбина стоит при N < 50 % — АЗ нет
        d.run(1);
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);

        // Пусковая уставка 25 % действует, пока оператор её не снял.
        d.in.all(TripReason.HIGH_FLUX, 3, 0.3);
        d.step();
        assertThat(d.ps.isTripped()).isTrue();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.HIGH_FLUX);

        Driver low = new Driver(FakeInputs.pr300Nominal(), null);
        low.in.power = 0.05;
        low.in.all(TripReason.HIGH_FLUX, 3, 0.05);
        low.step();
        assertThat(low.ps.setLowFluxSetpointBlocked(true)).isFalse();

        Driver turbine = pr300();
        turbine.in.all(TripReason.TURBINE_TRIP, 2, 1);
        turbine.step();
        assertThat(turbine.ps.firstOut()).isEqualTo(TripReason.TURBINE_TRIP);
    }

    @Test
    void firstOutIsTheEarliestVoteAndSourceIsKept() {
        Driver d = pr300();
        d.in.loops(TripReason.LOW_COOLANT_FLOW, 0.85);
        d.run(0.3); // ожидание t_d = 0,3 с: голос на тике 0, истекает на тике 6
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.TRIP_PENDING);
        d.in.all(TripReason.HIGH_FLUX, 3, 1.2);
        d.step();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.LOW_COOLANT_FLOW);
        assertThat(d.events.trips).containsExactlyInAnyOrder(TripReason.LOW_COOLANT_FLOW, TripReason.HIGH_FLUX);
        assertThat(d.events.firstOuts.get(d.events.trips.indexOf(TripReason.LOW_COOLANT_FLOW))).isTrue();

        Driver policy = pr300();
        policy.ps.requestTrip(TripReason.MANUAL, TripSource.SERVER_POLICY);
        policy.step();
        assertThat(policy.ps.firstOut()).isEqualTo(TripReason.MANUAL);
        assertThat(policy.ps.tripSource()).isEqualTo(TripSource.SERVER_POLICY);
        policy.step();
        assertThat(policy.ps.activeReasons()).isEmpty(); // запрос однократный
    }

    @Test
    void resetArmedTripsImmediatelyOnAnyVote() {
        Driver d = pr300();
        d.in.manual = true;
        d.step();
        d.in.manual = false;
        d.in.power = 0.05;
        d.in.all(TripReason.HIGH_FLUX, 3, 0.05);
        d.run(10);
        assertThat(d.ps.reset()).isTrue();
        d.in.loops(TripReason.LOW_SG_LEVEL, 0.1); // t_d = 2 с, но из RESET_ARMED — сразу
        d.step();
        assertThat(d.ps.isTripped()).isTrue();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.LOW_SG_LEVEL);
    }

    @Test
    void warningsAndSafetyInjection() {
        Driver d = pr300();
        d.in.power = 1.045;
        d.in.period = 15;
        d.step();
        assertThat(d.ps.pendingActions()).containsExactlyInAnyOrder(
                new ProtectionAction.RodBlock(InterlockReason.PERIOD_WARNING),
                new ProtectionAction.RodBlock(InterlockReason.POWER_WARNING));

        d.in.power = 0.6;
        d.in.period = Double.POSITIVE_INFINITY;
        d.in.pumpTripped = true;
        d.step();
        assertThat(d.ps.pendingActions()).containsExactly(new ProtectionAction.Stepback(0.5));

        d.in.pumpTripped = false;
        d.in.set(TripReason.LOW_PRIMARY_PRESSURE, 12.4e6, 12.4e6, 15.5e6);
        d.step();
        assertThat(d.ps.pendingActions()).containsExactly(new ProtectionAction.SafetyInjection("LOW_PRIMARY_PRESSURE"));
        d.run(0.5);
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.LOW_PRIMARY_PRESSURE);
        assertThat(d.ps.pendingActions()).contains(new ProtectionAction.SafetyInjection("LOW_PRIMARY_PRESSURE"));
    }

    @Test
    void shortPeriodIgnoresFallingPower() {
        Driver d = pr300();
        d.in.all(TripReason.SHORT_PERIOD, 3, -5);
        d.step();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);
        d.in.all(TripReason.SHORT_PERIOD, 3, 8);
        d.step();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.SHORT_PERIOD);
    }

    @Test
    void cabinetLossIsFailSafe() {
        Driver d = pr300();
        d.ps.injectCommonCauseFailure(false);
        assertThat(d.ps.isTripped()).isTrue();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.LOSS_OF_DRIVE_POWER);
        d.in.power = 0.05;
        d.in.all(TripReason.HIGH_FLUX, 3, 0.05);
        d.run(11);
        assertThat(d.ps.rodsReleased()).isTrue();
        assertThat(d.ps.reset()).isFalse();
        assertThat(d.events.denied.get(0)).isEqualTo(EnumSet.of(TripReason.LOSS_OF_DRIVE_POWER));
        d.ps.repairCabinet();
        assertThat(d.ps.reset()).isTrue();
    }

    @Test
    void resetNeedsRodsAtBottom() {
        Driver d = pr300();
        d.in.lua = true;
        d.step();
        assertThat(d.ps.tripSource()).isEqualTo(TripSource.LUA);
        d.in.lua = false;
        d.in.rodsAtBottom = false;
        d.in.power = 0.05;
        d.in.all(TripReason.HIGH_FLUX, 3, 0.05);
        d.run(11);
        assertThat(d.ps.reset()).isFalse();
        d.in.rodsAtBottom = true;
        d.step();
        assertThat(d.ps.reset()).isTrue();
    }
}
