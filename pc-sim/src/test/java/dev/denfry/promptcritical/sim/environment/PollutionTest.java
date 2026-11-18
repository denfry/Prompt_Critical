package dev.denfry.promptcritical.sim.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Загрязнение против тест-кейсов раздела 14: T-14-02 подъём факела, T-14-03 шлейф, T-14-04 затухание, T-14-05 электрофильтр, pH дождя. */
class PollutionTest {

    @Test
    void briggsPlumeRiseT14_02() {
        double f = Pollution.buoyancyFlux(10, 2, 423, 293);
        assertThat(f).isCloseTo(30.15, within(0.05));
        assertThat(Pollution.plumeRise(f, 5)).isCloseTo(55.1, within(0.2));
        // за мокрой сероочисткой (325 К) подъём примерно вдвое меньше
        double wet = Pollution.plumeRise(Pollution.buoyancyFlux(10, 2, 325, 293), 5);
        assertThat(wet / 55.1).isBetween(0.4, 0.55);
    }

    @Test
    void gaussianPlumeT14_03() {
        assertThat(Pollution.sigmaY(2010)).isCloseTo(146.7, within(0.5));
        assertThat(Pollution.sigmaZ(2010)).isCloseTo(60.2, within(0.3));
        double c = Pollution.groundConcentration(55.74e-3, 5, 85.1, 2010, 0) * 1e9;
        assertThat(c).isCloseTo(148, within(2.0));
        assertThat(Pollution.groundConcentration(55.74e-3, 5, 85.1, 2010, 200) * 1e9).isLessThan(c);
    }

    @Test
    void decayRatioT14_04() {
        assertThat(PollutionField.decayRatio()).isCloseTo(2.0 / 3, within(1e-3));
    }

    @Test
    void transferConservesMassAndOnlyTimeCleans() {
        PollutionField f = new PollutionField();
        f.set(0, 0, new double[] {1000, 500, 200});
        PollutionField.Air none = new PollutionField.Air() {
            public double concentration(int cx, int cz, int s) {
                return 0;
            }

            public double vegetation(int cx, int cz) {
                return 0;
            }
        };
        double dt = 3 * 3600;
        f.step(dt, none, List.of(), false, 0);
        // перенос массу не меняет: остаётся только самоочистка e^{−k₀t} (плюс удалённые слабые ячейки — их нет)
        assertThat(f.total(0) / 1000).isCloseTo(Math.exp(-PollutionField.K0 * dt), within(0.01));
        assertThat(f.cells()).isGreaterThan(1);
        // по ветру (+x) уходит больше, чем против
        assertThat(f.load(1, 0)[0]).isGreaterThan(f.load(-1, 0)[0]);
    }

    @Test
    void depositionBuildsLoadUnderAPlume() {
        PollutionField f = new PollutionField();
        PollutionField.Air plume = new PollutionField.Air() {
            public double concentration(int cx, int cz, int s) {
                return cx == 0 && cz == 0 && s == 0 ? 148e-9 : 0;
            }

            public double vegetation(int cx, int cz) {
                return 0.5;
            }
        };
        for (int i = 0; i < 24; i++) f.step(3600, plume, List.of(PollutionField.key(0, 0)), false, 0);
        // сутки сухого осаждения 148 мкг/м³ при v_d 0,008 м/с — порядка 100 мг/м² без учёта очистки и переноса
        assertThat(f.load(0, 0)[0]).isBetween(5.0, 110.0);
        assertThat(Pollution.acidIndex(f.load(0, 0)[0], 0)).isGreaterThan(1);
    }

    @Test
    void rainAcidityAndLevels() {
        assertThat(Pollution.rainPh(0, 0)).isCloseTo(5.6, within(1e-9));
        assertThat(Pollution.rainPh(100, 0)).isCloseTo(4.2, within(0.02));
        assertThat(Pollution.level(50, 0, 0, 0, false, 5.6)).isEqualTo(Pollution.Level.HAZE);
        assertThat(Pollution.level(0, 600, 0, 0, false, 5.6)).isEqualTo(Pollution.Level.COUGH);
        assertThat(Pollution.level(0, 0, 0, 0, true, 4.3)).isEqualTo(Pollution.Level.ACID_RAIN);
        assertThat(Pollution.level(0, 3000, 0, 0, false, 5.6)).isEqualTo(Pollution.Level.WEAKNESS);
    }

    @Test
    void cleaningEfficienciesT14_05() {
        assertThat(GasCleaning.esp(5000, 100, 1.5, 1)).isCloseTo(0.9933, within(1e-4));
        assertThat(GasCleaning.esp(5000, 100, 0.3, 1)).isLessThan(0.95);
        assertThat(GasCleaning.cyclone()).isCloseTo(0.8, within(1e-9));
        assertThat(GasCleaning.fgd(10)).isBetween(0.9, 0.93);
        assertThat(GasCleaning.fgd(20)).isGreaterThan(0.99);
    }

    @Test
    void ledgerMethodFactors() {
        EmissionLedger l = new EmissionLedger();
        l.record("boiler@1", EmissionLedger.Category.BOILER, EmissionLedger.Pollutant.CO2, 10, 100);
        l.record("boiler@1", EmissionLedger.Category.BOILER, EmissionLedger.Pollutant.CH4, 0.01, 100);
        assertThat(l.reportedCo2eT()).isCloseTo(1.2 * (1000 + 28) / 1000, within(1e-9));
        l.source("boiler@1", EmissionLedger.Category.BOILER).method = EmissionLedger.Method.MEASURED;
        assertThat(l.reportedCo2eT()).isCloseTo(1.028, within(1e-9));
        l.rollPeriod();
        assertThat(l.reportedCo2eT()).isZero();
    }
}
