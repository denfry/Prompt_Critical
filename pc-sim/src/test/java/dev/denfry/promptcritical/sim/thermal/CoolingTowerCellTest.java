package dev.denfry.promptcritical.sim.thermal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.environment.Environment;
import org.junit.jupiter.api.Test;

class CoolingTowerCellTest {
    /** Stull 2011, контрольная точка автора: 20 °C и 50 % — 13,7 °C. */
    @Test
    void wetBulbFollowsStull() {
        assertThat(Environment.wetBulbC(20, 50)).isCloseTo(13.7, within(0.1));
        assertThat(Environment.wetBulbC(30, 99)).isCloseTo(29.9, within(0.3));
        assertThat(Environment.wetBulbC(30, 20)).isLessThan(Environment.wetBulbC(30, 60));
    }

    /** Летний день 30 °C и 40 %: мокрый 20 °C, вода с градирни ≈ 25 °C; без вентилятора — заметно теплее. */
    @Test
    void coldWaterSitsAboveTheWetBulb() {
        double wb = Environment.wetBulbC(30, 40);
        double fan = CoolingTowerCell.coldWaterC(30, 40, CoolingTowerCell.DESIGN_HEAT_W, true);
        assertThat(fan - wb).isCloseTo(5, within(1e-9));
        assertThat(CoolingTowerCell.coldWaterC(30, 40, CoolingTowerCell.DESIGN_HEAT_W, false) - wb).isCloseTo(15, within(1e-9));
        assertThat(CoolingTowerCell.coldWaterC(30, 40, 2 * CoolingTowerCell.DESIGN_HEAT_W, true)).isGreaterThan(fan);
        assertThat(CoolingTowerCell.evaporationKgS(5e6)).isCloseTo(2.08, within(0.01));
        assertThat(CoolingTowerCell.icing(-10, true)).isTrue();
        assertThat(CoolingTowerCell.icing(-10, false)).isFalse();
    }
}
