package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DesaltingTest {
    private static final double T_120C = 393.15;

    /** T9.43: 200 мг/л, w_in 0,005, r_w 0,06, η 0,8, w_res 0,002 — 7,55 мг/л за ступень, 0,30 за две (±1 %). */
    @Test
    void referenceStagesOfGdd() {
        double s1 = Desalting.stageSalt(200, 0.005, 0.06, 0.8, 0.002);
        double s2 = Desalting.stageSalt(s1, 0.002, 0.06, 0.8, 0.002);
        assertThat(s1).isCloseTo(7.55, within(7.55 * 0.01));
        assertThat(s2).isCloseTo(0.30, within(0.30 * 0.01));
    }

    /** Номинальный режим даёт остаточную воду примера §9.7.3 и соль ниже нормы НПЗ 5 мг/л. */
    @Test
    void nominalOperationMeetsRefinerySpec() {
        Desalting.Result r = Desalting.twoStage(200, 0.005, 0.06, 0.10, Desalting.FIELD_KV_CM, T_120C);
        assertThat(Desalting.residualWater(0.065, 0.10, Desalting.FIELD_KV_CM, T_120C)).isCloseTo(0.002, within(2e-4));
        assertThat(r.saltOut()).isLessThan(Desalting.REFINERY_SALT_MAX_MG_L);
        assertThat(r.stage1Salt()).isBetween(6.0, 9.0);
        assertThat(r.washWater()).isCloseTo(0.12, within(1e-12));
        // вода сохраняется: вход + промывка = остаток в нефти + сброс
        assertThat(r.waterOut() + r.brine()).isCloseTo(0.005 + 0.12, within(1e-12));
    }

    /** Без напряжения на электродах капли не сливаются — соль почти не уходит; холодная нефть отстаивается хуже. */
    @Test
    void fieldAndHeatMatter() {
        double on = Desalting.twoStage(200, 0.005, 0.06, 0.10, Desalting.FIELD_KV_CM, T_120C).saltOut();
        double off = Desalting.twoStage(200, 0.005, 0.06, 0.10, 0, T_120C).saltOut();
        double cold = Desalting.twoStage(200, 0.005, 0.06, 0.10, Desalting.FIELD_KV_CM, 333.15).saltOut();
        assertThat(off).isGreaterThan(100);
        assertThat(cold).isGreaterThan(10 * on);
        assertThat(Desalting.viscosity(T_120C)).isCloseTo(1.5e-3, within(0.05e-3));
    }

    /** Смесительный клапан: η 0,5–0,9 в диапазоне 0,05–0,15 МПа, оптимум перепада внутри диапазона. */
    @Test
    void mixingValveHasAnOptimum() {
        assertThat(Desalting.mixingEfficiency(0.05)).isBetween(0.5, 0.56);
        assertThat(Desalting.mixingEfficiency(0.15)).isCloseTo(0.9, within(0.005));
        double low = Desalting.twoStage(200, 0.005, 0.06, 0.05, 2, T_120C).saltOut();
        double mid = Desalting.twoStage(200, 0.005, 0.06, 0.10, 2, T_120C).saltOut();
        double high = Desalting.twoStage(200, 0.005, 0.06, 0.15, 2, T_120C).saltOut();
        assertThat(mid).isLessThan(low).isLessThan(high * 1.3);
        assertThat(mid).isLessThan(high);
    }
}
