package dev.denfry.promptcritical.sim.petrochem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Пиролиз против литературы: молекулярная схема этана Sundaram–Froment при 65 % превращения даёт выходы Ullmann
 * («Ethylene», табл. 4) с точностью до нескольких процентов; литературные выходы остального сырья сходятся по балансу
 * водорода с сырьём; жёсткость поднимает этилен и метан и снижает пропилен (отношение П/Э — обычная мера жёсткости;
 * Мухина, гл. 3); коксование ограничивает пробег 40–60 сутками; выжиг снимает кокс за сутки.
 */
class PyrolysisTest {
    private static final double C = 273.15;

    private static PyrolysisFurnace furnace(PyrolysisFeed f, double cotC, double steam, double tau) {
        PyrolysisFurnace p = new PyrolysisFurnace();
        p.setFeed(f);
        p.setCotK(cotC + C);
        p.setSteamRatio(steam);
        p.setResidenceS(tau);
        p.setMode(PyrolysisFurnace.Mode.RUN);
        return p;
    }

    @Test
    void ethaneMolecularSchemeMatchesTypicalYieldsAtSixtyFivePercentConversion() {
        // COT, при котором схема даёт 62–66 % превращения
        PyrolysisFurnace p = furnace(PyrolysisFeed.ETHANE, 885, 0.3, 0.4);
        double[] y = p.yieldsNow();
        assertThat(p.conversion()).isBetween(0.60, 0.70);
        Map<PyroComponent, Double> ref = PyrolysisFeed.ethaneUllmann();
        double x = p.conversion() / 0.65; // приведение к 65 %
        assertThat(100 * y[PyroComponent.C2H4.ordinal()] / x).isCloseTo(ref.get(PyroComponent.C2H4), within(4.0));
        assertThat(100 * y[PyroComponent.H2.ordinal()] / x).isCloseTo(ref.get(PyroComponent.H2), within(0.8));
        assertThat(100 * y[PyroComponent.CH4.ordinal()] / x).isCloseTo(ref.get(PyroComponent.CH4), within(1.5));
        // селективность по этилену (моль на моль превращённого этана) — около 0,8
        double sel = y[PyroComponent.C2H4.ordinal()] / 28.054 / (p.conversion() / 30.07);
        assertThat(sel).isBetween(0.75, 0.88);
    }

    @Test
    void literatureSlatesConserveHydrogen() {
        assertThat(PyroComponent.hydrogenContent(fractions(PyrolysisFeed.ethaneUllmann())))
                .isCloseTo(PyrolysisFeed.ETHANE.hydrogenFraction(), within(0.005));
        for (PyrolysisFeed f : new PyrolysisFeed[] {PyrolysisFeed.PROPANE, PyrolysisFeed.BUTANE}) {
            double[] y = f.yields(f.highConversion());
            assertThat(PyroComponent.hydrogenContent(y)).as(f.id()).isCloseTo(f.hydrogenFraction(), within(0.006));
        }
        for (PyrolysisFeed f : new PyrolysisFeed[] {PyrolysisFeed.NAPHTHA, PyrolysisFeed.NATURAL_GASOLINE}) {
            assertThat(PyroComponent.hydrogenContent(f.yields(f.lowConversion()))).as(f.id()).isCloseTo(f.hydrogenFraction(), within(0.01));
            assertThat(PyroComponent.hydrogenContent(f.yields(f.highConversion()))).as(f.id()).isCloseTo(f.hydrogenFraction(), within(0.01));
        }
    }

    private static double[] fractions(Map<PyroComponent, Double> m) {
        double[] w = new double[PyroComponent.N];
        m.forEach((c, v) -> w[c.ordinal()] = v / 100);
        return w;
    }

    @ParameterizedTest
    @ValueSource(strings = {"propane", "butane", "naphtha", "natural_gasoline"})
    void severityRaisesEthyleneAndLowersThePropyleneToEthyleneRatio(String id) {
        PyrolysisFeed f = byId(id);
        double[] mild = furnace(f, 800, 0.4, 0.35).yieldsNow(), hard = furnace(f, 860, 0.4, 0.35).yieldsNow();
        assertThat(hard[PyroComponent.C2H4.ordinal()]).isGreaterThan(mild[PyroComponent.C2H4.ordinal()]);
        double peMild = mild[PyroComponent.C3H6.ordinal()] / mild[PyroComponent.C2H4.ordinal()];
        double peHard = hard[PyroComponent.C3H6.ordinal()] / hard[PyroComponent.C2H4.ordinal()];
        assertThat(peHard).isLessThanOrEqualTo(peMild);
    }

    @Test
    void naphthaAtHighSeverityGivesTheClassicSlate() {
        PyrolysisFurnace p = furnace(PyrolysisFeed.NAPHTHA, 850, 0.5, 0.35);
        double[] y = p.yieldsNow();
        // Ullmann, нафта жёсткого режима: этилен 30–34 %, пропилен 12–16 %, метан 14–17 %, бутадиен 4–5 %
        assertThat(100 * y[PyroComponent.C2H4.ordinal()]).isBetween(27.0, 35.0);
        assertThat(100 * y[PyroComponent.C3H6.ordinal()]).isBetween(12.0, 17.0);
        assertThat(100 * y[PyroComponent.CH4.ordinal()]).isBetween(13.0, 17.5);
        assertThat(100 * y[PyroComponent.C4H6.ordinal()]).isBetween(4.0, 5.2);
    }

    @Test
    void moreDilutionSteamRaisesEthylene() {
        double lean = furnace(PyrolysisFeed.NAPHTHA, 840, 0.4, 0.35).yieldsNow()[PyroComponent.C2H4.ordinal()];
        double rich = furnace(PyrolysisFeed.NAPHTHA, 840, 0.8, 0.35).yieldsNow()[PyroComponent.C2H4.ordinal()];
        assertThat(rich).isGreaterThan(lean);
    }

    @Test
    void yieldsSumToOne() {
        for (PyrolysisFeed f : PyrolysisFeed.ALL) {
            double s = 0;
            for (double v : furnace(f, 840, 0.4, 0.35).yieldsNow()) s += v;
            assertThat(s).as(f.id()).isCloseTo(1, within(1e-9));
        }
    }

    @Test
    void cokeLimitsTheRunToFortyToSixtyDaysAndDecokingTakesAboutADay() {
        PyrolysisFurnace p = furnace(PyrolysisFeed.ETHANE, 850, 0.3, 0.35);
        double days = 0;
        while (!p.tmtTrip() && days < 200) {
            p.step(1, 3600, 1.0);
            days += 1 / 24.0;
        }
        assertThat(days).isBetween(40.0, 60.0);
        assertThat(p.feedKgS()).isZero(); // защита по TMT отсекла сырьё
        p.setMode(PyrolysisFurnace.Mode.DECOKE);
        double hours = 0;
        while (p.cokeMm() > 0 && hours < 100) {
            p.step(1, 3600, 0);
            hours++;
        }
        assertThat(hours).isBetween(12.0, 30.0);
        // тяжёлое сырьё коксует быстрее
        PyrolysisFurnace n = furnace(PyrolysisFeed.NAPHTHA, 850, 0.5, 0.35);
        n.step(1, 86400, 1.0);
        PyrolysisFurnace e = furnace(PyrolysisFeed.ETHANE, 850, 0.5, 0.35);
        e.step(1, 86400, 1.0);
        assertThat(n.cokeMm()).isGreaterThan(e.cokeMm());
    }

    @Test
    void quenchExchangerRaisesHighPressureSteamAndTheFurnaceBurnsFuel() {
        PyrolysisFurnace p = furnace(PyrolysisFeed.PROPANE, 850, 0.4, 0.35);
        p.step(1, 1, 1.0);
        // ЗИА: порядка 0,6–1 кг пара ВД на кг сырья (Ullmann, §5.3)
        assertThat(p.hpSteamKgS()).isBetween(0.5, 1.2);
        assertThat(p.fuelW()).isGreaterThan(p.dutyW());
        assertThat(p.dutyW() / 1e6).isBetween(3.0, 7.0); // МДж на кг сырья
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        PyrolysisFurnace p = furnace(PyrolysisFeed.BUTANE, 830, 0.45, 0.3);
        p.setCokeMm(2.5);
        MutableStateTree tree = new MutableStateTree();
        p.save(new TreeStateWriter(tree));
        PyrolysisFurnace c = new PyrolysisFurnace();
        c.load(new TreeStateReader(tree));
        assertThat(c.feed()).isEqualTo(PyrolysisFeed.BUTANE);
        assertThat(c.cotK()).isCloseTo(830 + C, within(1e-9));
        assertThat(c.cokeMm()).isEqualTo(2.5);
        assertThat(c.mode()).isEqualTo(PyrolysisFurnace.Mode.RUN);
    }

    static PyrolysisFeed byId(String id) {
        for (PyrolysisFeed f : PyrolysisFeed.ALL) if (f.id().equals(id)) return f;
        throw new IllegalArgumentException(id);
    }
}
