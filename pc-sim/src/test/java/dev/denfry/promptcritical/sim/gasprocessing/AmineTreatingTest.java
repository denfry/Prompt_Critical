package dev.denfry.promptcritical.sim.gasprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class AmineTreatingTest {

    /** T9.37: 1 млн нм³/сут, 2 % H₂S, Δα = 0,44, 45 % МДЭА → 21,51 м³/ч (±0,5 %). */
    @Test
    void circulationReference() {
        assertThat(AmineTreating.C_AMINE).isCloseTo(3.93, within(0.005));
        double kmolH = 1e6 / 24 * 0.02 / AmineTreating.NM3_PER_KMOL;
        assertThat(kmolH).isCloseTo(37.2, within(0.05));
        assertThat(AmineTreating.circulationM3h(kmolH, 0.44)).isCloseTo(21.51, within(21.51 * 0.005));
    }

    /** На расчётной циркуляции газ проходит норму, кислый газ богат H₂S; мало амина — проскок. */
    @Test
    void absorberCapacity() {
        double gas = 1e6 / 24, kmolH = gas * 0.02 / AmineTreating.NM3_PER_KMOL;
        double q = AmineTreating.circulationM3h(kmolH, 0.44) * 1.1;
        AmineTreating.Result ok = AmineTreating.treat(gas, 0.02, 0.01, q, 120);
        assertThat(ok.saturated()).isFalse();
        assertThat(ok.sweetH2sPpm()).isLessThan(AmineTreating.SPEC_PPM_GPA);
        assertThat(ok.sweetH2sMgNm3()).isLessThan(20);
        assertThat(ok.richLoading()).isBetween(0.40, 0.50);
        assertThat(ok.acidPurity()).isGreaterThan(0.9);
        AmineTreating.Result low = AmineTreating.treat(gas, 0.02, 0.01, q / 2, 120);
        assertThat(low.saturated()).isTrue();
        assertThat(low.sweetH2sPpm()).isGreaterThan(5000);
        assertThat(low.richLoading()).isCloseTo(AmineTreating.ALPHA_RICH_MAX, within(1e-9));
    }

    /** Ребойлер: 110 кг пара на м³ — α_lean 0,01, 140 — ≈ 0,006 (§9.10.3: 0,005–0,01); мало пара — газ не очищается до нормы. */
    @Test
    void reboilerSetsLeanLoading() {
        assertThat(AmineTreating.leanLoading(110)).isCloseTo(0.01, within(1e-12));
        assertThat(AmineTreating.leanLoading(140)).isBetween(0.005, 0.0065);
        double gas = 1e6 / 24;
        AmineTreating.Result starved = AmineTreating.treat(gas, 0.02, 0.01, 30, 40);
        assertThat(starved.sweetH2sPpm()).isGreaterThan(AmineTreating.SPEC_PPM_GPA);
        // пример §9.10.3: 21,5 м³/ч × 120 кг/м³ ≈ 2,6 т/ч пара ≈ 1,6 МВт
        assertThat(AmineTreating.treat(gas, 0.02, 0.01, 21.5, 120).reboilerW()).isCloseTo(1.58e6, within(0.05e6));
    }
}
