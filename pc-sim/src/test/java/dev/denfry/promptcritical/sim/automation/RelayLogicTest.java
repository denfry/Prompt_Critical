package dev.denfry.promptcritical.sim.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Релейная логика A1 (03d §3d.3): решение схемы, просадка питания, реле времени, искатель, отказы. */
class RelayLogicTest {
    static final double DT = 0.05;
    static final AutomationConfig CFG = AutomationConfig.defaults();

    /** Шкаф с блоком питания: «+24» и «0». */
    static final class Rig {
        final RelayBank bank;
        final int plus, zero, psu;

        Rig(AutomationConfig cfg, double ratedA, long seed) {
            bank = new RelayBank(cfg, DT, seed);
            plus = bank.newNode();
            zero = bank.newNode();
            psu = bank.addSupply(plus, zero, ratedA);
        }

        Rig() {
            this(CFG, 5, 1);
        }

        int node() {
            return bank.newNode();
        }

        void step(int n) {
            for (int i = 0; i < n; i++) bank.stepFast();
        }
    }

    @Test
    @Tag("T-3d-01")
    void relayFedThroughItsOwnNormallyClosedContactBuzzesAtHalfTheTickRate() {
        Rig rig = new Rig();
        int coil = rig.node();
        int k = rig.bank.addRelay(RelayKind.GENERAL, 0, coil, rig.zero);
        rig.bank.addContact(k, 0, false, rig.plus, coil);
        boolean prev = false;
        for (int t = 0; t < 100; t++) {
            rig.bank.stepFast();
            boolean picked = rig.bank.isPicked(k);
            assertThat(picked).isNotEqualTo(prev); // период — 2 тика
            prev = picked;
        }
        assertThat(rig.bank.opsCount(k)).isEqualTo(50);
    }

    @Test
    @Tag("T-3d-02")
    void chainOfFiveRelaysPicksUpInFiveTicks() {
        Rig rig = new Rig();
        int[] k = new int[5];
        int feed = rig.plus;
        for (int i = 0; i < 5; i++) {
            int coil = rig.node();
            k[i] = rig.bank.addRelay(RelayKind.GENERAL, 0, coil, rig.zero);
            if (i == 0) rig.bank.addSwitch(feed, coil, true);
            else rig.bank.addContact(k[i - 1], 0, true, feed, coil);
            feed = rig.plus;
        }
        rig.bank.setSupplyInput(rig.psu, 0);
        rig.step(3);
        rig.bank.setSupplyInput(rig.psu, 1); // питание в тике 0
        rig.step(4);
        assertThat(rig.bank.relayState(k[3])).isEqualTo(RelayState.PICKED);
        assertThat(rig.bank.relayState(k[4])).isEqualTo(RelayState.DROPPED);
        rig.step(1);
        assertThat(rig.bank.relayState(k[4])).isEqualTo(RelayState.PICKED); // тик 5
    }

    @Test
    @Tag("T-3d-03")
    void overloadedSupplySagsAndNewCoilsFailToPickUpWhileHeldOnesStay() {
        Rig rig = new Rig(CFG, 2, 1);
        int[] k = new int[75], sw = new int[75];
        for (int i = 0; i < 75; i++) {
            int coil = rig.node();
            k[i] = rig.bank.addRelay(RelayKind.GENERAL, 0, coil, rig.zero);
            sw[i] = rig.bank.addSwitch(rig.plus, coil, i < 60);
        }
        rig.step(2);
        assertThat(rig.bank.supplyVoltage(rig.psu)).isCloseTo(19.2, within(1e-9));
        for (int i = 0; i < 60; i++) assertThat(rig.bank.isPicked(k[i])).isTrue();
        for (int i = 60; i < 75; i++) rig.bank.setSwitch(sw[i], true);
        rig.step(3);
        assertThat(rig.bank.supplyVoltage(rig.psu)).isCloseTo(15.36, within(1e-9));
        for (int i = 0; i < 60; i++) assertThat(rig.bank.isPicked(k[i])).as("held %d", i).isTrue();
        for (int i = 60; i < 75; i++) assertThat(rig.bank.isPicked(k[i])).as("new %d", i).isFalse();
        assertThat(rig.bank.supplyCurrent(rig.psu)).isEqualTo(2);
    }

    @Test
    void busDipBelowDropoutReleasesEveryRelay() {
        Rig rig = new Rig();
        int coil = rig.node();
        int k = rig.bank.addRelay(RelayKind.GENERAL, 0, coil, rig.zero);
        rig.bank.addSwitch(rig.plus, coil, true);
        rig.step(2);
        rig.bank.setSupplyInput(rig.psu, 0.5); // 12 В — держится
        rig.step(2);
        assertThat(rig.bank.isPicked(k)).isTrue();
        rig.bank.setSupplyInput(rig.psu, 0.25); // 6 В < 7,2 В
        rig.step(1);
        assertThat(rig.bank.isPicked(k)).isFalse();
        rig.bank.setSupplyInput(rig.psu, 0.7); // 16,8 В < 18 В: обратно не срабатывает
        rig.step(3);
        assertThat(rig.bank.isPicked(k)).isFalse();
    }

    @Test
    void shortCircuitOnTheSupplyCollapsesTheVoltage() {
        Rig rig = new Rig();
        int coil = rig.node();
        int k = rig.bank.addRelay(RelayKind.GENERAL, 0, coil, rig.zero);
        rig.bank.addSwitch(rig.plus, coil, true);
        int fault = rig.bank.addSwitch(rig.plus, rig.zero, false);
        rig.step(2);
        rig.bank.setSwitch(fault, true);
        rig.step(1);
        assertThat(rig.bank.supplyVoltage(rig.psu)).isZero();
        assertThat(rig.bank.isPicked(k)).isFalse();
        assertThat(rig.bank.cabinetHeatW(0)).isZero();
    }

    @Test
    @Tag("T-3d-04")
    void pneumaticTimeRelaySpreadIsTenPercent() {
        AutomationConfig cfg = CFG.withStationLimits(4096, 128);
        double sum = 0, min = Double.MAX_VALUE, max = 0;
        int n = 0;
        for (int b = 0; b < 3; b++) {
            Rig rig = new Rig(cfg, 10, 100 + b);
            int coil = rig.node();
            for (int i = 0; i < 3334 && n < 10_000; i++, n++) {
                int k = rig.bank.addTimeRelay(TimeRelayType.PNEUMATIC, TimeRelayMode.ON_DELAY, 10, 0, coil, rig.zero);
                double d = rig.bank.actualDelayS(k);
                sum += d;
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
        }
        assertThat(n).isEqualTo(10_000);
        assertThat(min).isGreaterThanOrEqualTo(9.0);
        assertThat(max).isLessThanOrEqualTo(11.0);
        assertThat(sum / n).isCloseTo(10.0, within(0.05));
    }

    @Test
    void onDelayContactClosesAfterTheActualDelay() {
        Rig rig = new Rig();
        int coil = rig.node(), lampNode = rig.node();
        int kt = rig.bank.addTimeRelay(TimeRelayType.PNEUMATIC, TimeRelayMode.ON_DELAY, 2, 0, coil, rig.zero);
        int start = rig.bank.addSwitch(rig.plus, coil, false);
        rig.bank.addContact(kt, 0, true, rig.plus, lampNode);
        int lamp = rig.bank.addLamp(lampNode, rig.zero, RelayBank.LAMP_W);
        int n = (int) Math.round(rig.bank.actualDelayS(kt) / DT);
        rig.bank.setSwitch(start, true);
        rig.step(n); // выход реле с тика n
        assertThat(rig.bank.timerOutput(kt)).isTrue();
        assertThat(rig.bank.lampLit(lamp)).isFalse(); // контакт — в тике n
        rig.step(1);
        assertThat(rig.bank.lampLit(lamp)).isTrue();
        rig.bank.setSwitch(start, false);
        rig.step(2);
        assertThat(rig.bank.timerOutput(kt)).isFalse();
        assertThat(rig.bank.lampLit(lamp)).isFalse();
        assertThatThrownBy(() -> rig.bank.setTimeSetpoint(kt, 200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void offDelayPulseAndFlasherModes() {
        Rig rig = new Rig();
        int coil = rig.node();
        int sw = rig.bank.addSwitch(rig.plus, coil, false);
        int off = rig.bank.addTimeRelay(TimeRelayType.ELECTRONIC, TimeRelayMode.OFF_DELAY, 1, 0, coil, rig.zero);
        int pulse = rig.bank.addTimeRelay(TimeRelayType.ELECTRONIC, TimeRelayMode.PULSE, 1, 0, coil, rig.zero);
        int flash = rig.bank.addTimeRelay(TimeRelayType.ELECTRONIC, TimeRelayMode.FLASHER, 0.5, 0, coil, rig.zero);
        assertThat(rig.bank.actualDelayS(off)).isCloseTo(1, within(1e-9));
        rig.bank.setSwitch(sw, true);
        rig.step(1);
        assertThat(rig.bank.timerOutput(off)).isTrue();
        assertThat(rig.bank.timerOutput(pulse)).isTrue();
        assertThat(rig.bank.timerOutput(flash)).isTrue();
        int toggles = 0;
        boolean last = true;
        for (int t = 0; t < 40; t++) { // 2 с под напряжением
            rig.step(1);
            if (rig.bank.timerOutput(flash) != last) toggles++;
            last = rig.bank.timerOutput(flash);
            if (t == 18) assertThat(rig.bank.timerOutput(pulse)).isTrue();
        }
        assertThat(rig.bank.timerOutput(pulse)).isFalse(); // импульс 1 с без перезапуска
        assertThat(toggles).isEqualTo(4); // полупериод 0,5 с
        rig.bank.setSwitch(sw, false);
        rig.step(19);
        assertThat(rig.bank.timerOutput(off)).isTrue();
        assertThat(rig.bank.timerOutput(flash)).isFalse();
        rig.step(2);
        assertThat(rig.bank.timerOutput(off)).isFalse();
    }

    @Test
    @Tag("T-3d-05")
    void sealInCircuitHoldsUntilStopAndDoesNotRestart() {
        Rig rig = new Rig();
        int a = rig.node(), b = rig.node();
        int stop = rig.bank.addSwitch(rig.plus, a, true); // СТОП (НЗ)
        int start = rig.bank.addSwitch(a, b, false); // ПУСК (НО)
        int km = rig.bank.addRelay(RelayKind.GENERAL, 0, b, rig.zero);
        rig.bank.addContact(km, 0, true, a, b); // самоподхват 13-14
        rig.step(2);
        rig.bank.setSwitch(start, true); // тик 0
        rig.step(1);
        rig.bank.setSwitch(start, false);
        for (int t = 1; t < 10; t++) {
            assertThat(rig.bank.isPicked(km)).as("tick %d", t).isTrue();
            rig.step(1);
        }
        assertThat(rig.bank.isPicked(km)).isTrue();
        rig.bank.setSwitch(stop, false); // тик 10
        rig.step(1);
        rig.bank.setSwitch(stop, true);
        assertThat(rig.bank.isPicked(km)).isFalse(); // тик 11
        rig.step(20);
        assertThat(rig.bank.isPicked(km)).isFalse();
        assertThat(rig.bank.opsCount(km)).isEqualTo(1);
    }

    /** Реле с катушкой через ключ и лампой на НЗ 21-22. */
    static boolean nc22AfterWeldedNo14(RelayKind kind) {
        Rig rig = new Rig();
        int coil = rig.node(), lampNode = rig.node(), noNode = rig.node();
        int k = rig.bank.addRelay(kind, 0, coil, rig.zero);
        int sw = rig.bank.addSwitch(rig.plus, coil, true);
        rig.bank.addContact(k, 0, true, rig.plus, noNode); // 11-14
        rig.bank.addContact(k, 1, false, rig.plus, lampNode); // 21-22
        int lamp = rig.bank.addLamp(lampNode, rig.zero, RelayBank.LAMP_W);
        int noLamp = rig.bank.addLamp(noNode, rig.zero, RelayBank.LAMP_W);
        rig.step(2);
        rig.bank.injectFault(k, RelayState.WELDED, 0);
        rig.bank.setSwitch(sw, false);
        rig.step(3);
        assertThat(rig.bank.relayState(k)).isEqualTo(RelayState.WELDED);
        rig.step(1);
        assertThat(rig.bank.lampLit(noLamp)).isTrue(); // сварка держит 11-14
        return rig.bank.lampLit(lamp);
    }

    @Test
    @Tag("T-3d-06")
    void forceGuidedRelayKeepsNormallyClosedContactsOpenWhenWelded() {
        assertThat(nc22AfterWeldedNo14(RelayKind.FORCE_GUIDED)).isFalse(); // отказ обнаруживаем
        assertThat(nc22AfterWeldedNo14(RelayKind.GENERAL)).isTrue(); // отказ скрыт
    }

    @Test
    void faultsInapplicableInTheCurrentPositionWaitForTheTransitionAndReplacementClearsThem() {
        Rig rig = new Rig();
        int coil = rig.node();
        int k = rig.bank.addRelay(RelayKind.GENERAL, 0, coil, rig.zero);
        int sw = rig.bank.addSwitch(rig.plus, coil, false);
        rig.step(1);
        rig.bank.injectFault(k, RelayState.STUCK_PICKED, 0);
        assertThat(rig.bank.relayState(k)).isEqualTo(RelayState.DROPPED);
        rig.bank.setSwitch(sw, true);
        rig.step(1);
        assertThat(rig.bank.relayState(k)).isEqualTo(RelayState.STUCK_PICKED);
        rig.bank.setSwitch(sw, false);
        rig.step(3);
        assertThat(rig.bank.isPicked(k)).isTrue();
        rig.bank.replaceRelay(k);
        rig.step(1);
        assertThat(rig.bank.relayState(k)).isEqualTo(RelayState.DROPPED);
        rig.bank.injectFault(k, RelayState.COIL_OPEN, 0);
        rig.bank.setSwitch(sw, true);
        rig.step(2);
        assertThat(rig.bank.relayState(k)).isEqualTo(RelayState.COIL_OPEN);
        assertThat(rig.bank.coilVoltage(k)).isZero();
        assertThat(rig.bank.supplyCurrent(rig.psu)).isZero(); // обрыв: ток не течёт
    }

    @Test
    void latchingRelayHoldsWithoutPowerAndResetWins() {
        Rig rig = new Rig();
        int set = rig.node(), reset = rig.node();
        int k = rig.bank.addLatchingRelay(0, set, rig.zero, reset, rig.zero);
        int sSet = rig.bank.addSwitch(rig.plus, set, false), sReset = rig.bank.addSwitch(rig.plus, reset, false);
        rig.bank.setSwitch(sSet, true);
        rig.step(1);
        rig.bank.setSwitch(sSet, false);
        rig.step(5);
        assertThat(rig.bank.isPicked(k)).isTrue();
        rig.bank.setSwitch(sSet, true);
        rig.bank.setSwitch(sReset, true);
        rig.step(2);
        assertThat(rig.bank.isPicked(k)).isFalse();
    }

    @Test
    void steppingSwitchCountsPulsesMissesFastOnesAndReturnsHome() {
        Rig rig = new Rig();
        int stepNode = rig.node(), retNode = rig.node(), common = rig.node(), lampNode = rig.node();
        rig.bank.addSwitch(rig.plus, common, true);
        int s = rig.bank.addSteppingSwitch(0, stepNode, rig.zero, retNode, rig.zero, new int[] {common, -1, -1}, true);
        rig.bank.setStepperBank(s, 4, 0, lampNode);
        int lamp = rig.bank.addLamp(lampNode, rig.zero, RelayBank.LAMP_W);
        int pulse = rig.bank.addSwitch(rig.plus, stepNode, false), ret = rig.bank.addSwitch(rig.plus, retNode, false);
        for (int i = 0; i < 3; i++) { // 3 импульса по 2 тика с паузой 2 тика — 5 шагов/с
            rig.bank.setSwitch(pulse, true);
            rig.step(2);
            rig.bank.setSwitch(pulse, false);
            rig.step(2);
        }
        assertThat(rig.bank.stepperPosition(s)).isEqualTo(4);
        assertThat(rig.bank.lampLit(lamp)).isTrue();
        rig.bank.setSwitch(pulse, true); // ещё шаг: 4 → 5
        rig.step(2);
        rig.bank.setSwitch(pulse, false);
        rig.step(2);
        assertThat(rig.bank.stepperPosition(s)).isEqualTo(5);
        rig.bank.setSwitch(pulse, false);
        rig.bank.setSwitch(ret, true);
        rig.step(2 * 21 + 1); // 5 → 25 → 1 со скоростью 10 шагов/с
        assertThat(rig.bank.stepperPosition(s)).isEqualTo(1);
        rig.step(10);
        assertThat(rig.bank.stepperPosition(s)).isEqualTo(1);
    }

    @Test
    void pulsesFasterThanTheStepperLimitAreMissed() {
        Rig rig = new Rig(CFG.withStepperMaxRateHz(5), 5, 1); // 1 шаг за 4 тика
        int stepNode = rig.node();
        int s = rig.bank.addSteppingSwitch(0, stepNode, rig.zero, rig.node(), rig.zero, new int[] {-1, -1, -1}, true);
        int pulse = rig.bank.addSwitch(rig.plus, stepNode, false);
        for (int i = 0; i < 8; i++) { // фронты через 2 тика
            rig.bank.setSwitch(pulse, i % 2 == 0);
            rig.step(1);
        }
        assertThat(rig.bank.stepperPosition(s)).isEqualTo(3);
        assertThat(rig.bank.missedSteps(s)).isEqualTo(2);
    }

    @Test
    void contactBounceOverCountsStepsWithoutAFilter() {
        Rig rig = new Rig(CFG, 5, 7);
        int stepNode = rig.node();
        int s = rig.bank.addSteppingSwitch(0, stepNode, rig.zero, rig.node(), rig.zero, new int[] {-1, -1, -1}, false);
        int pulse = rig.bank.addSwitch(rig.plus, stepNode, false);
        int pulses = 2000;
        for (int i = 0; i < pulses; i++) {
            rig.bank.setSwitch(pulse, true);
            rig.step(2);
            rig.bank.setSwitch(pulse, false);
            rig.step(2);
        }
        double extra = (double) rig.bank.stepperSteps(s) / pulses - 1;
        assertThat(extra).isCloseTo(CFG.bouncePulsesMean(), within(0.1));
    }

    @Test
    void camControllerFollowsTheShaft() {
        Rig rig = new Rig();
        int lampNode = rig.node();
        int c = rig.bank.addCamController(2);
        rig.bank.setCamInterval(c, 0, 0, 90);
        rig.bank.addCamContact(c, 0, true, rig.plus, lampNode);
        int lamp = rig.bank.addLamp(lampNode, rig.zero, RelayBank.LAMP_W);
        rig.bank.setCamShaftRpm(c, 120); // барабан 60 об/мин = 18°/тик
        int lit = 0;
        for (int t = 0; t < 200; t++) { // 10 оборотов барабана
            rig.step(1);
            if (rig.bank.lampLit(lamp)) lit++;
        }
        assertThat(lit).isEqualTo(50); // четверть оборота
        double a = rig.bank.camAngleDeg(c);
        assertThat(Math.min(a, 360 - a)).isLessThan(1e-6);
    }

    @Test
    void pressureSwitchTripsAtTheSetpointAndResetsBelowTheReturnZone() {
        Rig rig = new Rig(CFG, 5, 3);
        int lampNode = rig.node();
        int ps = rig.bank.addProcessSwitch(ProcessSwitchKind.PRESSURE, true, 5e5, 1e6, Double.NaN, 0);
        rig.bank.addProcessContact(ps, true, rig.plus, lampNode);
        int lamp = rig.bank.addLamp(lampNode, rig.zero, RelayBank.LAMP_W);
        double acc = 0.015 * 1e6;
        rig.bank.setProcessValue(ps, 5e5 + 3 * acc);
        rig.step(1);
        assertThat(rig.bank.processMeasured(ps)).isCloseTo(5e5 + 3 * acc, within(acc * 1.5));
        rig.step(1);
        assertThat(rig.bank.lampLit(lamp)).isTrue();
        rig.bank.setProcessValue(ps, 5e5 - 0.5 * 5e4); // внутри зоны возврата — держит
        rig.step(40);
        assertThat(rig.bank.processOutput(ps)).isTrue();
        rig.bank.setProcessValue(ps, 5e5 - 5e4 - 3 * acc);
        rig.step(40);
        assertThat(rig.bank.processOutput(ps)).isFalse();
    }

    @Test
    void thermostatLagsTheProcess() {
        Rig rig = new Rig();
        int ps = rig.bank.addProcessSwitch(ProcessSwitchKind.TEMPERATURE, true, 350, 100, Double.NaN, 0);
        rig.bank.setProcessValue(ps, 300);
        rig.step(1);
        rig.bank.setProcessValue(ps, 400);
        rig.step((int) Math.round(15 / DT)); // τ = 15 с: 63 %
        assertThat(rig.bank.processMeasured(ps)).isCloseTo(300 + 100 * (1 - Math.exp(-1)), within(2.5));
    }

    @Test
    void randomFailuresFollowTheBaseRateAndTheTenDegreeRule() {
        AutomationConfig cfg = CFG.withStationLimits(4096, 128);
        int n = 4000;
        double[] fraction = new double[2];
        for (int hot = 0; hot < 2; hot++) {
            Rig rig = new Rig(cfg, 10, 11 + hot);
            int[] k = new int[n];
            for (int i = 0; i < n; i++) k[i] = rig.bank.addRelay(RelayKind.GENERAL, 0, rig.node(), rig.zero);
            rig.bank.setCabinetTemperature(0, hot == 1 ? 333.15 : 298.15);
            rig.step(1);
            rig.bank.stepSlow(500 * 86400.0); // λ₀·t = 0,25
            int failed = 0;
            for (int i = 0; i < n; i++) {
                RelayState st = rig.bank.relayState(k[i]);
                if (st.isFailed()) failed++;
            }
            fraction[hot] = failed / (double) n;
        }
        // У отпущенных реле сразу проявляются COIL_OPEN и STUCK_DROPPED (0,60 отказов).
        assertThat(fraction[0]).isCloseTo(0.60 * (1 - Math.exp(-0.25)), within(0.025));
        assertThat(fraction[1]).isCloseTo(0.60 * (1 - Math.exp(-1.0)), within(0.03)); // f_T = 4 при 60 °C
    }

    @Test
    void stationLimitIsEnforced() {
        Rig rig = new Rig(CFG.withStationLimits(2, 128), 5, 1);
        rig.bank.addRelay(RelayKind.GENERAL, 0, rig.node(), rig.zero);
        rig.bank.addRelay(RelayKind.GENERAL, 0, rig.node(), rig.zero);
        assertThatThrownBy(() -> rig.bank.addRelay(RelayKind.GENERAL, 0, rig.node(), rig.zero))
                .isInstanceOf(IllegalStateException.class);
    }
}
