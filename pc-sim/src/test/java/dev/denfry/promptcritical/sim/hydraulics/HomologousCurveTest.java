package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Гомологическая характеристика по умолчанию (01 §1.2.7). */
class HomologousCurveTest {
    private final HomologousCurve c = HomologousCurve.defaultCentrifugal();

    @Test
    void ratedPoint() {
        // 225° попадает между узлами 220° и 230°: погрешность линейной интерполяции
        assertThat(c.head(1, 1)).isCloseTo(1, within(0.01));
        assertThat(c.torque(1, 1)).isCloseTo(1, within(0.01));
    }

    @Test
    void similarityLaws() {
        // h ∝ ν² при постоянном v/ν
        assertThat(c.head(0.5, 0.5)).isCloseTo(0.25 * c.head(1, 1), within(1e-12));
        assertThat(c.torque(2, 2)).isCloseTo(4 * c.torque(1, 1), within(1e-12));
    }

    @Test
    void quadrants() {
        assertThat(c.head(1, 0)).isCloseTo(1.3, within(1e-9));        // закрытая задвижка
        assertThat(c.head(0, 1)).isNegative();                        // заторможенный ротор, прямой ток
        assertThat(c.head(0, -1)).isPositive();                       // заторможенный ротор, обратный ток
        assertThat(c.head(-1, 0)).isCloseTo(0.5, within(1e-9));       // обратное вращение всё ещё качает вперёд
        assertThat(c.torque(-1, 0)).isNegative();                     // и тормозит обратное вращение
        assertThat(c.torque(0, 1)).isNegative();                      // прямой ток раскручивает стоящий ротор
        assertThat(c.head(0, 0)).isZero();
    }
}
