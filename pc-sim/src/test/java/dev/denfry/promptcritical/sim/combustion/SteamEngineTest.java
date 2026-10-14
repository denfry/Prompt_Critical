package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import org.junit.jupiter.api.Test;

/** Паровая машина I0 (07 §7.9.7). */
class SteamEngineTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final double DT = 0.05, P1 = 1.1e6, PB = 0.11e6;
    private static final double RHO1 = W.saturationP(P1).rhoG();

    /** T7.14: D 0,4 м, ход 0,6 м, ε 0,25, p₁ 1,1 МПа, p_b 0,11 МПа, 2 об/с, k_d 0,8 → P_i 131,8 кВт, пар 0,53 кг/с. */
    @Test
    void t7_14_indicatedPowerAndSteam() {
        SteamEngine e = new SteamEngine(0.4, 0.6, 1, 0.8, 0.25);
        assertThat(e.sweptVolume()).isCloseTo(0.0754, within(0.0001));
        assertThat(e.meanIndicatedPressure(P1, PB, 0.25)).isCloseTo(0.437e6, within(0.001e6));
        assertThat(e.indicatedPowerW(P1, PB, 0.25, 2)).isCloseTo(131.8e3, within(0.01 * 131.8e3));
        assertThat(e.steamFlowKgS(RHO1, 0.25, 2)).isCloseTo(0.53, within(0.01 * 0.53));
        assertThat(e.meanIndicatedTorqueNm(P1, PB, 0.25) * 2 * Math.PI * 2)
                .isCloseTo(e.indicatedPowerW(P1, PB, 0.25, 2), within(1e-6));
    }

    /** Форма момента по углу коленвала: среднее за оборот — 1 при любом числе цилиндров. */
    @Test
    void torqueShapeAveragesToOne() {
        for (int z = 1; z <= 4; z++) {
            SteamEngine e = new SteamEngine(0.4, 0.6, z, 0.8, 0.25);
            int n = 100_000;
            double s = 0;
            for (int k = 0; k < n; k++) s += e.torqueShape(2 * Math.PI * (k + 0.5) / n);
            assertThat(s / n).as("z = %d", z).isCloseTo(1, within(1e-6));
        }
        assertThat(new SteamEngine(0.4, 0.6, 1, 0.8, 0.25).torqueShape(0)).isZero();
        assertThat(new SteamEngine(0.4, 0.6, 2, 0.8, 0.25).torqueShape(0)).isGreaterThan(0.7);
    }

    /** С маховиком в номинальной точке: 120 об/мин, на валу 0,9·P_i ≈ 119 кВт, удельный расход ≈ 16 кг/(кВт·ч). */
    @Test
    void nominalPointWithFlywheel() {
        SteamEngineUnit u = new SteamEngineUnit(SteamEngineUnit.Spec.reference(1, SteamEngineUnit.FLYWHEEL_INERTIA),
                FlyballGovernor.withDashpot());
        double load = 0.9 * u.engine().meanIndicatedTorqueNm(P1, PB, 0.25);
        u.shaft().setOmega(u.nominalOmega());
        for (int k = 0; k < 1200; k++) u.step(DT, P1, RHO1, PB, load);
        double rev = 0, shaft = 0, steam = 0, ind = 0;
        for (int k = 0; k < 200; k++) {
            u.step(DT, P1, RHO1, PB, load);
            rev += u.revPerS() / 200;
            shaft += u.shaftPowerW() / 200;
            steam += u.steamFlowKgS() / 200;
            ind += u.indicatedPowerW() / 200;
        }
        assertThat(rev).isCloseTo(2.0, within(0.01));
        assertThat(ind).isCloseTo(131.8e3, within(0.015 * 131.8e3));
        assertThat(shaft).isCloseTo(118.6e3, within(0.015 * 118.6e3));
        assertThat(steam).isCloseTo(0.53, within(0.015 * 0.53));
        assertThat(steam * 3600 / (shaft / 1000)).as("кг/(кВт·ч)").isBetween(15.5, 16.6);
    }

    /** Без маховика обороты одноцилиндровой машины пульсируют ≈ ±10 %, с маховиком — меньше ±1 %. */
    @Test
    void flywheelSmoothsTorquePulsation() {
        double bare = pulsation(1, SteamEngineUnit.BARE_INERTIA);
        double flywheel = pulsation(1, SteamEngineUnit.FLYWHEEL_INERTIA);
        double twin = pulsation(2, SteamEngineUnit.BARE_INERTIA);
        assertThat(bare).as("(max − min)/mean без маховика").isBetween(0.14, 0.28);
        assertThat(flywheel).isLessThan(0.02);
        assertThat(twin).as("два кривошипа под 90° ровнее").isLessThan(bare / 3);
    }

    private static double pulsation(int cylinders, double inertia) {
        SteamEngineUnit u = new SteamEngineUnit(SteamEngineUnit.Spec.reference(cylinders, inertia),
                FlyballGovernor.withDashpot());
        double load = 0.9 * u.engine().meanIndicatedTorqueNm(P1, PB, 0.25);
        u.shaft().setOmega(u.nominalOmega());
        for (int k = 0; k < 1200; k++) u.step(DT, P1, RHO1, PB, load);
        double lo = Double.MAX_VALUE, hi = 0, mean = 0;
        for (int k = 0; k < 200; k++) {
            u.step(DT, P1, RHO1, PB, load);
            lo = Math.min(lo, u.minOmegaLastTick());
            hi = Math.max(hi, u.maxOmegaLastTick());
            mean += u.omega() / 200;
        }
        return (hi - lo) / mean;
    }

    /** Одноцилиндровая машина в мёртвой точке не трогается; два кривошипа трогаются из любого положения. */
    @Test
    void deadCentreNeedsBarring() {
        SteamEngineUnit single = new SteamEngineUnit(SteamEngineUnit.Spec.reference(1, 4000), FlyballGovernor.bare());
        single.setCrankAngle(0);
        for (int k = 0; k < 100; k++) single.step(DT, P1, RHO1, PB, 0);
        assertThat(single.omega()).isZero();
        single.setCrankAngle(Math.PI / 2); // провернули ломом
        for (int k = 0; k < 100; k++) single.step(DT, P1, RHO1, PB, 0);
        assertThat(single.omega()).isGreaterThan(1);

        SteamEngineUnit twin = new SteamEngineUnit(SteamEngineUnit.Spec.reference(2, 4000), FlyballGovernor.bare());
        for (int k = 0; k < 100; k++) twin.step(DT, P1, RHO1, PB, 0);
        assertThat(twin.omega()).isGreaterThan(1);
    }

    /** Без пара машина выбегает на трении и останавливается, не проворачиваясь назад. */
    @Test
    void coastsDownWithoutSteam() {
        SteamEngineUnit u = new SteamEngineUnit(SteamEngineUnit.Spec.reference(1, 4000), FlyballGovernor.bare());
        u.shaft().setOmega(u.nominalOmega());
        double prev = u.omega();
        for (int k = 0; k < 20 * 600; k++) {
            u.step(DT, PB, 0.6, PB, 0);
            assertThat(u.omega()).isLessThanOrEqualTo(prev).isGreaterThanOrEqualTo(0);
            prev = u.omega();
        }
        assertThat(u.omega()).isZero();
    }
}
