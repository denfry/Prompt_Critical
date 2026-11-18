package dev.denfry.promptcritical.sim.acoustics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Шум машин и нагрузка на слух: сложение уровней, расхождение, пример 04a §4a.4.8, доза по ISO 9612 / NIOSH. */
class NoiseTest {
    @Test
    void levelsAddByEnergy() {
        assertThat(SoundLevel.sum(80, 80)).isCloseTo(83.01, within(0.01));
        assertThat(SoundLevel.sum(90, 80)).isCloseTo(90.41, within(0.01));
        assertThat(SoundLevel.sum(80, 80, 80, 80, 80, 80, 80, 80, 80, 80)).isCloseTo(90, within(1e-9));
        assertThat(SoundLevel.sum()).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void distanceAndWalls() {
        // 04a §4a.4.8: 1 МВт ⇒ 100 дБ, у слушателя в 2 м 100 − 6 − 11 = 83 дБ
        double lw = SoundLevel.machineLw(1e6);
        assertThat(lw).isCloseTo(100, within(1e-9));
        assertThat(SoundLevel.atDistance(lw, 2, 0)).isCloseTo(83.0, within(0.05));
        // удвоение расстояния — −6 дБ
        assertThat(SoundLevel.atDistance(lw, 4, 0) - SoundLevel.atDistance(lw, 8, 0)).isCloseTo(6.02, within(0.01));
        // ближе метра — как на метре; стены — не больше 45 дБ
        assertThat(SoundLevel.atDistance(lw, 0.2, 0)).isEqualTo(SoundLevel.atDistance(lw, 1, 0));
        assertThat(SoundLevel.atDistance(lw, 2, 3 * BarrierMaterial.CONCRETE.tlDb))
                .isCloseTo(SoundLevel.atDistance(lw, 2, 0) - 45, within(1e-9));
    }

    @Test
    void badBearingIsHeardOnlyFromStageThree() {
        double run = SoundLevel.machineLw(1e5);
        // стадии 1–2: слой на 20 дБ ниже основного, общий растёт на 0,04 дБ — ухом не слышно
        assertThat(SoundLevel.machineLw(1e5, new RollingBearing(1, 0.85)) - run).isLessThan(0.05);
        // конец стадии 4: слой на 15 дБ выше основного
        assertThat(SoundLevel.machineLw(1e5, new RollingBearing(1, 1)) - run).isCloseTo(15.1, within(0.1));
    }

    @Test
    void doseFollowsEqualEnergyRule() {
        assertThat(NoiseExposure.allowedSeconds(85)).isCloseTo(8 * 3600, within(1e-6));
        assertThat(NoiseExposure.allowedSeconds(88)).isCloseTo(4 * 3600, within(1e-6));
        assertThat(NoiseExposure.allowedSeconds(100)).isCloseTo(15 * 60, within(1e-6));
        // 4 ч при 88 дБ(А) — полная доза, L_EX,8h = 85
        double d = NoiseExposure.step(0, 4 * 3600, 88, 0);
        assertThat(d).isCloseTo(1, within(1e-9));
        assertThat(NoiseExposure.lex8h(d)).isCloseTo(85, within(1e-9));
        // 8 ч при 82 дБ(А) — половина дозы, L_EX,8h = 85 − 10·lg 2 = 81,99 (шаг 3 дБ — округлённые 3,01)
        assertThat(NoiseExposure.step(0, 8 * 3600, 82, 0)).isCloseTo(0.5, within(1e-12));
        assertThat(NoiseExposure.lex8h(0.5)).isCloseTo(82, within(0.02));
        // ниже 80 дБ(А) не копится; в наушниках 100 дБ(А) превращаются в 75
        assertThat(NoiseExposure.doseRate(79.9)).isZero();
        assertThat(NoiseExposure.doseRate(NoiseExposure.atEar(100, true))).isZero();
        // спад: за период полураспада — вдвое
        assertThat(NoiseExposure.step(1, 600, 0, 600)).isCloseTo(0.5, within(1e-12));
    }
}
