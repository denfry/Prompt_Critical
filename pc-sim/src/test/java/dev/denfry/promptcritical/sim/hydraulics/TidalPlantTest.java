package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.environment.Tide;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Приливная станция за лунный цикл: La Rance (бассейн 22 км², 24 агрегата по 10 МВт) на приливе со средней величиной
 * 8 м даёт выработку порядка паспортных ≈ 500 ГВт·ч в год (EDF); двусторонний режим даёт больше одностороннего;
 * выработка не выше теоретического предела; сизигия даёт больше квадратуры.
 */
class TidalPlantTest {
    private static final double RHO_G = 1025 * 9.80665;

    /** Энергия за {@code hours} часов прилива {@code tide}, Дж; нагрузка берёт всё. */
    private static double energy(TidalPlant p, Tide tide, double fromHours, double hours) {
        double dt = 60;
        p.setBasinLevelM(tide.heightM(fromHours));
        double e0 = p.energyJ();
        for (double s = 0; s < hours * 3600; s += dt) p.step(dt, tide.heightM(fromHours + s / 3600), Double.POSITIVE_INFINITY);
        return p.energyJ() - e0;
    }

    @Test
    void laRanceYearlyOutputIsOfTheOrderOfItsRecord() {
        Tide tide = new Tide(Tide.Site.semidiurnal(4.0), Tide.Calendar.REAL); // средняя величина 8 м (Сен-Мало)
        TidalPlant p = new TidalPlant(TidalPlant.Spec.laRance());
        p.setMode(TidalPlant.Mode.TWO_WAY);
        double gwh = energy(p, tide, 0, 365.25 * 24) / 3.6e12;
        // EDF: около 500 ГВт·ч в год. Модель — верхняя оценка: без простоев агрегатов (готовность ≈ 0,85–0,9), без
        // графика нагрузки сети и с идеальным управлением; насосного режима, который добавил бы, тоже нет
        assertThat(gwh / 500).isBetween(0.8, 1.6);
    }

    @Test
    void twoWayGivesMoreThanEbbOnlyAndBothStayBelowTheTheoreticalLimit() {
        Tide tide = new Tide(Tide.Site.semidiurnal(2.0), Tide.Calendar.game(96));
        double area = 1e6, days = 8;
        TidalPlant.Spec spec = TidalPlant.Spec.sizedFor(area, 4.0);
        TidalPlant ebb = new TidalPlant(spec), two = new TidalPlant(spec);
        ebb.setMode(TidalPlant.Mode.EBB);
        two.setMode(TidalPlant.Mode.TWO_WAY);
        double eEbb = energy(ebb, tide, 0, days * 24), eTwo = energy(two, tide, 0, days * 24);
        assertThat(eTwo).isGreaterThan(eEbb);
        // предел: две полные сработки призмы за прилив, ρ·g·A·R² за прилив (R = 2·M2 = 4 м)
        double tides = days * 24 / (360 / tide.calendar().speedDegPerHour(Tide.Constituent.M2));
        double limit = RHO_G * area * 4.0 * 4.0 * tides;
        assertThat(eTwo).isLessThan(limit);
        assertThat(eEbb / limit).isBetween(0.10, 0.45);
    }

    @Test
    void springTidesGiveMoreThanNeaps() {
        Tide tide = new Tide(Tide.Site.semidiurnal(2.0), Tide.Calendar.game(96));
        TidalPlant.Spec spec = TidalPlant.Spec.sizedFor(1e6, 4.0);
        double spring = energy(new TidalPlant(spec), tide, 0, 24), neap = energy(new TidalPlant(spec), tide, 2 * 24, 24);
        assertThat(spring).isGreaterThan(1.5 * neap);
    }

    @Test
    void ebbModeCyclesThroughFillingHoldingAndGenerating() {
        Tide tide = new Tide(Tide.Site.semidiurnal(2.0), Tide.Calendar.game(96));
        TidalPlant p = new TidalPlant(TidalPlant.Spec.sizedFor(1e6, 4.0));
        Set<TidalPlant.State> seen = EnumSet.noneOf(TidalPlant.State.class);
        p.setBasinLevelM(tide.heightM(0));
        for (double s = 0; s < 26 * 3600; s += 60) {
            p.step(60, tide.heightM(s / 3600), Double.POSITIVE_INFINITY);
            seen.add(p.state());
            if (p.state() == TidalPlant.State.GENERATING_EBB) assertThat(p.headM()).isPositive();
        }
        assertThat(seen).contains(TidalPlant.State.SLUICING, TidalPlant.State.HOLDING, TidalPlant.State.GENERATING_EBB)
                .doesNotContain(TidalPlant.State.GENERATING_FLOOD);
    }

    @Test
    void loadLimitsTheFlowAndOffModeFollowsTheSea() {
        TidalPlant p = new TidalPlant(TidalPlant.Spec.sizedFor(1e6, 4.0));
        p.setBasinLevelM(2);
        p.step(1, -1, 0);
        p.step(1, -1, 100e3);
        assertThat(p.state()).isEqualTo(TidalPlant.State.GENERATING_EBB);
        assertThat(p.powerW()).isCloseTo(100e3, within(1e-6));
        double h = p.headM();
        assertThat(p.turbineFlowM3s()).isCloseTo(100e3 / (p.efficiency(h, true) * RHO_G * h), within(1e-6));
        p.setMode(TidalPlant.Mode.OFF);
        for (int i = 0; i < 4 * 3600; i++) p.step(1, -1, 0); // отверстия выравнивают 1 км² за пару часов
        assertThat(p.basinLevelM()).isCloseTo(-1, within(0.01));
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        TidalPlant p = new TidalPlant(TidalPlant.Spec.sizedFor(1e6, 4.0));
        p.setMode(TidalPlant.Mode.TWO_WAY);
        p.setBasinLevelM(1.25);
        MutableStateTree tree = new MutableStateTree();
        p.save(new TreeStateWriter(tree));
        TidalPlant c = new TidalPlant(p.spec());
        c.load(new TreeStateReader(tree));
        assertThat(c.mode()).isEqualTo(TidalPlant.Mode.TWO_WAY);
        assertThat(c.basinLevelM()).isEqualTo(1.25);
    }
}
