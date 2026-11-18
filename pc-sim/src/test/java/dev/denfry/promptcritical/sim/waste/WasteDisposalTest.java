package dev.denfry.promptcritical.sim.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.waste.Repository.Verdict;
import dev.denfry.promptcritical.sim.waste.ScalingFactors.Stream;
import dev.denfry.promptcritical.sim.waste.WasteClassification.Category;
import dev.denfry.promptcritical.sim.waste.WasteClassification.Route;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Классификация (ОСПОРБ-99/2010, GSG-1, ПП РФ № 1069), масштабирующие коэффициенты, распад с Am-241 → Np-237 и пункты
 * захоронения: критерии приёма и пик дозы после закрытия — у приповерхностного он от подвижных C-14, I-129, Tc-99, а
 * не от Cs-137; у глубинного — на порядки ниже при том же инвентаре.
 */
class WasteDisposalTest {
    private static final double Y = LongLivedNuclide.YEAR_S;

    private static WasteInventory solid(double csBqKg, double massKg) {
        return new WasteInventory(new ActivityInventory(Map.of(Emitter.CS137, csBqKg * massKg), massKg, 1.9), Map.of(), massKg / 1900);
    }

    /** Цементная бочка эксплуатационных отходов АЭС: Cs-137 и Co-60 по 10⁹ Бк, 340 кг. */
    private static WasteInventory nppDrum() {
        ActivityInventory g = new ActivityInventory(Map.of(Emitter.CS137, 1e9, Emitter.CO60, 1e9), 340, 1.9);
        return ScalingFactors.characterize(Stream.REACTOR_OPERATIONAL, g, 0.18);
    }

    @Test
    void categoriesFollowOsporbTable() {
        assertThat(WasteClassification.category(solid(5e5, 100), false)).isEqualTo(Category.VLLW);
        assertThat(WasteClassification.category(solid(5e6, 100), false)).isEqualTo(Category.LLW);
        assertThat(WasteClassification.category(solid(5e8, 100), false)).isEqualTo(Category.ILW);
        assertThat(WasteClassification.category(solid(5e10, 100), false)).isEqualTo(Category.HLW);
        // у жидких ОНАО нет, граница НАО — 10³ кБк/кг
        assertThat(WasteClassification.category(solid(5e5, 100), true)).isEqualTo(Category.LLW);
        assertThat(WasteClassification.category(solid(5e6, 100), true)).isEqualTo(Category.ILW);
        // трансурановые: 10⁵ кБк/кг Pu-238 — уже ВАО по своей колонке
        WasteInventory pu = new WasteInventory(new ActivityInventory(Map.of(Emitter.PU238, 2e8 * 10), 10, 2), Map.of(), 0.005);
        assertThat(WasteClassification.category(pu, false)).isEqualTo(Category.HLW);
    }

    @Test
    void disposalClassesAndRoutes() {
        var llw = WasteClassification.classify(solid(5e6, 300), false);
        assertThat(llw.rfClass()).isEqualTo(4);
        assertThat(llw.route()).isEqualTo(Route.NEAR_SURFACE);
        var ilw = WasteClassification.classify(solid(5e8, 300), false);
        assertThat(ilw.rfClass()).isEqualTo(3);
        // долгоживущие α выше 400 Бк/г: САО класса 2 — только глубоко
        WasteInventory alpha = new WasteInventory(new ActivityInventory(Map.of(Emitter.CS137, 5e8 * 300), 300, 1.9),
                Map.of(LongLivedNuclide.PU239, 1e3 * 300_000), 0.16);
        var ll = WasteClassification.classify(alpha, false);
        assertThat(ll.longLived()).isTrue();
        assertThat(ll.rfClass()).isEqualTo(2);
        assertThat(ll.route()).isEqualTo(Route.DEEP_GEOLOGICAL);
        // стеклоблок с 5 кВт/м³ — тепловыделяющие ВАО, класс 1
        WasteInventory hot = new WasteInventory(new ActivityInventory(Map.of(Emitter.CS137, 1e16), 400, 2.75), Map.of(), 0.145);
        assertThat(hot.heatDensityWm3()).isGreaterThan(2000);
        var h = WasteClassification.classify(hot, false);
        assertThat(h.heatGenerating()).isTrue();
        assertThat(h.rfClass()).isEqualTo(1);
        // жидкие — класс 5, на кондиционирование
        assertThat(WasteClassification.classify(solid(5e6, 300), true).route()).isEqualTo(Route.CONDITIONING);
    }

    @Test
    void scalingFactorsFollowYieldsAndHalfLives() {
        ActivityInventory g = new ActivityInventory(Map.of(Emitter.CS137, 1e15), 50, 2.7);
        Map<LongLivedNuclide, Double> dtm = ScalingFactors.difficultToMeasure(Stream.REPROCESSING_HLW, g);
        // равные выходы → отношение активностей = отношение периодов
        assertThat(dtm.get(LongLivedNuclide.TC99) / 1e15).isCloseTo(30.08 / 2.111e5, within(0.1e-4));
        assertThat(dtm.get(LongLivedNuclide.AM241)).isGreaterThan(dtm.get(LongLivedNuclide.PU239));
        // эксплуатационные: Ni-63 — от Co-60
        ActivityInventory npp = new ActivityInventory(Map.of(Emitter.CO60, 1e10), 100, 1);
        assertThat(ScalingFactors.difficultToMeasure(Stream.REACTOR_OPERATIONAL, npp).get(LongLivedNuclide.NI63)).isCloseTo(1e10, within(1.0));
    }

    @Test
    void americiumDecaysIntoNeptunium() {
        WasteInventory w = new WasteInventory(new ActivityInventory(Map.of(), 1, 1), Map.of(LongLivedNuclide.AM241, 1e12), 0.001);
        WasteInventory later = w.decayed(432.6 * Y);
        assertThat(later.bq(LongLivedNuclide.AM241)).isCloseTo(5e11, within(1e8));
        // Np-237: λ_Np/λ_Am · A₀ · (доля распавшихся атомов) ≈ 1e12 · 432,6/2,144e6 · 0,5
        assertThat(later.bq(LongLivedNuclide.NP237)).isCloseTo(1e12 * 432.6 / 2.144e6 * 0.5, within(2e6));
        // тепло α-распада: 1e12 Бк × 5,64 МэВ ≈ 0,9 Вт
        assertThat(w.heatW()).isCloseTo(0.903, within(0.01));
    }

    @Test
    void nearSurfaceAcceptsLowLevelDrumsOnly() {
        Repository ns = new Repository(Repository.Type.NEAR_SURFACE);
        assertThat(ns.accept(new Repository.Package(WasteForm.CEMENT, nppDrum(), true, 1e-4, false))).isEqualTo(Verdict.ACCEPTED);
        assertThat(ns.accept(new Repository.Package(WasteForm.CEMENT, nppDrum(), false, 1e-4, false))).isEqualTo(Verdict.NON_COMPLIANT);
        assertThat(ns.accept(new Repository.Package(WasteForm.CEMENT, nppDrum(), true, 5e-3, false))).isEqualTo(Verdict.DOSE_RATE);
        WasteInventory glass = ScalingFactors.characterize(Stream.REPROCESSING_HLW,
                new ActivityInventory(Map.of(Emitter.CS137, 1e15, Emitter.SR90, 1e15), 50, 2.75), 0.018);
        assertThat(ns.accept(new Repository.Package(WasteForm.BOROSILICATE_GLASS, glass, true, 10, true))).isEqualTo(Verdict.WRONG_CLASS);
        assertThat(ns.packages()).isEqualTo(1);

        Repository deep = new Repository(Repository.Type.DEEP_GEOLOGICAL);
        assertThat(deep.accept(new Repository.Package(WasteForm.BOROSILICATE_GLASS, glass, true, 10, false))).isEqualTo(Verdict.NEEDS_OVERPACK);
        assertThat(deep.accept(new Repository.Package(WasteForm.BOROSILICATE_GLASS, glass, true, 10, true))).isEqualTo(Verdict.ACCEPTED);
        assertThat(deep.heatW()).isCloseTo(glass.heatW(), within(1e-9));
        WasteInventory tooHot = ScalingFactors.characterize(Stream.REPROCESSING_HLW,
                new ActivityInventory(Map.of(Emitter.CS137, 2e16, Emitter.SR90, 2e16), 50, 2.75), 0.018);
        assertThat(tooHot.heatW()).isGreaterThan(Repository.MAX_DEEP_PACKAGE_W);
        assertThat(deep.accept(new Repository.Package(WasteForm.BOROSILICATE_GLASS, tooHot, true, 10, true))).isEqualTo(Verdict.TOO_HOT);
    }

    @Test
    void nearSurfaceDoseIsFromMobileLongLivedNuclidesAndBelowConstraint() {
        Repository ns = new Repository(Repository.Type.NEAR_SURFACE);
        for (int i = 0; i < 1000; i++) ns.accept(new Repository.Package(WasteForm.CEMENT, nppDrum(), true, 1e-4, false));
        assertThat(ns.packages()).isEqualTo(1000);
        // ручей с водосбором 10 км² при 1,5 мм/сут ≈ 5,5 млн м³/год
        double q = 10e6 * 1.5e-3 * 365.25;
        Repository.Peak p = ns.peak(q);
        assertThat(p.svPerY()).isGreaterThan(0).isLessThan(Repository.DOSE_CONSTRAINT_SV_Y);
        assertThat(p.nuclide()).isIn("C14", "I129", "TC99");
        assertThat(p.years()).isGreaterThan(300);
        // Cs-137 до реки не доходит: задержка тысячи лет при T½ 30 лет
        assertThat(ns.travelY(Element.CS)).isGreaterThan(10_000);
        assertThat(ns.travelY(Element.I)).isLessThan(200);
        // в первые 300 лет контейнеры целы — выхода нет
        assertThat(ns.doseSvY(100, q)).isZero();

        Repository deep = new Repository(Repository.Type.DEEP_GEOLOGICAL);
        for (int i = 0; i < 1000; i++) deep.accept(new Repository.Package(WasteForm.CEMENT, nppDrum(), true, 1e-4, false));
        assertThat(deep.peak(q).svPerY()).isLessThan(p.svPerY() / 100);
    }

    @Test
    void repositoryDecaysAndRoundTrips() {
        Repository ns = new Repository(Repository.Type.NEAR_SURFACE);
        ns.accept(new Repository.Package(WasteForm.CEMENT, nppDrum(), true, 1e-4, false));
        double a0 = ns.inventory(WasteForm.CEMENT).emitters().bq(Emitter.CO60);
        ns.decay(5.2714 * Y);
        assertThat(ns.inventory(WasteForm.CEMENT).emitters().bq(Emitter.CO60)).isCloseTo(a0 / 2, within(a0 * 1e-6));
        MutableStateTree t = new MutableStateTree();
        ns.save(new TreeStateWriter(t));
        Repository back = new Repository(Repository.Type.NEAR_SURFACE);
        back.load(new TreeStateReader(t));
        assertThat(back.packages()).isEqualTo(1);
        assertThat(back.inventory(WasteForm.CEMENT).bq(LongLivedNuclide.C14))
                .isCloseTo(ns.inventory(WasteForm.CEMENT).bq(LongLivedNuclide.C14), within(1e-3));
        assertThat(back.heatW()).isCloseTo(ns.heatW(), within(1e-12));
    }

    @Test
    void glassReleasesFarSlowerThanCement() {
        double cement = WasteForm.CEMENT.releasePerYear(340), glass = WasteForm.BOROSILICATE_GLASS.releasePerYear(400);
        assertThat(cement).isBetween(1e-4, 1e-2);
        assertThat(glass).isBetween(1e-7, 1e-4);
        assertThat(WasteForm.COMPACTED.releasePerYear(300)).isEqualTo(WasteForm.FREE_RELEASE_PER_Y);
    }
}
