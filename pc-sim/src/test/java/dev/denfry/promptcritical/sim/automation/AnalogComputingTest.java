package dev.denfry.promptcritical.sim.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.Quality;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Аналоговые вычислители A2 (03d §3d.4): интегратор, дифференциатор, ПИ из блоков, алгебраический контур. */
class AnalogComputingTest {
    static final double DT = 0.05;
    static final AutomationConfig CFG = AutomationConfig.defaults();

    static AnalogBank bank(long seed) {
        return new AnalogBank(CFG, DT, seed);
    }

    static void step(AnalogBank a, int n) {
        for (int i = 0; i < n; i++) a.stepFast();
    }

    @Test
    @Tag("T-3d-07")
    void integratorDriftsAtEpsilonOverTi() {
        AnalogBank a = bank(1);
        int i = a.addIntegrator(AnalogTech.TUBE, new int[0], new double[0], 100);
        a.setIdeal(i);
        a.setLeak(i, false);
        a.setDrift(i, 0.010);
        step(a, (int) Math.round(3600 / DT) + 1);
        assertThat(a.output(i)).isCloseTo(-0.360, within(0.001));
    }

    @Test
    @Tag("T-3d-08")
    void integratorSaturatesAtTheRailAndStaysThere() {
        AnalogBank a = bank(1);
        int x = a.newSignal();
        a.setSignal(x, 1);
        int i = a.addIntegrator(AnalogTech.TRANSISTOR, new int[] {x}, new double[] {1}, 1);
        a.setIdeal(i);
        int ticks = 0;
        while (a.integratorState(i) > -AnalogBank.U_SAT + 1e-9) {
            a.stepFast();
            ticks++;
        }
        assertThat(ticks).isBetween(209, 211);
        step(a, 100);
        assertThat(a.integratorState(i)).isEqualTo(-AnalogBank.U_SAT);
        assertThat(a.output(i)).isGreaterThanOrEqualTo(-AnalogBank.U_SAT);
    }

    @Test
    @Tag("T-3d-09")
    void tubeIntegratorDriftReachesItsRmsAtTheAdjustInterval() {
        AutomationConfig cfg = CFG.withStationLimits(256, 4096);
        double sumSq = 0;
        int n = 0;
        for (int b = 0; b < 3; b++) {
            AnalogBank a = new AnalogBank(cfg, DT, 50 + b);
            int[] ids = new int[Math.min(4096, 10_000 - n)];
            for (int j = 0; j < ids.length; j++) ids[j] = a.addIntegrator(AnalogTech.TUBE, new int[0], new double[0], 100);
            for (int h = 0; h < 7 * 24; h++) a.stepSlow(3600);
            for (int id : ids) sumSq += a.drift(id) * a.drift(id);
            n += ids.length;
            if (b == 0) assertThat(a.calDue(ids[0])).isFalse();
        }
        assertThat(n).isEqualTo(10_000);
        assertThat(Math.sqrt(sumSq / n)).isCloseTo(CFG.driftRmsTubeV(), within(0.03 * CFG.driftRmsTubeV()));
    }

    @Test
    void calibrationOnlyOutsideOperateAndCalDueAfterTheInterval() {
        AnalogBank a = bank(2);
        int i = a.addIntegrator(AnalogTech.TUBE, new int[0], new double[0], 10);
        a.setDrift(i, 0.05);
        a.stepSlow(8 * 86400.0);
        assertThat(a.calDue(i)).isTrue();
        a.stepFast();
        assertThat(a.quality(i)).isEqualTo(Quality.UNCERTAIN);
        assertThat(a.calibrate(i)).isFalse(); // в OPERATE запрещено
        a.setIntegratorMode(i, IntegratorMode.HOLD);
        assertThat(a.calibrate(i)).isTrue();
        assertThat(Math.abs(a.drift(i))).isLessThanOrEqualTo(AnalogTech.TUBE.offsetV);
        assertThat(a.calDue(i)).isFalse();
    }

    @Test
    void hotTubeDriftsTwiceAsFastPerTenKelvin() {
        AutomationConfig cfg = CFG.withStationLimits(256, 4096);
        double[] rms = new double[2];
        for (int hot = 0; hot < 2; hot++) {
            AnalogBank a = new AnalogBank(cfg, DT, 70 + hot);
            int[] ids = new int[4000];
            for (int j = 0; j < ids.length; j++) {
                ids[j] = a.addIntegrator(AnalogTech.TUBE, new int[0], new double[0], 100);
                a.setDrift(ids[j], 0);
                a.setBlockTemperature(ids[j], hot == 1 ? 323.15 : 300);
            }
            a.stepSlow(7 * 86400.0);
            double s = 0;
            for (int id : ids) s += a.drift(id) * a.drift(id);
            rms[hot] = Math.sqrt(s / ids.length);
        }
        assertThat(rms[1] / rms[0]).isCloseTo(2, within(0.1));
    }

    @Test
    @Tag("T-3d-10")
    void differentiatorStepResponseIsGainThenExponentialDecay() {
        AnalogBank a = bank(1);
        int x = a.newSignal();
        int d = a.addDifferentiator(AnalogTech.TRANSISTOR, x, 10, 8);
        a.setIdeal(d);
        a.stepFast();
        a.setSignal(x, 1);
        a.stepFast();
        assertThat(a.output(d)).isCloseTo(10 / 1.3, within(0.01));
        double prev = a.output(d);
        a.stepFast();
        assertThat(a.output(d) / prev).isCloseTo(1.25 / 1.3, within(1e-4));
    }

    @Test
    void differentiatorAmplifiesSensorNoise() {
        AnalogBank a = bank(5);
        int x = a.newSignal();
        int d = a.addDifferentiator(AnalogTech.TRANSISTOR, x, 10, 8);
        a.setIdeal(d);
        java.util.SplittableRandom r = new java.util.SplittableRandom(9);
        double sigma = 0.01, s2 = 0;
        int n = 20_000;
        for (int t = 0; t < n + 200; t++) {
            a.setSignal(x, sigma * r.nextGaussian());
            a.stepFast();
            if (t >= 200) s2 += a.output(d) * a.output(d);
        }
        double gain = Math.sqrt(s2 / n) / sigma;
        assertThat(gain).isBetween(5.0, 15.0);
    }

    /** ПИ с внешней обратной связью по сбросу: u = K_c e + y, ẏ = (u_act − y)/T_i (03d §3d.4.8). */
    @Test
    @Tag("T-3d-11")
    void externalResetFeedbackPiLeavesTheStopOnTheFirstTickOfSignChange() {
        AnalogBank a = bank(3);
        int sp = a.newSignal(), pv = a.newSignal(), uAct = a.newSignal();
        double kc = 2;
        int lag = a.addIntegrator(AnalogTech.TRANSISTOR, new int[] {uAct, -1}, new double[] {-1, 1}, 20);
        a.setInput(lag, 1, a.out(lag));
        int u = a.addSummer(AnalogTech.TRANSISTOR, new int[] {sp, pv, a.out(lag)}, new double[] {-kc, kc, -1});
        a.setIdeal(lag);
        a.setIdeal(u);
        a.setSignal(sp, 8);
        a.setSignal(pv, 2);
        for (int t = 0; t < 300 / DT; t++) {
            a.stepFast();
            a.setSignal(uAct, Math.max(0, Math.min(10, a.output(u)))); // клапан в упоре 100 % = 10 В
        }
        assertThat(a.signal(uAct)).isEqualTo(10);
        // нет виндапа: сброс = положению клапана (минус утечка 10·T_i/T_leak = 1 мВ)
        assertThat(a.integratorState(lag)).isCloseTo(10, within(2e-3));
        a.setSignal(pv, 9); // ошибка меняет знак
        a.stepFast();
        assertThat(a.output(u)).isLessThan(10);
    }

    @Test
    void manualToAutoIsBumplessWhenTheIntegratorTracks() {
        AnalogBank a = bank(4);
        int sp = a.newSignal(), pv = a.newSignal();
        double kc = 2;
        int integ = a.addIntegrator(AnalogTech.TRANSISTOR, new int[] {sp, pv}, new double[] {-kc, kc}, 30);
        int u = a.addSummer(AnalogTech.TRANSISTOR, new int[] {sp, pv, a.out(integ)}, new double[] {-kc, kc, -1});
        int st = a.addManualStation(a.out(u), -1);
        int ic = a.addSummer(AnalogTech.TRANSISTOR, new int[] {a.out(st, AnalogBank.STATION_TRACK), sp, pv},
                new double[] {-1, kc, -kc}); // u_track − K_c·e
        a.setIntegratorIc(integ, a.out(ic), a.out(st, AnalogBank.STATION_MODE), -1);
        for (int b : new int[] {integ, u, ic}) a.setIdeal(b);
        a.setSignal(sp, 5);
        a.setSignal(pv, 4);
        a.setManualOutput(st, 6);
        step(a, 50);
        assertThat(a.signal(a.out(st, AnalogBank.STATION_OUT_MA))).isCloseTo(4 + 1.6 * 6, within(1e-12));
        a.setStationMode(st, StationMode.AUTO);
        a.stepFast();
        assertThat(a.signal(a.out(st, AnalogBank.STATION_OUT_MA))).isCloseTo(4 + 1.6 * 6, within(1e-9));
        step(a, 20); // дальше интегрирует ошибку
        assertThat(a.signal(a.out(st, AnalogBank.STATION_TRACK))).isGreaterThan(6);
    }

    @Test
    void stationRampsTheManualOutputAndForcedManualPicksUpTheAutoValue() {
        AnalogBank a = bank(4);
        int auto = a.newSignal(), force = a.newSignal();
        int st = a.addManualStation(auto, force);
        a.setManualOutput(st, 2);
        a.setManualRamp(st, 1);
        step(a, 20); // 1 с — 10 %
        assertThat(a.signal(a.out(st, AnalogBank.STATION_TRACK))).isCloseTo(3, within(1e-9));
        a.setManualRamp(st, 0);
        a.setStationMode(st, StationMode.AUTO);
        a.setSignal(auto, 7);
        a.stepFast();
        assertThat(a.signal(a.out(st, AnalogBank.STATION_OUT_MA))).isCloseTo(4 + 1.6 * 7, within(1e-12));
        a.setSignal(force, 1); // ПАЗ: принудительный РУЧ
        a.stepFast();
        assertThat(a.stationMode(st)).isEqualTo(StationMode.MANUAL);
        assertThat(a.signal(a.out(st, AnalogBank.STATION_TRACK))).isCloseTo(7, within(1e-12));
        assertThat(a.signal(a.out(st, AnalogBank.STATION_MODE))).isEqualTo(1);
    }

    @Test
    @Tag("T-3d-12")
    void dividerInTheFeedbackWithoutAnIntegratorIsAnAlgebraicLoop() {
        AnalogBank a = bank(1);
        int sp = a.newSignal(), den = a.newSignal();
        int sum = a.addSummer(AnalogTech.TRANSISTOR, new int[] {sp, -1}, new double[] {-1, 1});
        int div = a.addMultiplier(AnalogTech.TRANSISTOR, MultiplierMode.DIV, a.out(sum), den);
        a.setInput(sum, 1, a.out(div));
        int after = a.addSummer(AnalogTech.TRANSISTOR, new int[] {a.out(div)}, new double[] {1});
        a.setIdeal(after);
        a.setSignal(sp, 1);
        a.setSignal(den, 5);
        step(a, 3);
        assertThat(a.algebraicLoop(sum)).isTrue();
        assertThat(a.algebraicLoop(div)).isTrue();
        assertThat(a.algebraicLoop(after)).isFalse();
        assertThat(a.output(sum)).isEqualTo(AnalogBank.U_SAT);
        assertThat(a.output(div)).isEqualTo(AnalogBank.U_SAT);
        assertThat(a.quality(div)).isEqualTo(Quality.BAD);
        assertThat(a.output(after)).isEqualTo(-AnalogBank.U_SAT);
    }

    @Test
    void integratorInTheLoopIsNotAnAlgebraicLoop() {
        AnalogBank a = bank(1);
        int x = a.newSignal();
        int i = a.addIntegrator(AnalogTech.TRANSISTOR, new int[] {x, -1}, new double[] {1, 1}, 5);
        int s = a.addSummer(AnalogTech.TRANSISTOR, new int[] {a.out(i)}, new double[] {1});
        a.setInput(i, 1, a.out(s));
        a.stepFast();
        assertThat(a.algebraicLoop(i)).isFalse();
        assertThat(a.algebraicLoop(s)).isFalse();
    }

    @Test
    void multiplierModesAndSqrtCutoff() {
        AnalogBank a = bank(1);
        int x1 = a.newSignal(), x2 = a.newSignal();
        int mul = a.addMultiplier(AnalogTech.TRANSISTOR, MultiplierMode.MUL, x1, x2);
        int div = a.addMultiplier(AnalogTech.TRANSISTOR, MultiplierMode.DIV, x1, x2);
        int sq = a.addMultiplier(AnalogTech.TRANSISTOR, MultiplierMode.SQUARE, x1, x2);
        int rt = a.addMultiplier(AnalogTech.TRANSISTOR, MultiplierMode.SQRT, x1, x2);
        for (int b : new int[] {mul, div, sq, rt}) a.setIdeal(b);
        a.setSignal(x1, 4);
        a.setSignal(x2, 5);
        a.stepFast();
        assertThat(a.output(mul)).isCloseTo(2, within(1e-12));
        assertThat(a.output(div)).isCloseTo(8, within(1e-12));
        assertThat(a.output(sq)).isCloseTo(1.6, within(1e-12));
        assertThat(a.output(rt)).isCloseTo(Math.sqrt(40), within(1e-12));
        a.setSignal(x1, 0.05); // ниже отсечки 0,1 В: расходомер показывает 0
        a.setSignal(x2, 0.01);
        a.stepFast();
        assertThat(a.output(rt)).isZero();
        assertThat(a.output(div)).isEqualTo(AnalogBank.U_SAT);
        assertThat(a.quality(div)).isEqualTo(Quality.UNCERTAIN);
        // Погрешность реального множителя — в пределах δ_m шкалы
        AnalogBank r = bank(8);
        int y1 = r.newSignal(), y2 = r.newSignal();
        int m = r.addMultiplier(AnalogTech.TUBE, MultiplierMode.MUL, y1, y2);
        r.setSignal(y1, 5);
        r.setSignal(y2, 5);
        r.stepFast();
        assertThat(r.output(m)).isCloseTo(2.5, within(0.05 + 2.5 * 0.005 + 0.002 + 0.05));
    }

    @Test
    void functionGeneratorInterpolatesAndExtrapolatesTheEndSegments() {
        AnalogBank a = bank(1);
        int x = a.newSignal();
        double[] xs = new double[11], ys = new double[11];
        for (int j = 0; j <= 10; j++) {
            xs[j] = j - 5;
            ys[j] = (j - 5) * (j - 5) / 2.5;
        }
        int f = a.addFunction(AnalogTech.TRANSISTOR, x, xs, ys);
        a.setIdeal(f);
        a.setSignal(x, 2.5);
        a.stepFast();
        assertThat(a.output(f)).isCloseTo((4 + 9) / 2.5 / 2, within(1e-12));
        a.setSignal(x, 6); // за краем: продолжение отрезка 4…5 (наклон 3,6) и насыщение
        a.stepFast();
        assertThat(a.output(f)).isEqualTo(AnalogBank.U_SAT); // 13,6 В → насыщение
    }

    @Test
    void comparatorHasAReturnZoneAndDrivesARelay() {
        AnalogBank a = bank(1);
        int x1 = a.newSignal(), x2 = a.newSignal();
        int c = a.addComparator(AnalogTech.TRANSISTOR, x1, x2, Double.NaN);
        a.setIdeal(c);
        a.setSignal(x2, 5);
        a.setSignal(x1, 5.01);
        a.stepFast();
        assertThat(a.output(c)).isEqualTo(1);
        a.setSignal(x1, 4.95); // внутри зоны 0,1 В
        a.stepFast();
        assertThat(a.output(c)).isEqualTo(1);
        a.setSignal(x1, 4.89);
        a.stepFast();
        assertThat(a.output(c)).isZero();

        // Мост A2 → A1: контакт компаратора включает реле в следующем тике A1
        RelayBank relays = new RelayBank(CFG, DT, 1);
        int plus = relays.newNode(), zero = relays.newNode(), coil = relays.newNode();
        relays.addSupply(plus, zero, 1);
        int contact = relays.addSwitch(plus, coil, false);
        int k = relays.addRelay(RelayKind.GENERAL, 0, coil, zero);
        a.setSignal(x1, 6);
        a.stepFast();
        relays.setSwitch(contact, a.output(c) > 0.5);
        relays.stepFast();
        assertThat(relays.isPicked(k)).isTrue();
    }

    @Test
    void currentToVoltageConverterFlagsBrokenLoop() {
        AnalogBank a = bank(1);
        int ma = a.newSignal(), v = a.newSignal();
        int iv = a.addConverter(AnalogTech.TRANSISTOR, true, ma);
        int vi = a.addConverter(AnalogTech.TRANSISTOR, false, v);
        a.setIdeal(iv);
        a.setIdeal(vi);
        a.setSignal(ma, 12);
        a.setSignal(v, 12);
        a.stepFast();
        assertThat(a.output(iv)).isCloseTo(5, within(1e-12));
        assertThat(a.output(vi)).isEqualTo(20.5);
        a.setSignal(ma, 2); // обрыв петли (NE43)
        a.stepFast();
        assertThat(a.output(iv)).isEqualTo(-1);
        assertThat(a.quality(iv)).isEqualTo(Quality.BAD);
    }

    @Test
    void summerNoiseAndOffsetAreOfTheTechnologyOrder() {
        AnalogBank a = bank(12);
        int s = a.addSummer(AnalogTech.TUBE, new int[0], new double[0]);
        double sum = 0, sum2 = 0;
        int n = 40_000;
        for (int t = 0; t < n; t++) {
            a.stepFast();
            sum += a.output(s);
            sum2 += a.output(s) * a.output(s);
        }
        double mean = sum / n, sd = Math.sqrt(sum2 / n - mean * mean);
        assertThat(Math.abs(mean)).isLessThan(AnalogTech.TUBE.offsetV + 0.003);
        assertThat(sd).isCloseTo(AnalogTech.TUBE.noiseSigmaV, within(0.2 * AnalogTech.TUBE.noiseSigmaV));
    }

    @Test
    void tubesWearOutAndAreReplaced() {
        AnalogBank a = bank(6);
        int x = a.newSignal();
        a.setSignal(x, 1);
        int s = a.addSummer(AnalogTech.TUBE, new int[] {x}, new double[] {1});
        int t = a.addSummer(AnalogTech.TRANSISTOR, new int[] {x}, new double[] {1});
        assertThat(a.heatW()).isEqualTo(28);
        for (int h = 0; h < 100 && !a.tubeFailed(s); h++) a.stepSlow(5000 * 3600.0);
        assertThat(a.tubeFailed(s)).isTrue();
        assertThat(a.tubeFailed(t)).isFalse();
        a.stepFast();
        assertThat(a.output(s)).isZero();
        assertThat(a.quality(s)).isEqualTo(Quality.BAD);
        a.replaceTube(s);
        a.stepFast();
        assertThat(a.output(s)).isCloseTo(-1, within(0.05));
    }

    @Test
    void knobsHaveFiniteResolution() {
        AnalogBank a = bank(1);
        int x = a.newSignal();
        // SIMC-пример §3d.4.8: K_c = 8,3, T_i/K_c = 24/8,3 ≈ 2,89 с
        int i = a.addIntegrator(AnalogTech.TRANSISTOR, new int[] {x}, new double[] {1.004}, 24 / 8.3);
        a.setIdeal(i);
        a.setLeak(i, false);
        a.setSignal(x, 1);
        a.stepFast();
        assertThat(a.integratorState(i)).isCloseTo(-DT / 2.89, within(1e-12));
    }
}
