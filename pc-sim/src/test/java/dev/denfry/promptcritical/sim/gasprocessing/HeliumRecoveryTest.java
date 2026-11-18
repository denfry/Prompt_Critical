package dev.denfry.promptcritical.sim.gasprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/** Гелиевая установка: концентрат 70–95 % после второй ступени, извлечение 90–98 %, без жидкого азота гелия нет. */
class HeliumRecoveryTest {
    @Test
    void cryogenicRecoveryMatchesPlantFigures() {
        HeliumRecovery h = new HeliumRecovery();
        h.step(10, 1e9);
        assertThat(h.vapour1()).isBetween(0.0005, 0.2);
        assertThat(h.crudePurity()).isBetween(0.70, 0.98);
        assertThat(h.recovery()).isBetween(0.88, 0.98);
        // гелий 0,055 % от 10 нм³/с — около 5 л/с
        assertThat(h.heliumNm3S()).isCloseTo(10 * HeliumRecovery.HE_IN_GAS * h.recovery(), within(1e-12));
        // материальный баланс по объёму: всё, что вошло, вышло гелием, азотом или в сеть
        assertThat(h.heliumNm3S() + h.nitrogenNm3S() + h.returnNm3S()).isCloseTo(10, within(1e-9));
        assertThat(h.ln2KgS()).isPositive();
    }

    @Test
    void warmerFirstStageLetsMoreMethaneIntoTheVapour() {
        HeliumRecovery cold = new HeliumRecovery(), warm = new HeliumRecovery();
        cold.setT1K(160);
        warm.setT1K(180);
        cold.step(10, 1e9);
        warm.step(10, 1e9);
        assertThat(warm.vapour1()).isGreaterThan(cold.vapour1());
        // тёплая первая ступень грузит вторую: азота нужно больше
        assertThat(warm.ln2KgS()).isGreaterThan(cold.ln2KgS());
    }

    @Test
    void noLiquidNitrogenNoHelium() {
        HeliumRecovery h = new HeliumRecovery();
        h.step(10, 0);
        assertThat(h.heliumNm3S()).isZero();
        assertThat(h.returnNm3S()).isCloseTo(10, within(1e-9));
    }
}
