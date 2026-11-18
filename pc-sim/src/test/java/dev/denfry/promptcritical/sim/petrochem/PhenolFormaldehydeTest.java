package dev.denfry.promptcritical.sim.petrochem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PhenolFormaldehydeTest {

    /** Ullmann's: серебряный процесс 86–90 % мольного выхода, Formox 88–92 %; теоретически 0,937 кг/кг. */
    @Test
    void formaldehydeFromMethanol() {
        assertThat(PhenolFormaldehyde.formaldehydePerMethanol(PhenolFormaldehyde.Catalyst.SILVER)).isCloseTo(0.834, within(0.01));
        assertThat(PhenolFormaldehyde.formaldehydePerMethanol(PhenolFormaldehyde.Catalyst.FE_MO))
                .isGreaterThan(PhenolFormaldehyde.formaldehydePerMethanol(PhenolFormaldehyde.Catalyst.SILVER))
                .isLessThan(30.03 / 32.04);
    }

    /** Новолак F:P = 0,8: DPn = 5, M ≈ 5·94,11 + 4·12,01 ≈ 518 (Knop, Pilato: 500–900); масса по стехиометрии. */
    @Test
    void novolacStoichiometry() {
        PhenolFormaldehyde.Resin n = PhenolFormaldehyde.condense(0.8, false);
        assertThat(n.type()).isEqualTo(PhenolFormaldehyde.ResinType.NOVOLAC);
        assertThat(n.dpn()).isCloseTo(5, within(1e-9));
        assertThat(n.resinKg()).isCloseTo((94.11 + 0.8 * (30.03 - 18.02)) / 94.11, within(1e-9));
        assertThat(n.resinKg() + n.waterKg()).isCloseTo(1 + n.formaldehydeKg(), within(1e-12));
        double mn = n.dpn() * 94.11 + (n.dpn() - 1) * 12.01;
        assertThat(mn).isBetween(500.0, 900.0);
    }

    /** Резол — щелочь и избыток формальдегида; при кислоте и r ≥ 1 всё равно не самоотверждается. */
    @Test
    void resolNeedsBaseAndExcessFormaldehyde() {
        assertThat(PhenolFormaldehyde.condense(1.5, true).type()).isEqualTo(PhenolFormaldehyde.ResinType.RESOL);
        assertThat(PhenolFormaldehyde.condense(1.5, false).type()).isEqualTo(PhenolFormaldehyde.ResinType.NOVOLAC);
        assertThat(PhenolFormaldehyde.condense(0.8, true).type()).isEqualTo(PhenolFormaldehyde.ResinType.NOVOLAC);
        assertThat(PhenolFormaldehyde.condense(1.5, true).resinKg()).isGreaterThan(1.2);
    }
}
