package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/**
 * ГАЭС против паспорта Загорской ГАЭС: агрегат даёт 200 МВт турбиной и берёт ≈ 220 МВт насосом при напоре 100 м,
 * потери водовода 700 м Ø7,5 м — около процента напора; КПД цикла закачка–выработка 70–80 % (Загорская ГАЭС —
 * 73 %); характеристики насос-турбины; автоматика по частоте.
 */
class PumpedStorageTest {
    private static final double F0 = 50;

    @Test
    void zagorskUnitMatchesItsRatings() {
        PumpTurbine u = PumpTurbine.zagorsk();
        assertThat(u.turbinePowerW()).isCloseTo(200e6, within(2e6));
        assertThat(u.pumpPowerW()).isCloseTo(220e6, within(3e6));
        assertThat(u.turbineFlowM3s()).isBetween(215.0, 235.0);
        assertThat(u.pumpFlowM3s()).isBetween(190.0, 210.0);
    }

    @Test
    void pumpTurbineCharacteristics() {
        PumpTurbine u = PumpTurbine.zagorsk();
        assertThat(u.pumpHeadM(0)).isCloseTo(PumpTurbine.SHUT_OFF_HEAD * 100, within(1e-9));
        assertThat(u.pumpHeadM(u.pumpFlowM3s())).isCloseTo(100, within(1e-9));
        // КПД насоса — пик на расчётной подаче, турбины — у 90 % открытия при расчётном напоре
        assertThat(u.pumpEfficiencyAt(u.pumpFlowM3s())).isGreaterThan(u.pumpEfficiencyAt(0.7 * u.pumpFlowM3s()));
        assertThat(u.turbineEfficiencyAt(0.9, 100)).isEqualTo(u.turbineEfficiency());
        assertThat(u.turbineEfficiencyAt(0.9, 80)).isLessThan(u.turbineEfficiencyAt(0.9, 100));
        assertThat(u.turbineEfficiencyAt(0.3, 100)).isLessThan(0.85);
    }

    @Test
    void penstockLossIsAboutOnePercentOfTheHead() {
        PumpedStorage.Spec s = PumpedStorage.Spec.zagorskUnit();
        double q = s.unit().turbineFlowM3s();
        assertThat(s.lossCoefficient() * q * q).isBetween(0.5, 2.0);
    }

    @Test
    void roundTripEfficiencyIsSeventyToEightyPercent() {
        PumpedStorage p = new PumpedStorage(PumpedStorage.Spec.zagorskUnit());
        p.setMode(PumpedStorage.Mode.PUMP);
        double pumpS = 0;
        while (p.fill() < 1 && pumpS < 48 * 3600) {
            p.step(60, F0, F0);
            pumpS += 60;
        }
        assertThat(p.fill()).isEqualTo(1);
        p.setMode(PumpedStorage.Mode.GENERATE);
        double genS = 0;
        while (p.fill() > 0 && genS < 48 * 3600) {
            p.step(60, F0, F0);
            genS += 60;
        }
        double eta = p.energyOutJ() / p.energyInJ();
        // литература: 70–80 %; Загорская ГАЭС — 73 % с собственными нуждами, пусками и трансформаторами, которых в
        // модели нет, поэтому модель — у верхнего края
        assertThat(eta).isBetween(0.73, 0.80);
        // бассейн на 8 ч турбины: наполняется насосом за 8–11 ч
        assertThat(genS / 3600).isCloseTo(8, within(1.0));
        assertThat(pumpS / 3600).isBetween(8.0, 11.0);
    }

    @Test
    void autoPumpsOnSurplusAndGeneratesOnDeficitWithDroop() {
        PumpedStorage p = new PumpedStorage(PumpedStorage.Spec.zagorskUnit());
        p.setUpperLevelM(4);
        p.step(1, 50.3, F0);
        assertThat(p.state()).isEqualTo(PumpedStorage.State.PUMPING);
        assertThat(p.powerW()).isPositive();
        p.step(1, 50.1, F0); // гистерезис: ещё выше номинала +0,05
        assertThat(p.state()).isEqualTo(PumpedStorage.State.PUMPING);
        p.step(1, 50.0, F0);
        assertThat(p.state()).isEqualTo(PumpedStorage.State.STANDSTILL);
        p.step(1, 49.7, F0);
        assertThat(p.state()).isEqualTo(PumpedStorage.State.GENERATING);
        double small = -p.powerW();
        p.step(1, 49.0, F0);
        assertThat(-p.powerW()).isGreaterThan(2 * small);
        p.step(1, 0, F0); // сети нет
        assertThat(p.state()).isEqualTo(PumpedStorage.State.STANDSTILL);
        assertThat(p.powerW()).isZero();
    }

    @Test
    void fullOrEmptyBasinStopsTheUnitAndTooHighAHeadCannotBePumped() {
        PumpedStorage p = new PumpedStorage(PumpedStorage.Spec.zagorskUnit());
        p.setUpperLevelM(p.spec().drawdownM());
        p.setMode(PumpedStorage.Mode.PUMP);
        p.step(1, F0, F0);
        assertThat(p.state()).isEqualTo(PumpedStorage.State.STANDSTILL);
        p.setUpperLevelM(0);
        p.setMode(PumpedStorage.Mode.GENERATE);
        p.step(1, F0, F0);
        assertThat(p.state()).isEqualTo(PumpedStorage.State.STANDSTILL);
        // напор 150 м на колесо 100 м: выше напора на закрытую задвижку 135 м
        PumpedStorage high = new PumpedStorage(new PumpedStorage.Spec(PumpTurbine.zagorsk(), 150, 5, 1e5, 0, 300, 7.5));
        high.setMode(PumpedStorage.Mode.PUMP);
        high.step(1, F0, F0);
        assertThat(high.state()).isEqualTo(PumpedStorage.State.STANDSTILL);
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        PumpedStorage p = new PumpedStorage(PumpedStorage.Spec.zagorskUnit());
        p.setMode(PumpedStorage.Mode.GENERATE);
        p.setUpperLevelM(3.5);
        p.setTurbineSetpoint(0.6);
        MutableStateTree tree = new MutableStateTree();
        p.save(new TreeStateWriter(tree));
        PumpedStorage c = new PumpedStorage(p.spec());
        c.load(new TreeStateReader(tree));
        assertThat(c.mode()).isEqualTo(PumpedStorage.Mode.GENERATE);
        assertThat(c.upperLevelM()).isEqualTo(3.5);
        assertThat(c.turbineSetpoint()).isEqualTo(0.6);
    }
}
