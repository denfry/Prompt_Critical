package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Уровень освобождения 17 §17.3.3: правило суммы долей по удельной активности. */
class ExemptionTest {
    @Test
    void caesiumIsFreeAtTenBecquerelsPerGram() {
        // 1 кг при 10 Бк/г — ровно на уровне
        assertThat(Exemption.fractionSum(Map.of(Emitter.CS137, 1e4), 1)).isCloseTo(1, within(1e-12));
        assertThat(Exemption.exempt(Map.of(Emitter.CS137, 1e4), 1)).isTrue();
        assertThat(Exemption.exempt(Map.of(Emitter.CS137, 1.1e4), 1)).isFalse();
    }

    @Test
    void fractionsAddUpAcrossNuclides() {
        // по 0,6 уровня Cs-137 и Am-241: каждый в отдельности свободен, вместе — нет
        Map<Emitter, Double> mix = Map.of(Emitter.CS137, 6e3, Emitter.AM241, 600.0);
        assertThat(Exemption.fractionSum(mix, 1)).isCloseTo(1.2, within(1e-12));
        assertThat(Exemption.exempt(mix, 1)).isFalse();
    }

    @Test
    void massDilutesAndMasslessSourcesNeverClear() {
        assertThat(Exemption.exempt(Map.of(Emitter.CO60, 1e6), 1000)).isFalse();
        assertThat(Exemption.exempt(Map.of(Emitter.CO60, 1e6), 1e5)).isTrue();
        assertThat(Exemption.exempt(Map.of(Emitter.CO60, 1.0), 0)).isFalse();
        assertThat(Exemption.exempt(Map.of(), 0)).isTrue();
    }
}
