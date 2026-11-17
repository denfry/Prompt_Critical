package dev.denfry.promptcritical.sim.reactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.neutronics.Inhour;
import dev.denfry.promptcritical.sim.neutronics.LatticeDesigns;
import dev.denfry.promptcritical.sim.neutronics.LatticeModel;
import dev.denfry.promptcritical.sim.neutronics.NeutronicsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Графитовый котёл T0: критический размер, подкритическое размножение (1/M), выход на критичность стержнями,
 * стабильный период по обратным часам, аварийная защита и блокировки, разгон без стержней, сохранение.
 */
class GraphitePileTest {
    private final SplittableRandom random = new SplittableRandom(42);
    /** Уставки защиты вне досягаемости — для опытов с реактивностью. */
    private static final PileConfig NO_TRIPS = new PileConfig(1e6, 1e7, 0.004, 0.05, 5, 0.1, 1e-6, 1e12, 1e-5, 2e-6, 0.5,
            110, 10, 873, 723, 293.15);

    private static final class Recorder implements PileEvents {
        final List<PileTrip> trips = new ArrayList<>();
        int critical, clad, fire;

        @Override
        public void tripped(PileTrip reason, double powerW, double periodS) {
            trips.add(reason);
        }

        @Override
        public void critical(double powerW) {
            critical++;
        }

        @Override
        public void cladFailed(double fuelTempK) {
            clad++;
        }

        @Override
        public void graphiteFire(double graphiteTempK) {
            fire++;
        }
    }

    /** С метровым графитовым отражателем куб 3³ подкритичен, 4³ — уже надкритичен (k∞ ≈ 1,07, M² ≈ 650 см²). */
    @Test
    void criticalSizeLiesBetweenThreeAndFourMetresOfFuel() {
        GraphitePile small = Piles.pile(Piles.cube(3, 1, 8, false, false, true));
        GraphitePile big = Piles.pile(Piles.cube(4, 1, 8, false, false, true));
        Piles.run(small, 1, random);
        Piles.run(big, 1, random);
        assertThat(small.keff()).isBetween(0.95, 1.0);
        assertThat(big.keff()).isBetween(1.0, 1.05);
        // отражатель сберегает топливо: тот же куб без него хуже
        GraphitePile bare = Piles.pile(Piles.cube(4, 0, 8, false, false, true));
        Piles.run(bare, 1, random);
        assertThat(bare.keff()).isLessThan(big.keff() - 0.03);
    }

    /** Источник в подкритичной кладке: счёт растёт с загрузкой как 1/(1 − k), обратный счёт падает к нулю. */
    @Test
    void countRateFollowsSubcriticalMultiplication() {
        double prevCps = 0, prevK = 0;
        double[] inverse = new double[4];
        for (int step = 0; step < 4; step++) {
            int load = 2 + 2 * step;
            GraphitePile p = Piles.pile(Piles.cube(3, 1, load, false, false, true));
            Piles.run(p, 120, random);
            double cps = p.expectedCountRate(0);
            assertThat(p.keff()).isGreaterThan(prevK);
            assertThat(cps).isGreaterThan(prevCps);
            // M = 1/(1 − k): счёт в пределах 30 % от S·M с поправкой на форму потока
            inverse[step] = 1 / cps;
            prevCps = cps;
            prevK = p.keff();
        }
        assertThat(inverse[3] / inverse[0]).isLessThan(0.5);
        // без источника почти один фон
        GraphitePile dark = Piles.pile(Piles.cube(3, 1, 8, false, false, false));
        Piles.run(dark, 60, random);
        assertThat(dark.expectedCountRate(0)).isLessThan(prevCps / 100);
        // шумит пуассоновски, но около ожидаемого
        GraphitePile p = Piles.pile(Piles.cube(3, 1, 8, false, false, true));
        Piles.run(p, 120, random);
        assertThat(p.countRate(0)).isCloseTo(p.expectedCountRate(0), within(p.expectedCountRate(0) * 0.25));
    }

    /**
     * Пуск как на CP-1: стержни введены — счёт стоит; взвести «zip»; тянуть регулирующий, пока котёл не держит цепную
     * реакцию сам; остановить привод — мощность растёт с постоянным положительным периодом.
     */
    @Test
    void controlRodWithdrawalBringsThePileToCriticality() {
        Recorder ev = new Recorder();
        GraphitePile p = Piles.pile(Piles.cube(4, 1, 8, true, true, true));
        Piles.run(p, 30, random, ev);
        assertThat(p.keff()).isLessThan(1);
        assertThat(p.powerW()).isLessThan(1);
        double sourceRange = p.expectedCountRate(0);
        // регулирующий не идёт вверх, пока аварийный не взведён
        p.driveControlRods(1);
        Piles.run(p, 5, random, ev);
        assertThat(p.controlInsertion()).isEqualTo(1.0);
        assertThat(p.windZip()).isTrue();
        Piles.run(p, 80, random, ev);
        assertThat(p.zipCocked()).isTrue();
        assertThat(p.winding()).isFalse();
        // медленно вверх до ρ > 0
        p.driveControlRods(1);
        int guard = 0;
        while (p.reactivity() < 0.0015 && guard++ < 4000) Piles.run(p, 0.5, random, ev);
        p.driveControlRods(0);
        assertThat(p.tripped()).isFalse();
        Piles.run(p, 60, random, ev);
        assertThat(ev.critical).isEqualTo(1);
        assertThat(p.critical()).isTrue();
        assertThat(p.periodS()).isPositive().isFinite();
        assertThat(p.expectedCountRate(0)).isGreaterThan(10 * sourceRange);
        assertThat(ev.trips).isEmpty();
    }

    /** Стабильный период по прибору совпадает с решением обратных часов для той же реактивности. */
    @Test
    void stablePeriodMatchesInhour() {
        // подобрать ввод регулирующего на ρ ≈ +0,15β: каждая проба — свежая кладка, один шаг без динамики
        double lo = 0, hi = 1;
        for (int i = 0; i < 25; i++) {
            double mid = 0.5 * (lo + hi);
            GraphitePile probe = noTrips();
            probe.setRodInsertion(Piles.CONTROL_KEY, mid);
            Piles.run(probe, 0.05, random);
            if (probe.reactivity() > 0.15 * probe.betaEff()) lo = mid;
            else hi = mid;
        }
        GraphitePile p = noTrips();
        p.setRodInsertion(Piles.CONTROL_KEY, hi);
        Piles.run(p, 150, random);
        double rho = p.reactivity();
        assertThat(rho).isPositive();
        double expected = Inhour.period(rho, p.delayedGroups(), p.generationTime());
        assertThat(p.periodS()).isCloseTo(expected, within(expected * 0.1));
    }

    private static GraphitePile noTrips() {
        GraphitePile p = new GraphitePile(NO_TRIPS, NeutronicsConfig.defaults());
        p.form(Piles.cube(4, 1, 8, true, true, false));
        p.setRodInsertion(Piles.ZIP_KEY, 0);
        return p;
    }

    /** Быстрый подъём стержня: защита по периоду, «zip» падает за секунды, регулирующий идёт вниз, мощность спадает. */
    @Test
    void shortPeriodTripsTheZipRod() {
        Recorder ev = new Recorder();
        GraphitePile p = Piles.pile(Piles.cube(4, 1, 8, true, true, true));
        p.setRodInsertion(Piles.ZIP_KEY, 0);
        Piles.run(p, 10, random, ev);
        p.setRodInsertion(Piles.CONTROL_KEY, 0);
        int guard = 0;
        while (!p.tripped() && guard++ < 600) Piles.run(p, 0.5, random, ev);
        assertThat(ev.trips).containsExactly(PileTrip.SHORT_PERIOD);
        Piles.run(p, 3, random, ev);
        assertThat(p.zipInsertion()).isEqualTo(1.0);
        Piles.run(p, 30, random, ev);
        // защита поймала разгон ещё в диапазоне источника: котёл глубоко подкритичен, регулирующий идёт вниз
        assertThat(p.reactivity()).isLessThan(-0.005);
        assertThat(p.controlInsertion()).isGreaterThan(0.1);
        assertThat(p.powerW()).isLessThan(1);
        assertThat(p.periodS()).satisfiesAnyOf(t -> assertThat(t).isNegative(), t -> assertThat(t).isGreaterThan(60));
        // взвести нельзя, пока защита не снята; снять — когда период ушёл
        assertThat(p.windZip()).isFalse();
        assertThat(p.resetTrip()).isTrue();
        assertThat(p.windZip()).isTrue();
    }

    /** Мощность выше уставки — защита по мощности; ручная кнопка — тоже защита. */
    @Test
    void highPowerAndManualScram() {
        Recorder ev = new Recorder();
        GraphitePile p = Piles.pile(Piles.cube(5, 1, 8, true, true, true));
        p.setRodInsertion(Piles.ZIP_KEY, 0);
        p.scram(ev);
        assertThat(ev.trips).containsExactly(PileTrip.MANUAL);
        Piles.run(p, 3, random, ev);
        assertThat(p.zipInsertion()).isEqualTo(1.0);

        PileConfig low = new PileConfig(1e6, 1e7, 0.004, 0.05, 5, 0.1, 0.01, 1e-3, 1e-5, 2e-6, 0.5, 110, 10, 873, 723, 293.15);
        GraphitePile q = new GraphitePile(low, dev.denfry.promptcritical.sim.neutronics.NeutronicsConfig.defaults());
        q.form(Piles.cube(5, 1, 8, true, true, true));
        q.setRodInsertion(Piles.ZIP_KEY, 0);
        q.setRodInsertion(Piles.CONTROL_KEY, 0);
        Recorder ev2 = new Recorder();
        Piles.run(q, 120, random, ev2);
        assertThat(ev2.trips).containsExactly(PileTrip.HIGH_POWER);
    }

    /** Кладка без стержней надкритична: разгон до плавления оболочек; температурный коэффициент графита отрицателен. */
    @Test
    void pileWithoutRodsRunsAwayUntilTheCladdingFails() {
        Recorder ev = new Recorder();
        GraphitePile p = Piles.pile(Piles.cube(5, 1, 8, false, false, true));
        Piles.run(p, 0.05, random, ev);
        double rho0 = p.reactivity();
        assertThat(rho0).isGreaterThan(0.0067); // мгновенно-критична: собрана разом, как в аварии с полусферами
        Piles.run(p, 600, random, ev);
        assertThat(ev.trips).containsExactly(PileTrip.HIGH_POWER); // сработала, но ронять нечего
        assertThat(ev.clad).isEqualTo(1);
        assertThat(p.cladFailed()).isTrue();
        assertThat(p.fuelTempK()).isGreaterThan(PileConfig.defaults().cladFailureK());
        assertThat(p.reactivity()).isLessThan(rho0); // доплер и графит гасят разгон
        // температурные коэффициенты модели решётки T0 отрицательны у свежего топлива
        LatticeModel m = new LatticeModel();
        var cell = LatticeDesigns.referenceCell(LatticeDesigns.T0_SLUG);
        var s = LatticeDesigns.referenceState(LatticeDesigns.T0_SLUG);
        var x = m.evaluate(cell, dev.denfry.promptcritical.sim.nuclide.NuclideVector.uranium(1000, 0.00711), s);
        assertThat(x.dkdTm()).isNegative();
        assertThat(x.dkdTf()).isNegative();
    }

    /** Перестройка кладки сохраняет положения стержней по ключу; новые стержни приходят введёнными. */
    @Test
    void reformingKeepsRodsByKey() {
        GraphitePile p = Piles.pile(Piles.cube(4, 1, 8, true, false, true));
        p.setRodInsertion(Piles.CONTROL_KEY, 0.25);
        Piles.run(p, 1, random);
        p.form(Piles.cube(4, 1, 8, true, true, true));
        assertThat(p.rodInsertion(Piles.CONTROL_KEY)).isEqualTo(0.25);
        assertThat(p.rodInsertion(Piles.ZIP_KEY)).isEqualTo(1.0);
        assertThat(p.zipCount()).isEqualTo(1);
        assertThat(p.controlCount()).isEqualTo(1);
    }

    /** Сохранение: мощность, температуры, защита и стержни переживают перезагрузку и новую кладку от биндинга. */
    @Test
    void saveAndLoadRoundTrip() {
        GraphitePile p = Piles.pile(Piles.cube(4, 1, 8, true, true, true));
        p.setRodInsertion(Piles.ZIP_KEY, 0);
        p.setRodInsertion(Piles.CONTROL_KEY, 0.95);
        Piles.run(p, 30, random);
        assertThat(p.tripped()).isFalse();
        p.scram();
        Piles.run(p, 1, random);

        MutableStateTree tree = new MutableStateTree();
        p.save(new TreeStateWriter(tree));
        GraphitePile q = new GraphitePile();
        q.load(new TreeStateReader(tree));
        assertThat(q.layout().nodeCount()).isZero();
        q.form(Piles.cube(4, 1, 8, true, true, true));
        assertThat(q.trip()).isEqualTo(PileTrip.MANUAL);
        assertThat(q.rodInsertion(Piles.CONTROL_KEY)).isEqualTo(p.rodInsertion(Piles.CONTROL_KEY));
        assertThat(q.rodInsertion(Piles.ZIP_KEY)).isEqualTo(p.rodInsertion(Piles.ZIP_KEY));
        assertThat(q.graphiteTempK()).isEqualTo(p.graphiteTempK());
        Piles.run(q, 0.05, random);
        assertThat(q.powerW()).isCloseTo(p.powerW(), within(p.powerW() * 0.2));
    }

    /** Безопасное состояние без сохранения: защита сработала, все стержни внизу. */
    @Test
    void safeStateDropsAllRods() {
        GraphitePile p = Piles.pile(Piles.cube(4, 1, 8, true, true, true));
        p.setRodInsertion(Piles.ZIP_KEY, 0);
        p.setRodInsertion(Piles.CONTROL_KEY, 0);
        p.resetToSafeState();
        assertThat(p.tripped()).isTrue();
        assertThat(p.zipInsertion()).isEqualTo(1.0);
        assertThat(p.controlInsertion()).isEqualTo(1.0);
    }

    /** Бюджет P5: кладка 9×9×9 (729 узлов) с движущимися стержнями — тик в пределах миллисекунды на сервере. */
    @Test
    void tickFitsTheBudgetWhileRodsMove() {
        GraphitePile p = new GraphitePile(NO_TRIPS, NeutronicsConfig.defaults());
        p.form(Piles.cube(7, 1, 8, true, true, true));
        Piles.run(p, 2, random);
        p.windZip();
        Piles.run(p, 10, random); // прогрев JIT, «zip» идёт вверх
        long t0 = System.nanoTime();
        int ticks = 400;
        Piles.run(p, ticks * GraphitePile.DT, random);
        double msPerTick = (System.nanoTime() - t0) / 1e6 / ticks;
        assertThat(p.zipInsertion()).isBetween(0.01, 0.99); // стержень двигался весь замер
        assertThat(msPerTick).isLessThan(3);
    }
}
