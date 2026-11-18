package dev.denfry.promptcritical.sim.mes;

import java.util.List;

/**
 * Экономическое распределение нагрузки по равенству приростных стоимостей: при заданной нагрузке {@code D} каждый
 * агрегат в работе несёт мощность, где {@code dF/dP = λ}, а упёршиеся в предел стоят на пределе; λ подбирается
 * делением отрезка пополам так, чтобы сумма мощностей равнялась нагрузке (λ-итерация, Wood &amp; Wollenberg §3.1,
 * §3.7). Потери в сети не учитываются. Сумма {@code P(λ)} не убывает по λ, поэтому бисекция сходится всегда.
 */
public final class EconomicDispatch {
    static final int ITERATIONS = 200;

    private EconomicDispatch() {
    }

    /**
     * Итог распределения.
     *
     * @param lambda   приростная стоимость системы, у.е./МВт·ч
     * @param output   мощности агрегатов по порядку списка, МВт
     * @param cost     суммарные затраты на топливо (без платы за выбросы), у.е./ч
     * @param feasible нагрузка в пределах {@code ΣpMin..ΣpMax}; иначе все агрегаты на ближайшем пределе
     */
    public record Result(double lambda, double[] output, double cost, boolean feasible) {
        public double total() {
            double s = 0;
            for (double p : output) s += p;
            return s;
        }
    }

    /** Распределить нагрузку {@code demand} (МВт) между агрегатами в работе. */
    public static Result dispatch(List<GeneratingUnit> units, double demand) {
        return dispatch(units, demand, 0);
    }

    /**
     * То же с платой за выбросы {@code fee} (у.е./т): к приростной стоимости каждого агрегата добавляется
     * {@code fee·emission}, так что грязные агрегаты разгружаются первыми.
     */
    public static Result dispatch(List<GeneratingUnit> units, double demand, double fee) {
        if (!Double.isFinite(demand) || !(fee >= 0) || !Double.isFinite(fee)) throw new IllegalArgumentException("bad demand or fee");
        int n = units.size();
        double[] p = new double[n];
        if (n == 0) return new Result(0, p, 0, demand <= 0);
        double minSum = 0, maxSum = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (GeneratingUnit u : units) {
            minSum += u.pMin();
            maxSum += u.pMax();
            double adder = fee * u.emission();
            lo = Math.min(lo, u.incremental(u.pMin()) + adder);
            hi = Math.max(hi, u.incremental(u.pMax()) + adder);
        }
        boolean feasible = demand >= minSum - 1e-9 && demand <= maxSum + 1e-9;
        double lambda;
        if (demand <= minSum) lambda = lo;
        else if (demand >= maxSum) lambda = hi;
        else {
            double a = lo - 1, b = hi + 1;
            for (int i = 0; i < ITERATIONS && b - a > 1e-12 * Math.max(1, Math.abs(b)); i++) {
                double mid = 0.5 * (a + b);
                if (sum(units, mid, fee) < demand) a = mid;
                else b = mid;
            }
            lambda = 0.5 * (a + b);
        }
        for (int i = 0; i < n; i++) {
            GeneratingUnit u = units.get(i);
            p[i] = demand <= minSum ? u.pMin() : demand >= maxSum ? u.pMax() : u.outputAt(lambda, fee * u.emission());
        }
        if (feasible) balance(units, p, demand);
        double cost = 0;
        for (int i = 0; i < n; i++) cost += units.get(i).cost(p[i]);
        return new Result(lambda, p, cost, feasible);
    }

    private static double sum(List<GeneratingUnit> units, double lambda, double fee) {
        double s = 0;
        for (GeneratingUnit u : units) s += u.outputAt(lambda, fee * u.emission());
        return s;
    }

    /**
     * Остаток от бисекции (и от линейных агрегатов, которые прыгают от минимума к максимуму) раздать агрегатам с
     * запасом, чтобы баланс мощности сходился точно.
     */
    private static void balance(List<GeneratingUnit> units, double[] p, double demand) {
        double rest = demand;
        for (double v : p) rest -= v;
        for (int i = 0; i < p.length && Math.abs(rest) > 1e-9; i++) {
            GeneratingUnit u = units.get(i);
            double next = Math.max(u.pMin(), Math.min(u.pMax(), p[i] + rest));
            rest -= next - p[i];
            p[i] = next;
        }
    }
}
