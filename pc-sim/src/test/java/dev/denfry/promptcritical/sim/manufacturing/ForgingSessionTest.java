package dev.denfry.promptcritical.sim.manufacturing;

import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.bar45;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import org.junit.jupiter.api.Test;

class ForgingSessionTest {
    @Test
    void handForgedShaftFromFireToPassport() {
        var s = ForgingSession.start(bar45(0.045, 293.15)).inFire(0.6, 600);
        int strikes = 0;
        for (int i = 0; i < 200 && s.piece().aM() > 0.040; i++) {
            if (s.piece().tempK() < Forging.range(SteelFamily.MEDIUM_CARBON).lowerK() + 50) {
                s = s.inFire(0.6, 120);
            } else {
                s = s.strike(Forging.Op.DRAW);
                strikes++;
            }
        }
        assertThat(s.piece().aM()).isLessThanOrEqualTo(0.040);
        assertThat(strikes).isBetween(25, 60);
        s = s.inFire(0.4, 300).quench(Quenching.Medium.WATER).temper(473.15, 3600);
        PartSpec p = s.toPart("shaft");
        assertThat(p.route()).isEqualTo(PartRoute.FORGED);
        assertThat(p.heatTreat()).isEqualTo(HeatTreatState.QUENCHED_TEMPERED);
        assertThat(p.passesGate()).isTrue();
        assertThat(s.clockS()).isPositive();
    }

    @Test
    void reheatingAboveAc3ClearsHardening() {
        var normalized = bar45(0.01, 293.15).grade().withState(HeatTreatState.NORMALIZED);
        var s = ForgingSession.start(bar45(0.01, 293.15)).inFire(0.6, 600).quench(Quenching.Medium.WATER);
        assertThat(s.piece().grade().state()).isEqualTo(HeatTreatState.QUENCHED);
        assertThat(s.piece().hardnessHrc()).isGreaterThan(50);
        s = s.inFire(0.6, 600).quench(Quenching.Medium.AIR);
        assertThat(s.piece().grade().state()).isEqualTo(HeatTreatState.NORMALIZED);
        assertThat(s.piece().hardnessHrc()).isEqualTo(normalized.hardnessHrc());
    }

    @Test
    void sameDecisionsGiveTheSameResult() {
        var a = ForgingSession.start(bar45(0.04, 293.15)).inFire(0.7, 500).strike(Forging.Op.DRAW)
                .quench(Quenching.Medium.OIL);
        var b = ForgingSession.start(bar45(0.04, 293.15)).inFire(0.7, 500).strike(Forging.Op.DRAW)
                .quench(Quenching.Medium.OIL);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void strikeFromTheFireCoolsDuringTransfer() {
        var hot = ForgingSession.start(bar45(0.04, 293.15)).inFire(0.7, 900);
        var struck = hot.strike(Forging.Op.BEND);
        assertThat(struck.piece().tempK()).isLessThan(hot.piece().tempK());
        assertThat(struck.clockS()).isEqualTo(hot.clockS() + ForgingSession.TRANSFER_S + Forging.HAND_SERIES_S);
        assertThat(struck.place()).isEqualTo(ForgingSession.Place.ANVIL);
    }

    @Test
    void strikeOnTheAnvilHasNoTransferAndQuenchClockFollowsMedium() {
        var s = ForgingSession.start(bar45(0.04, 1273.15));
        assertThat(s.strike(Forging.Op.DRAW).clockS()).isEqualTo(Forging.HAND_SERIES_S);
        assertThat(s.quench(Quenching.Medium.WATER).clockS()).isZero();
        assertThat(s.quench(Quenching.Medium.AIR).clockS()).isEqualTo(Quenching.AIR_COOL_S);
        assertThat(s.quench(Quenching.Medium.WATER).place()).isEqualTo(ForgingSession.Place.COOLED);
    }

    @Test
    void overheatingReportsBurntOnceAndLastEventsAreStepLocal() {
        var s = ForgingSession.start(bar45(0.04, 293.15)).inFire(1.0, 3000);
        assertThat(s.piece().severity(PartDefect.BURNT)).isPositive();
        assertThat(s.lastEvents()).containsExactly(ForgeEvent.BURNT);
        assertThat(s.inFire(1.0, 10).lastEvents()).isEmpty();
        assertThat(ForgingSession.start(bar45(0.04, 293.15)).inFire(0.3, 60).lastEvents()).isEmpty();
    }

    @Test
    void invalidArgumentsAreRejected() {
        var s = ForgingSession.start(bar45(0.04, 293.15));
        assertThatThrownBy(() -> s.inFire(1.1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.inFire(Double.NaN, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.inFire(0.5, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.temper(473.15, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
