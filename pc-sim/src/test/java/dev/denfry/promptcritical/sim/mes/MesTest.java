package dev.denfry.promptcritical.sim.mes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class MesTest {
    /** Wood &amp; Wollenberg, пример 3A: три агрегата, кривые расхода тепла, топливо 1,1 / 1,0 / 1,0 у.е./МБТЕ. */
    private static List<GeneratingUnit> example3A() {
        return List.of(
                GeneratingUnit.fromHeatRate("1", 510, 7.2, 0.00142, 1.1, 150, 600),
                GeneratingUnit.fromHeatRate("2", 310, 7.85, 0.00194, 1.0, 100, 400),
                GeneratingUnit.fromHeatRate("3", 78, 7.97, 0.00482, 1.0, 50, 200));
    }

    @Test
    void lambdaIterationMatchesWoodWollenbergExample3A() {
        EconomicDispatch.Result r = EconomicDispatch.dispatch(example3A(), 850);
        assertThat(r.feasible()).isTrue();
        assertThat(r.lambda()).isCloseTo(9.148, within(0.001));
        assertThat(r.output()[0]).isCloseTo(393.2, within(0.1));
        assertThat(r.output()[1]).isCloseTo(334.6, within(0.1));
        assertThat(r.output()[2]).isCloseTo(122.2, within(0.1));
        assertThat(r.total()).isCloseTo(850, within(1e-6));
        // равные приростные стоимости
        for (int i = 0; i < 3; i++) assertThat(example3A().get(i).incremental(r.output()[i])).isCloseTo(r.lambda(), within(1e-3));
    }

    /** Нагрузка вне диапазона — агрегаты на пределах, признак «не покрыть»; у предела λ не выходит за кривые. */
    @Test
    void limitsAndInfeasibleDemand() {
        EconomicDispatch.Result high = EconomicDispatch.dispatch(example3A(), 2000);
        assertThat(high.feasible()).isFalse();
        assertThat(high.output()).containsExactly(600, 400, 200);
        EconomicDispatch.Result low = EconomicDispatch.dispatch(example3A(), 100);
        assertThat(low.feasible()).isFalse();
        assertThat(low.output()).containsExactly(150, 100, 50);
        // 1150 МВт: агрегат 2 упирается в 400, агрегаты 1 и 3 делят остаток при общей λ
        EconomicDispatch.Result edge = EconomicDispatch.dispatch(example3A(), 1150);
        assertThat(edge.output()[1]).isCloseTo(400, within(1e-6));
        assertThat(edge.total()).isCloseTo(1150, within(1e-6));
        assertThat(example3A().get(0).incremental(edge.output()[0]))
                .isCloseTo(example3A().get(2).incremental(edge.output()[2]), within(1e-3));
        assertThat(example3A().get(1).incremental(400)).isLessThan(edge.lambda());
    }

    /** Агрегаты с линейной кривой (ветряк с нулевыми затратами) грузятся первыми, баланс сходится точно. */
    @Test
    void linearUnitsAreLoadedByMeritOrder() {
        List<GeneratingUnit> units = List.of(GeneratingUnit.of("wind", 0, 0, 0, 0, 50),
                GeneratingUnit.of("gas", 100, 20, 0, 0, 100));
        EconomicDispatch.Result r = EconomicDispatch.dispatch(units, 80);
        assertThat(r.output()[0]).isCloseTo(50, within(1e-6));
        assertThat(r.output()[1]).isCloseTo(30, within(1e-6));
    }

    /** Плата за выбросы разгружает грязный агрегат в пользу чистого. */
    @Test
    void pollutionFeeShiftsLoadToCleanUnit() {
        List<GeneratingUnit> units = List.of(new GeneratingUnit("coal", 0, 10, 0.01, 0, 500, 1.0),
                new GeneratingUnit("gas", 0, 10, 0.01, 0, 500, 0.4));
        EconomicDispatch.Result free = EconomicDispatch.dispatch(units, 400);
        assertThat(free.output()[0]).isCloseTo(free.output()[1], within(1e-6));
        EconomicDispatch.Result taxed = EconomicDispatch.dispatch(units, 400, 5);
        // 5·(1,0 − 0,4) = 3 у.е./МВт·ч разницы → 2·0,01·ΔP = 3, ΔP = 150
        assertThat(taxed.output()[1] - taxed.output()[0]).isCloseTo(150, within(1e-4));
    }

    /**
     * Без заявки рынка агрегаты грузятся до цены: при цене λ примера 3A план совпадает с распределением на 850 МВт.
     * Холостой ход обнулён — с ним при такой цене все три агрегата убыточны и план выводит их из работы.
     */
    @Test
    void plannerLoadsToPriceWithoutCap() {
        List<GeneratingUnit> noLoad = example3A().stream()
                .map(u -> new GeneratingUnit(u.name(), 0, u.b(), u.c(), u.pMin(), u.pMax(), 0)).toList();
        ProductionPlanner.Plan p = ProductionPlanner.plan(noLoad, 9.148, 0, 10_000);
        assertThat(p.shadow()).isZero();
        assertThat(p.total()).isCloseTo(850, within(0.5));
        assertThat(p.profit()).isCloseTo(p.revenue() - p.fuel(), within(1e-9));
        assertThat(ProductionPlanner.plan(example3A(), 9.148, 0, 10_000).committed()).containsExactly(false, false, false);
    }

    /** Заявка рынка ограничивает отпуск; её теневая цена — цена минус λ распределения на заявку. */
    @Test
    void plannerRespectsMarketCap() {
        ProductionPlanner.Plan p = ProductionPlanner.plan(example3A(), 12, 0, 700);
        assertThat(p.total()).isCloseTo(700, within(1e-6));
        double lambda = EconomicDispatch.dispatch(example3A(), 700).lambda();
        assertThat(p.shadow()).isCloseTo(12 - lambda, within(1e-6));
    }

    /** Убыточный агрегат выводится из работы; плата за выбросы может вывести грязный агрегат целиком. */
    @Test
    void plannerDecommitsLossMakingUnits() {
        List<GeneratingUnit> units = List.of(new GeneratingUnit("cheap", 50, 5, 0.01, 10, 300, 0),
                new GeneratingUnit("dear", 500, 30, 0.01, 50, 300, 0),
                new GeneratingUnit("dirty", 50, 8, 0.01, 10, 300, 1.2));
        ProductionPlanner.Plan base = ProductionPlanner.plan(units, 20, 0, 1000);
        assertThat(base.committed()).containsExactly(true, false, true);
        assertThat(base.output()[1]).isZero();
        assertThat(base.profit()).isPositive();
        ProductionPlanner.Plan taxed = ProductionPlanner.plan(units, 20, 15, 1000);
        assertThat(taxed.committed()).containsExactly(true, false, false);
        assertThat(taxed.emissions()).isZero();
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> new GeneratingUnit("x", 0, 1, -1, 0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeneratingUnit("x", 0, 1, 0, 5, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EconomicDispatch.dispatch(example3A(), Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductionPlanner.plan(example3A(), 10, -1, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
