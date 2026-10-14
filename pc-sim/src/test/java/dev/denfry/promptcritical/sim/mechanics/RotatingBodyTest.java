package dev.denfry.promptcritical.sim.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Вращающееся тело 03a §3a.2. */
class RotatingBodyTest {
    private static final double DT = 0.05, J = 50, W_NOM = 100, P = 50e3;
    private static final RotatingBody.Spec SPEC = new RotatingBody.Spec(J, 5, 0.05);

    /** Разгон от вала Create: без нагрузки за ≈ 5 t_c доходит до ω_cmd; момент не выше пускового 2·P/ω_nom. */
    @Test
    void spinsUpToCommandWithinStallLimit() {
        RotatingBody b = new RotatingBody(SPEC, 0);
        RotatingBody.CreateDrive d = RotatingBody.CreateDrive.of(P, W_NOM, CouplingType.FREEWHEEL);
        double maxTorque = 0;
        for (int k = 0; k < 400; k++) {
            b.stepDriven(DT, W_NOM, d, false, 0);
            maxTorque = Math.max(maxTorque, b.driveTorque());
        }
        assertThat(maxTorque).isLessThanOrEqualTo(d.stallTorque() + 1e-9);
        // установившееся скольжение: K_c(ω_cmd − ω) = τ_fr(ω)
        double kc = J / d.slipTime();
        double expected = (kc * W_NOM - 5) / (kc + 0.05);
        assertThat(b.omega()).isCloseTo(expected, within(1e-3));
        assertThat(b.shaftPower()).isCloseTo(b.driveTorque() * b.omega(), within(1e-9));
    }

    /** Нагрузка выше номинала: скольжение растёт, пока момент не упрётся в τ_max = P/ω, и вал тормозится. */
    @Test
    void powerLimitsTorqueAtSpeed() {
        RotatingBody b = new RotatingBody(SPEC, W_NOM);
        RotatingBody.CreateDrive d = RotatingBody.CreateDrive.of(P, W_NOM, CouplingType.FREEWHEEL);
        double prev = W_NOM;
        for (int k = 0; k < 20; k++) {
            prev = b.omega();
            b.stepDriven(DT, W_NOM, d, false, 2 * P / W_NOM);
        }
        assertThat(b.driveTorque()).isCloseTo(P / prev, within(1e-6));
        assertThat(b.omega()).isLessThan(prev).isLessThan(0.97 * W_NOM);
    }

    /** Обгонная муфта: выше ω_cmd привод не тормозит — выбег; жёсткая муфта тормозит до ω_cmd. */
    @Test
    void couplingTypes() {
        RotatingBody free = new RotatingBody(SPEC, W_NOM);
        RotatingBody rigid = new RotatingBody(SPEC, W_NOM);
        RotatingBody clutchOff = new RotatingBody(SPEC, W_NOM);
        for (int k = 0; k < 100; k++) {
            free.stepDriven(DT, 0.5 * W_NOM, RotatingBody.CreateDrive.of(P, W_NOM, CouplingType.FREEWHEEL), false, 0);
            rigid.stepDriven(DT, 0.5 * W_NOM, RotatingBody.CreateDrive.of(P, W_NOM, CouplingType.RIGID), false, 0);
            clutchOff.stepDriven(DT, W_NOM, RotatingBody.CreateDrive.of(P, W_NOM, CouplingType.FRICTION_CLUTCH),
                    false, 0);
        }
        assertThat(free.driveTorque()).isZero();
        assertThat(free.omega()).isGreaterThan(0.8 * W_NOM);
        assertThat(rigid.omega()).isCloseTo(0.5 * W_NOM, within(1.0));
        assertThat(clutchOff.driveTorque()).isZero();
        assertThat(clutchOff.omega()).isLessThan(W_NOM);
    }

    /** Нулевой вал (overstress): выбег на трении до остановки, без перехода через ноль. */
    @Test
    void coastDownStopsWithoutReversal() {
        RotatingBody b = new RotatingBody(SPEC, W_NOM);
        RotatingBody.CreateDrive d = RotatingBody.CreateDrive.of(P, W_NOM, CouplingType.FREEWHEEL);
        double prev = W_NOM;
        for (int k = 0; k < 20_000; k++) {
            b.stepDriven(DT, 0, d, false, 0);
            assertThat(b.omega()).isBetween(0.0, prev);
            prev = b.omega();
        }
        assertThat(b.omega()).isZero();
        // в покое сухое трение держит вал под малым моментом
        b.stepTorque(DT, 4, 0);
        assertThat(b.omega()).isZero();
    }

    /** Объявляемая мощность не ниже 2 % номинала (§3a.2.4). */
    @Test
    void declaredPowerFloor() {
        RotatingBody b = new RotatingBody(SPEC, 0);
        RotatingBody.CreateDrive d = RotatingBody.CreateDrive.of(P, W_NOM, CouplingType.FREEWHEEL);
        assertThat(b.declaredPower(d)).isEqualTo(0.02 * P);
    }
}
