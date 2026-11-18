package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatrolRoundTest {

    /** Круг по порядку засчитывается; пропуск метки не засчитывает круг. */
    @Test
    void roundInOrder() {
        PatrolRound r = new PatrolRound(3);
        assertThat(r.visit(1, 10)).isEqualTo(PatrolRound.Result.OUT_OF_ORDER);
        assertThat(r.visit(0, 10)).isEqualTo(PatrolRound.Result.STARTED);
        assertThat(r.visit(2, 20)).isEqualTo(PatrolRound.Result.OUT_OF_ORDER);
        assertThat(r.visit(1, 30)).isEqualTo(PatrolRound.Result.NEXT);
        assertThat(r.visit(2, 40)).isEqualTo(PatrolRound.Result.COMPLETE);
        assertThat(r.lastComplete()).isEqualTo(40);
        assertThat(r.next()).isZero();
        assertThat(r.visit(5, 50)).isEqualTo(PatrolRound.Result.UNKNOWN);
    }

    /** Просрочка — больше часа реального времени (72 000 тиков) от последнего круга. */
    @Test
    void overdueAfterAnHour() {
        PatrolRound r = new PatrolRound(1);
        assertThat(r.visit(0, 1000)).isEqualTo(PatrolRound.Result.COMPLETE);
        assertThat(r.overdue(1000 + 72_000, 0)).isFalse();
        assertThat(r.overdue(1000 + 72_001, 0)).isTrue();
        assertThat(new PatrolRound(2).overdue(72_001, 0)).isTrue();
    }
}
