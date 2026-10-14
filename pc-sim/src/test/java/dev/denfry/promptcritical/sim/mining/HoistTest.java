package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Шахтный подъём и канат (08 §8.2.8). */
class HoistTest {
    private static Hoist.Spec spec(boolean wet) {
        return new Hoist.Spec(100, 2, 20_000, 3, 3000, 300_000, 500, 1, true, wet);
    }

    private static Hoist hoist(double ropeMm, HoistConfig cfg) {
        return new Hoist(spec(false), RopeState.fresh(ropeMm, 1770, RopeConstruction.R6X19_FC), cfg);
    }

    /** T8.6: 100 м, клеть 3 т, 3,2 т людей, a = 1 м/с²: 28 мм — SF 6,5, людей нельзя; 36 мм — SF 10,5. */
    @Test
    void t8_6_ropeSafetyFactorGatesPeople() {
        Hoist thin = hoist(28, HoistConfig.defaults());
        assertThat(thin.rope().breakingForceN()).isCloseTo(458e3, within(1e3));
        assertThat(thin.maxRopeForceN(3200)).isCloseTo(70.1e3, within(0.1e3));
        assertThat(thin.safetyFactor(3200)).isCloseTo(6.5, within(0.1));
        assertThat(thin.load(HoistDuty.PEOPLE, 3200)).isEqualTo(Hoist.Verdict.FORBIDDEN);
        assertThat(thin.payloadKg()).isZero();
        assertThat(thin.load(HoistDuty.CARGO, 3200)).isEqualTo(Hoist.Verdict.ALLOWED);

        Hoist thick = hoist(36, HoistConfig.defaults());
        assertThat(thick.rope().breakingForceN()).isCloseTo(757e3, within(1e3));
        assertThat(thick.maxRopeForceN(3200)).isCloseTo(72.1e3, within(0.1e3));
        assertThat(thick.safetyFactor(3200)).isCloseTo(10.5, within(0.1));
        assertThat(thick.load(HoistDuty.PEOPLE, 3200)).isEqualTo(Hoist.Verdict.ALLOWED);
        assertThat(thick.sheaveRatioOk()).isTrue();
        assertThat(thin.sheaveRatioOk()).isTrue();
    }

    /** {@code ADVISORY}: загрузка выполняется, но помечается {@code UNSAFE_OPERATION}. */
    @Test
    void advisoryModeRecordsUnsafeOperation() {
        HoistConfig d = HoistConfig.defaults();
        HoistConfig advisory = new HoistConfig(d.sfPeople(), d.sfMixed(), d.sfCargo(), d.ropeN0Cycles(),
                d.corrosionPerDay(), d.ropeItemLengthM(), HoistConfig.SafetyMode.ADVISORY);
        Hoist h = hoist(28, advisory);
        assertThat(h.load(HoistDuty.PEOPLE, 3200)).isEqualTo(Hoist.Verdict.UNSAFE_OPERATION);
        assertThat(h.payloadKg()).isEqualTo(3200);
        assertThat(h.duty()).isEqualTo(HoistDuty.PEOPLE);
    }

    /** Тормоз держит клеть; без тормоза и момента клеть уходит вниз и снимается защитой по скорости. */
    @Test
    void brakeHoldsAndOverspeedTrips() {
        Hoist h = hoist(36, HoistConfig.defaults());
        h.load(HoistDuty.CARGO, 3000);
        for (int i = 0; i < 100; i++) h.stepFast(0.01, 0, true);
        assertThat(h.state()).isEqualTo(HoistState.HELD);
        assertThat(h.positionM()).isZero();

        h.setSpeedLimit(5);
        int steps = 0;
        while (h.state() != HoistState.TRIP_OVERSPEED && steps++ < 1000) h.stepFast(0.01, 0, false);
        assertThat(h.state()).isEqualTo(HoistState.TRIP_OVERSPEED);
        assertThat(-h.speedMs()).isCloseTo(5.75, within(0.15));
        for (int i = 0; i < 100; i++) h.stepFast(0.01, 0, false);
        assertThat(h.speedMs()).isZero();
        assertThat(h.state()).isEqualTo(HoistState.TRIP_OVERSPEED);
        assertThat(h.positionM()).isBetween(1.0, 5.0);
        h.reset();
        assertThat(h.state()).isEqualTo(HoistState.HELD);
    }

    /** Переподъём: клеть уходит выше устья — защита. Потеря питания — тормоз. */
    @Test
    void overwindAndPowerLossTrip() {
        Hoist h = hoist(36, HoistConfig.defaults());
        h.stepFast(0.01, 2 * 3000 * Hoist.G * 2, false);
        assertThat(h.state()).isEqualTo(HoistState.TRIP_OVERWIND);
        assertThat(h.positionM()).isZero();
        h.reset();
        h.tripPowerLoss();
        h.stepFast(0.01, 0, false);
        assertThat(h.state()).isEqualTo(HoistState.TRIP_POWER_LOSS);
        assertThat(h.speedMs()).isZero();
    }

    /** Спуск на регуляторе скорости: износ, наработка и перегиб каната за рейс. */
    @Test
    void controlledTripWearsRope() {
        Hoist h = hoist(36, HoistConfig.defaults());
        h.load(HoistDuty.CARGO, 4000);
        double dt = 0.01;
        double mEff = 20_000 / 4.0 + 7000;
        for (int dir = 0; dir < 2; dir++) {
            for (int i = 0; i < 20_000; i++) {
                double z = h.positionM();
                double remaining = dir == 0 ? 100 - z : z;
                double target = Math.min(8, Math.sqrt(2 * 0.8 * Math.max(0, remaining - 0.3)) + 0.05);
                double uRef = dir == 0 ? -target : target;
                double m = 7000 + h.rope().massPerMetreKg() * z;
                double torque = 2 * (m * Hoist.G + 500 * Math.signum(uRef) + mEff * 2 * (uRef - h.speedMs()));
                boolean stop = remaining < 0.35;
                h.stepFast(dt, stop ? m * Hoist.G * 2 : torque, stop);
                if (stop && h.speedMs() == 0) break;
            }
            assertThat(h.state()).isEqualTo(HoistState.HELD);
        }
        RopeState r = h.rope();
        assertThat(r.bendCycles()).isEqualTo(1);
        assertThat(r.tonneKm()).isCloseTo(4 * 0.2, within(0.01));
        // σ ≈ 7,2 т·g/(0,385·36² мм²) ≈ 141 МПа: N_f ≈ 2·10⁵·(83,3/40)^2,5·(354/141)² ≈ 7,9·10⁶ → D_k ≈ 2/N_f
        double sigma = 7180 * Hoist.G / r.metalAreaM2();
        double nf = Hoist.cyclesToFailure(2e5, 3000 / 36.0, 1770, sigma);
        assertThat(r.damage()).isCloseTo(2 / nf, within(0.3 * 2 / nf));
    }

    /** N_f = N₀((D/d)/40)^2,5(0,2R_r/σ)²: D/d = 80, σ = 0,2R_r → N₀·2^2,5. */
    @Test
    void fatigueCurve() {
        assertThat(Hoist.cyclesToFailure(2e5, 80, 1770, 0.2 * 1770e6)).isCloseTo(2e5 * Math.pow(2, 2.5), within(1.0));
        assertThat(Hoist.cyclesToFailure(2e5, 80, 1770, 0.1 * 1770e6))
                .isCloseTo(4 * 2e5 * Math.pow(2, 2.5), within(1.0));
    }

    /** Мокрый ствол: 0,002 D_k в сутки; D_k ≥ 0,7 — {@code ROPE_WEAR}. */
    @Test
    void wetShaftCorrodesRope() {
        Hoist h = new Hoist(spec(true), RopeState.fresh(36, 1770, RopeConstruction.R6X19_FC), HoistConfig.defaults());
        for (int i = 0; i < 24; i++) h.stepFast(3600, 0, true);
        assertThat(h.rope().damage()).isCloseTo(0.002, within(1e-9));
        assertThat(h.ropeWearAlarm()).isFalse();
        RopeState worn = new RopeState(36, 1770, RopeConstruction.R6X19_FC, 0.75, 0, 0, 0);
        assertThat(worn.worn()).isTrue();
        assertThat(worn.residualStrengthN()).isCloseTo(worn.breakingForceN() * (1 - 0.3 * 0.75), within(1.0));
        assertThat(worn.visibleBrokenWires()).isCloseTo(40 * 0.421875, within(1e-9));
    }

    /** Перегруз сверх остаточной прочности — обрыв; клеть садится на парашюты. */
    @Test
    void overloadBreaksRope() {
        HoistConfig d = HoistConfig.defaults();
        HoistConfig advisory = new HoistConfig(d.sfPeople(), d.sfMixed(), d.sfCargo(), d.ropeN0Cycles(),
                d.corrosionPerDay(), d.ropeItemLengthM(), HoistConfig.SafetyMode.ADVISORY);
        Hoist h = hoist(10, advisory);
        assertThat(h.load(HoistDuty.CARGO, 3200)).isEqualTo(Hoist.Verdict.UNSAFE_OPERATION);
        h.stepFast(0.01, 0, true);
        assertThat(h.state()).isEqualTo(HoistState.ROPE_BROKEN_CAUGHT);
        assertThat(h.rope().damage()).isEqualTo(1);
        h.stepFast(0.01, 1e6, false);
        assertThat(h.state()).isEqualTo(HoistState.ROPE_BROKEN_CAUGHT);
    }
}
