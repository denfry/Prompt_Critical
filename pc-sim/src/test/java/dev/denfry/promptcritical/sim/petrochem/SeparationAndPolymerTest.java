package dev.denfry.promptcritical.sim.petrochem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Газоразделение и ПЭВД: из пирогаза пропана получается этилен и пропилен полимерной чистоты, деметанизатор
 * холодный (минус 80–100 °C), C3-сплиттер — с летучестью около 1,1; компрессор и щёлочь — по порядку величин
 * промышленных установок; ПЭВД превращает 20–35 % за проход, перегрев выше предела — разложение этилена.
 */
class SeparationAndPolymerTest {
    /** Пирогаз пропана 1 кг/с, моль/с. */
    private static double[] crackedPropane() {
        PyrolysisFurnace p = new PyrolysisFurnace();
        p.setFeed(PyrolysisFeed.PROPANE);
        p.setCotK(850 + 273.15);
        p.setMode(PyrolysisFurnace.Mode.RUN);
        p.step(1, 1, 1.0);
        return PyroComponent.molesFromMass(p.productKgS());
    }

    @Test
    void separationGivesPolymerGradeEthyleneAndPropylene() {
        CrackedGasSeparation.Result r = CrackedGasSeparation.split(crackedPropane());
        assertThat(r.ethylenePurity()).isGreaterThan(0.995);
        assertThat(r.propylenePurity()).isGreaterThan(0.99);
        // почти весь этилен уходит в товарный продукт
        double[] in = crackedPropane();
        double c2h4In = in[PyroComponent.C2H4.ordinal()] + in[PyroComponent.C2H2.ordinal()] * CrackedGasSeparation.ACETYLENE_TO_ETHYLENE;
        assertThat(r.ethylene()[PyroComponent.C2H4.ordinal()] / c2h4In).isGreaterThan(0.97);
        // водород и метан — в топливный газ
        assertThat(r.fuelGas()[PyroComponent.H2.ordinal()]).isCloseTo(in[PyroComponent.H2.ordinal()], within(in[PyroComponent.H2.ordinal()] * 0.1));
        // смола — из закалочной колонны
        assertThat(r.fuelOil()[PyroComponent.FUEL_OIL.ordinal()]).isCloseTo(in[PyroComponent.FUEL_OIL.ordinal()], within(1e-12));
    }

    @Test
    void massIsConserved() {
        double[] in = crackedPropane();
        CrackedGasSeparation.Result r = CrackedGasSeparation.split(in);
        double out = 0;
        for (double[] s : new double[][] {r.fuelGas(), r.ethylene(), r.ethane(), r.propylene(), r.propane(), r.c4(), r.pygas(), r.fuelOil()})
            out += PyroComponent.massKg(s);
        // CO₂ и H₂S ушли со щёлочью; водород — на гидрирование ацетилена (остался в продуктах)
        double removed = in[PyroComponent.CO2.ordinal()] * 0.04401 + in[PyroComponent.H2S.ordinal()] * 0.034081;
        assertThat(out).isCloseTo(PyroComponent.massKg(in) - removed, within(1e-6));
    }

    @Test
    void columnsSitWhereIndustryRunsThem() {
        // средняя температура колонны (K_лк·K_тк = 1): деметанизатор при 3,2 МПа — верх около −95 °C, куб около −5 °C;
        // C2-сплиттер при 1,9 МПа — около минус 30 °C
        assertThat(CrackedGasSeparation.temperatureK(CrackedGasSeparation.DEMETHANIZER) - 273.15).isBetween(-75.0, -30.0);
        assertThat(CrackedGasSeparation.temperatureK(CrackedGasSeparation.C2_SPLITTER) - 273.15).isBetween(-50.0, -10.0);
        double t = CrackedGasSeparation.temperatureK(CrackedGasSeparation.C3_SPLITTER);
        double alpha = PyroComponent.C3H6.wilsonK(t, 1.8e6) / PyroComponent.C3H8.wilsonK(t, 1.8e6);
        assertThat(alpha).isBetween(1.05, 1.25);
    }

    @Test
    void compressorAndCausticAreOfIndustrialOrder() {
        double[] in = crackedPropane();
        CrackedGasSeparation.Result r = CrackedGasSeparation.split(in);
        // компрессор пирогаза: на установке 1 млн т этилена в год (≈ 100 кг/с сырья) — 50–60 МВт, то есть ≈ 0,5 МВт
        // на кг/с сырья
        assertThat(r.compressorW() / 1e6).isBetween(0.3, 0.8);
        assertThat(r.causticKgS()).isPositive().isLessThan(0.01);
    }

    @Test
    void ldpeConvertsTwentyToThirtyFivePercentPerPass() {
        LdpeReactor r = new LdpeReactor();
        r.setRunning(true);
        assertThat(r.step(1, 1.0)).isZero();
        assertThat(r.conversion()).isBetween(0.20, 0.35);
        assertThat(r.peakK() - 273.15).isBetween(270.0, 320.0);
        assertThat(r.density()).isBetween(0.915, 0.930);
        assertThat(r.polymerKgS()).isCloseTo(r.conversion(), within(1e-12));
    }

    @Test
    void tooMuchInitiatorOverheatsTheTubeAndEthyleneDecomposes() {
        LdpeReactor r = new LdpeReactor();
        r.setRunning(true);
        r.setOxygenPpm(100);
        double energy = r.step(1, 1.0);
        assertThat(r.decomposed()).isTrue();
        assertThat(r.running()).isFalse();
        // 4,5 МДж на кг этилена в трубе
        assertThat(energy).isCloseTo(LdpeReactor.INVENTORY_KG * 4.53e6, within(LdpeReactor.INVENTORY_KG * 0.05e6));
        r.setRunning(true);
        assertThat(r.running()).isFalse(); // до ремонта не пустить
        r.repair();
        r.setOxygenPpm(40);
        r.setRunning(true);
        assertThat(r.step(1, 1.0)).isZero();
    }

    @Test
    void higherPressureLowersTheDecompositionLimit() {
        assertThat(LdpeReactor.decompositionK(300)).isLessThan(LdpeReactor.decompositionK(200));
    }
}
