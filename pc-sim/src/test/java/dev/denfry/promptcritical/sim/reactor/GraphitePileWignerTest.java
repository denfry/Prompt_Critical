package dev.denfry.promptcritical.sim.reactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.neutronics.NeutronicsConfig;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Энергия Вигнера в котле T0 (04c §4c.11.1): холодная кладка копит запас на мощности; прогретая под закрытыми
 * заслонками отдаёт малый запас порциями (отжиг), большой — разом, до пожара графита (Windscale, 1957).
 */
class GraphitePileWignerTest {
    private final SplittableRandom random = new SplittableRandom(7);
    /** Защита по периоду и мощности вне досягаемости: мощность держит «оператор» теста. */
    private static final PileConfig NO_TRIPS = new PileConfig(1e6, 1e7, 0.004, 0.05, 5, 0.1, 1e-6, 1e12, 1e-5, 2e-6, 0.5,
            110, 10, 873, 723, 293.15);

    private static final class Recorder implements PileEvents {
        int runaway, annealed, fire;
        double releasedJg;

        @Override
        public void wignerRunaway(double graphiteTempK, double storedJg) {
            runaway++;
        }

        @Override
        public void wignerAnnealed(double releasedJg, double graphiteTempK) {
            annealed++;
            this.releasedJg += releasedJg;
        }

        @Override
        public void graphiteFire(double graphiteTempK) {
            fire++;
        }
    }

    private static GraphitePile pile() {
        GraphitePile p = new GraphitePile(NO_TRIPS, NeutronicsConfig.defaults());
        p.form(Piles.cube(5, 1, 8, true, true, true));
        p.setRodInsertion(Piles.ZIP_KEY, 1);
        p.setRodInsertion(Piles.CONTROL_KEY, 1);
        return p;
    }

    /** Оператор держит мощность обоими стержнями: раз в секунду — реактивность к цели по логарифму мощности. */
    private void hold(GraphitePile p, double targetW, double seconds, PileEvents ev) {
        double c = p.rodInsertion(Piles.CONTROL_KEY);
        for (int s = 0; s < seconds; s++) {
            Piles.run(p, 1, random, ev);
            double want = Math.max(-0.002, Math.min(0.0015, 7e-4 * Math.log(targetW / Math.max(p.powerW(), 1e-3))));
            c = Math.max(0, Math.min(1, c - (want - p.reactivity()) * 3));
            p.setRodInsertion(Piles.CONTROL_KEY, c);
            p.setRodInsertion(Piles.ZIP_KEY, c);
        }
    }

    @Test
    void coldPileStoresEnergyAtPower() {
        GraphitePile p = pile();
        Recorder ev = new Recorder();
        hold(p, 150e3, 1800, ev);
        assertThat(p.powerW()).isBetween(100e3, 200e3);
        assertThat(p.graphiteTempK()).isBetween(300.0, 340.0); // часы SLOW: кладка заметно прогрелась за полчаса
        assertThat(p.wignerStoredJg()).isBetween(20.0, 80.0);
        assertThat(ev.runaway + ev.annealed + ev.fire).isZero();
    }

    /** Малый запас, прогретая кладка, заслонки закрыты: запас уходит порциями, графит не доходит до окисления. */
    @Test
    void smallStoreAnnealsUnderClosedDampers() {
        GraphitePile p = pile();
        p.wigner().preload(100, 293.15);
        p.setGraphiteTempK(420);
        p.setDampersClosed(true);
        Recorder ev = new Recorder();
        double peak = 0;
        for (int m = 0; m < 60 && ev.annealed == 0; m++) {
            hold(p, 150e3, 60, ev);
            peak = Math.max(peak, p.graphiteTempK());
        }
        assertThat(ev.annealed).isPositive();
        assertThat(ev.releasedJg).isGreaterThan(50);
        assertThat(ev.fire).isZero();
        assertThat(peak).isLessThan(WignerEnergy.OXIDATION_K);
    }

    /** Большой запас в прогреваемой кладке: dE/dT больше c_p, нагрев идёт сам, графит загорается. */
    @Test
    void largeStoreRunsAwayIntoAGraphiteFire() {
        GraphitePile p = pile();
        p.wigner().preload(400, 293.15);
        p.setGraphiteTempK(380);
        p.setDampersClosed(true);
        Recorder ev = new Recorder();
        for (int m = 0; m < 30 && ev.fire == 0; m++) hold(p, 150e3, 60, ev);
        assertThat(ev.runaway).isPositive();
        assertThat(ev.fire).isEqualTo(1);
        assertThat(p.graphiteFire()).isTrue();
        assertThat(ev.annealed).isZero();
    }

    @Test
    void storedEnergyAndDampersSurviveSaveAndLoad() {
        GraphitePile p = pile();
        p.wigner().preload(77, 293.15);
        p.setDampersClosed(true);
        Piles.run(p, 1, random);
        MutableStateTree tree = new MutableStateTree();
        p.save(new TreeStateWriter(tree));
        GraphitePile q = new GraphitePile();
        q.load(new TreeStateReader(tree));
        assertThat(q.wignerStoredJg()).isCloseTo(p.wignerStoredJg(), within(1e-9));
        assertThat(q.dampersClosed()).isTrue();
    }
}
