package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HazardCodesTest {

    /** 13 §13.8.2: бензин — ООН 1203, Кемлер 33, ромб 1-3-0; хлор — 268 и OX; неизвестный продукт — нет маркировки. */
    @Test
    void productTable() {
        assertThat(HazardCodes.of("gasoline").un()).isEqualTo(1203);
        assertThat(HazardCodes.of("gasoline").kemler()).isEqualTo("33");
        assertThat(HazardCodes.of("gasoline").diamond()).isEqualTo("1-3-0");
        assertThat(HazardCodes.of("chlorine").diamond()).isEqualTo("4-0-0-OX");
        assertThat(HazardCodes.of("hydrogen_sulfide").kemler()).isEqualTo("263");
        assertThat(HazardCodes.of("water")).isNull();
    }
}
