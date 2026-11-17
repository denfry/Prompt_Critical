package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GasTurbineTest {
    private static final double W0 = 2 * Math.PI * 3000 / 60, P = 3e6;

    /** Брайтон при ISO: T₂ ≈ 540 К, T₄ ≈ 747 К, удельная работа ≈ 190 кДж/кг, КПД ≈ 28 %. */
    @Test
    void braytonCycleAtIso() {
        assertThat(GasTurbine.ratedExhaustK()).isCloseTo(747, within(2.0));
        assertThat(GasTurbine.specificWork(GasTurbine.ISO_K)).isBetween(180e3, 200e3);
        assertThat(GasTurbine.efficiency(GasTurbine.ISO_K)).isBetween(0.26, 0.30);
        assertThat(GasTurbine.ambientFactor(GasTurbine.ISO_K, GasTurbine.ISO_PA)).isCloseTo(1, within(1e-12));
    }

    /** Жара +35 °C отнимает 0,6–0,9 %/К мощности, мороз −10 °C добавляет; высота 1000 м — минус ≈ 11 %. */
    @Test
    void ambientAirSetsTheAvailablePower() {
        double hot = GasTurbine.ambientFactor(308.15, GasTurbine.ISO_PA);
        double cold = GasTurbine.ambientFactor(263.15, GasTurbine.ISO_PA);
        assertThat((1 - hot) / 20).isBetween(0.006, 0.012);
        assertThat(cold).isGreaterThan(1.1);
        assertThat(GasTurbine.ambientFactor(GasTurbine.ISO_K, 89_875)).isCloseTo(0.887, within(0.002));
    }

    private static GasTurbine started() {
        GasTurbine t = new GasTurbine(P, W0);
        t.setRun(true);
        for (int s = 0; s < 1200; s++) t.step(0.05, 0, GasTurbine.ISO_K, GasTurbine.ISO_PA, true);
        return t;
    }

    /** Пуск: пусковой двигатель до 25 %, зажигание, разгон до холостого хода 104 % (статизм 4 %). */
    @Test
    void startsLightsOffAndIdlesOnDroop() {
        GasTurbine t = started();
        assertThat(t.lit()).isTrue();
        assertThat(t.speedPu()).isCloseTo(1.04, within(0.005));
        assertThat(t.fuelPu()).isCloseTo(GasTurbine.NO_LOAD_FUEL, within(0.03));
    }

    /** Под 70 % нагрузки — обороты по статизму 1,04 − 0,04·0,7, мощность 2,1 МВт; топливо кончилось — гаснет. */
    @Test
    void carriesLoadOnDroopAndFlamesOutWithoutFuel() {
        GasTurbine t = started();
        double torque = 0.7 * P / W0;
        for (int s = 0; s < 1200; s++) t.step(0.05, torque, GasTurbine.ISO_K, GasTurbine.ISO_PA, true);
        assertThat(t.speedPu()).isCloseTo(1.04 - 0.04 * 0.7 * 1.0, within(0.01));
        assertThat(t.shaftPowerW()).isCloseTo(0.7 * P * t.speedPu(), within(0.03 * P));
        assertThat(t.heatInputW()).isGreaterThan(t.shaftPowerW() * 2.5);
        for (int s = 0; s < 400; s++) t.step(0.05, torque, GasTurbine.ISO_K, GasTurbine.ISO_PA, false);
        assertThat(t.lit()).isFalse();
        assertThat(t.speedPu()).isLessThan(0.5);
    }

    /** В жару номинальную нагрузку не вытянуть: топливо упирается в потолок, обороты проседают ниже полосы статизма. */
    @Test
    void hotDayCannotCarryRatedLoad() {
        GasTurbine t = started();
        double torque = 1.0 * P / W0;
        for (int s = 0; s < 1200; s++) t.step(0.05, torque, 313.15, GasTurbine.ISO_PA, true);
        assertThat(t.capacityW(313.15, GasTurbine.ISO_PA)).isLessThan(0.85 * P);
        assertThat(t.speedPu()).isLessThan(0.97);
    }

    @Test
    void overspeedTripLatches() {
        GasTurbine t = started();
        t.setSpeedPu(1.12);
        t.step(0.05, 0, GasTurbine.ISO_K, GasTurbine.ISO_PA, true);
        assertThat(t.tripped()).isTrue();
        assertThat(t.resetTrip()).isFalse();
        for (int s = 0; s < 2000; s++) t.step(0.05, 0, GasTurbine.ISO_K, GasTurbine.ISO_PA, true);
        assertThat(t.lit()).isFalse();
        assertThat(t.resetTrip()).isTrue();
    }
}
