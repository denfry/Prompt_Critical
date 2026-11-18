package dev.denfry.promptcritical.sim.mes;

import java.util.ArrayList;
import java.util.List;

/**
 * План выработки на час: какие агрегаты держать в работе и на какой мощности, чтобы прибыль
 * {@code Σ(цена·P − F(P) − плата·выброс·P)} была наибольшей, а отпуск не превышал заявки рынка {@code cap}.
 *
 * <p>Агрегат в работе нагружается до точки, где его приростная стоимость с платой за выбросы равна цене; если отпуск
 * больше заявки, нагрузка урезается распределением по равенству приростных стоимостей на мощность заявки, а теневая
 * цена заявки {@code μ = цена − λ} (условия ККТ для выпуклой задачи). Пуск-останов — жадно: из работы выводится
 * агрегат с наибольшим убытком (с учётом холостого хода {@code a}), пока убыточных не останется. Жадный останов не
 * гарантирует оптимум задачи с целочисленными решениями, но для щита завода его хватает.
 */
public final class ProductionPlanner {
    private ProductionPlanner() {
    }

    /**
     * План.
     *
     * @param output    мощности по порядку списка (0 — агрегат выведен), МВт
     * @param committed агрегат в работе
     * @param shadow    теневая цена заявки μ, у.е./МВт·ч (0 — заявка не ограничивает)
     * @param revenue   выручка, у.е./ч
     * @param fuel      затраты на топливо, у.е./ч
     * @param pollution плата за выбросы, у.е./ч
     * @param emissions выброс, т/ч
     */
    public record Plan(double[] output, boolean[] committed, double shadow, double revenue, double fuel, double pollution,
            double emissions) {
        public double profit() {
            return revenue - fuel - pollution;
        }

        public double total() {
            double s = 0;
            for (double p : output) s += p;
            return s;
        }
    }

    /**
     * @param price цена электроэнергии, у.е./МВт·ч
     * @param fee   плата за выбросы, у.е./т
     * @param cap   заявка рынка (наибольший отпуск), МВт
     */
    public static Plan plan(List<GeneratingUnit> units, double price, double fee, double cap) {
        if (!Double.isFinite(price) || !(fee >= 0) || !Double.isFinite(fee) || !(cap >= 0) || !Double.isFinite(cap))
            throw new IllegalArgumentException("bad market data");
        int n = units.size();
        boolean[] on = new boolean[n];
        java.util.Arrays.fill(on, true);
        while (true) {
            Plan p = solve(units, on, price, fee, cap);
            int worst = -1;
            double worstProfit = -1e-9;
            for (int i = 0; i < n; i++) {
                if (!on[i]) continue;
                GeneratingUnit u = units.get(i);
                double pi = p.output()[i];
                double profit = price * pi - u.cost(pi) - fee * u.emission() * pi;
                if (profit < worstProfit) {
                    worstProfit = profit;
                    worst = i;
                }
            }
            if (worst < 0) return p;
            on[worst] = false;
        }
    }

    private static Plan solve(List<GeneratingUnit> units, boolean[] on, double price, double fee, double cap) {
        int n = units.size();
        List<GeneratingUnit> active = new ArrayList<>();
        for (int i = 0; i < n; i++) if (on[i]) active.add(units.get(i));
        double[] act = new double[active.size()];
        double total = 0;
        for (int i = 0; i < act.length; i++) {
            GeneratingUnit u = active.get(i);
            act[i] = u.outputAt(price, fee * u.emission());
            total += act[i];
        }
        double mu = 0;
        if (total > cap) {
            EconomicDispatch.Result r = EconomicDispatch.dispatch(active, cap, fee);
            act = r.output();
            mu = Math.max(0, price - r.lambda());
        }
        double[] out = new double[n];
        double revenue = 0, fuel = 0, emissions = 0;
        for (int i = 0, k = 0; i < n; i++) {
            if (!on[i]) continue;
            GeneratingUnit u = units.get(i);
            out[i] = act[k++];
            revenue += price * out[i];
            fuel += u.cost(out[i]);
            emissions += u.emission() * out[i];
        }
        return new Plan(out, on.clone(), mu, revenue, fuel, fee * emissions, emissions);
    }
}
