package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.radiation.AirborneDose;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import org.junit.jupiter.api.Test;

class PpeTest {

    /** Ёмкость фильтра B класса 1 — хлор 1000 ppm при 30 л/мин 20 мин: 0,6 л = 0,0245 моль. */
    @Test
    void filterCapacityFromEn14387() {
        assertThat(FilterType.B.capacity()).isCloseTo(0.6 / 24.45, within(1e-6));
        assertThat(FilterType.A.capacity()).isCloseTo(2.1 / 24.45, within(1e-6));
        assertThat(FilterType.CO.capacity()).isCloseTo(0.0025 * 30 * 90 / 24.45, within(1e-6));
    }

    /** Маска с фильтром B ослабляет хлор в 50 раз и выдерживает 1000 ppm ровно 20 мин, затем проскок. */
    @Test
    void maskProtectsUntilBreakthrough() {
        GasMask m = new GasMask(FilterType.B, 0);
        assertThat(m.inhale(1, Contaminant.INORGANIC_GAS, 1000, 0.209)).isCloseTo(20, within(1e-9));
        for (int s = 1; s < 20 * 60 - 1; s++) m.inhale(1, Contaminant.INORGANIC_GAS, 1000, 0.209);
        assertThat(m.spent()).isFalse();
        m.inhale(2, Contaminant.INORGANIC_GAS, 1000, 0.209);
        assertThat(m.spent()).isTrue();
        assertThat(m.inhale(1, Contaminant.INORGANIC_GAS, 1000, 0.209)).isEqualTo(1000);
    }

    /** Не тот фильтр, нехватка кислорода и удушающий газ — защиты нет и фильтр не расходуется. */
    @Test
    void maskDoesNotHelpWithoutOxygenOrWrongFilter() {
        GasMask m = new GasMask(FilterType.B, 0);
        assertThat(m.inhale(60, Contaminant.CARBON_MONOXIDE, 500, 0.209)).isEqualTo(500);
        assertThat(m.inhale(60, Contaminant.INORGANIC_GAS, 500, 0.16)).isEqualTo(500);
        assertThat(m.inhale(60, Contaminant.ASPHYXIANT, 1e5, 0.209)).isEqualTo(1e5);
        assertThat(m.loaded()).isZero();
        assertThat(new GasMask(null, 0).inhale(1, Contaminant.INORGANIC_GAS, 100, 0.209)).isEqualTo(100);
    }

    /** Аппарат: 2000 л при 40 л/мин — 50 мин, свисток на 20 % остатка, затем пустой. */
    @Test
    void scbaLastsFiftyMinutes() {
        Scba s = new Scba(Scba.AIR_L);
        assertThat(s.minutesLeft()).isCloseTo(50, within(1e-9));
        for (int i = 0; i < 39 * 60; i++) assertThat(s.breathe(1)).isTrue();
        assertThat(s.whistle()).isFalse();
        for (int i = 0; i < 2 * 60; i++) s.breathe(1);
        assertThat(s.whistle()).isTrue();
        for (int i = 0; i < 9 * 60; i++) s.breathe(1);
        assertThat(s.empty()).isTrue();
        assertThat(s.breathe(1)).isFalse();
        assertThat(Scba.inhaled(1e4)).isCloseTo(1, within(1e-12));
    }

    /** Фильтр P или аппарат снимают ингаляционную часть дозы облака, погружение остаётся. */
    @Test
    void particleFilterCutsInhalationDoseOnly() {
        double open = AirborneDose.svH(Emitter.I131, 1e4);
        double masked = AirborneDose.svH(Emitter.I131, 1e4, 1 / Ppe.GAS_MASK_FACTOR);
        double submersion = AirborneDose.svH(Emitter.I131, 1e4, 0);
        assertThat(masked).isLessThan(open / 10).isGreaterThan(submersion);
    }
}
