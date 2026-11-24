package dev.denfry.promptcritical.sim.reactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** План HP2.2: без разрешения пусконаладки регулирующие стержни не поднимаются, вниз идут, аварийные взводятся. */
class PilePermissiveTest {
    private final SplittableRandom random = new SplittableRandom(42);

    @Test
    void controlRodsDoNotRiseWithoutPermit() {
        GraphitePile p = Piles.pile(Piles.cube(4, 1, 8, true, true, true));
        p.setPermitted(false);
        p.windZip();
        Piles.run(p, 80, random);
        assertThat(p.zipCocked()).isTrue();
        p.driveControlRods(1);
        Piles.run(p, 10, random);
        assertThat(p.rodInsertion(Piles.CONTROL_KEY)).isEqualTo(1.0);
        p.setPermitted(true);
        Piles.run(p, 10, random);
        double raised = p.rodInsertion(Piles.CONTROL_KEY);
        assertThat(raised).isLessThan(1.0);
        // без разрешения поднятый стержень остаётся на месте и опускается по команде
        p.setPermitted(false);
        Piles.run(p, 10, random);
        assertThat(p.rodInsertion(Piles.CONTROL_KEY)).isEqualTo(raised);
        p.driveControlRods(-1);
        Piles.run(p, 5, random);
        assertThat(p.rodInsertion(Piles.CONTROL_KEY)).isGreaterThan(raised);
    }
}
