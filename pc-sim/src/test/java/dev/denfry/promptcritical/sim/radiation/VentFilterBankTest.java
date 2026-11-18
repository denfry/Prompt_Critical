package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class VentFilterBankTest {

    /** 03a §3a.9.3: HEPA 99,97 % — DF 3333; уголь по йоду — DF в пределах 100–1000, во влажном воздухе хуже. */
    @Test
    void decontaminationFactors() {
        VentFilterBank b = new VentFilterBank(1, 1);
        assertThat(b.df(Emitter.CS137, false)).isCloseTo(3333.3, within(0.5));
        double dry = b.df(Emitter.I131, false), wet = b.df(Emitter.I131, true);
        assertThat(dry).isBetween(100.0, 1000.0);
        assertThat(wet).isLessThan(dry).isGreaterThan(10.0);
        assertThat(new VentFilterBank(0, 2).df(Emitter.I131, false)).isCloseTo(dry * dry, within(1e-6));
        assertThat(new VentFilterBank(0, 0).df(Emitter.CS137, false)).isEqualTo(1);
    }

    /** Задержанное копится в фильтре; йод распадается за 8 суток наполовину. */
    @Test
    void retainedActivityAccumulatesAndDecays() {
        VentFilterBank b = new VentFilterBank(1, 1);
        double out = b.pass(Emitter.CS137, 1e6, 10, false);
        assertThat(out).isCloseTo(1e6 / b.df(Emitter.CS137, false), within(1e-6));
        assertThat(b.retainedCs137Bq()).isCloseTo((1e6 - out) * 10, within(1e-3));
        b.pass(Emitter.I131, 1e6, 1, false);
        double i = b.retainedI131Bq();
        b.decay(8.02 * 86400);
        assertThat(b.retainedI131Bq()).isCloseTo(i / 2, within(i * 1e-9));
    }
}
