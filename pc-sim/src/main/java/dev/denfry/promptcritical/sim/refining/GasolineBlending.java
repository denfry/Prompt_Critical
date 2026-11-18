package dev.denfry.promptcritical.sim.refining;

import java.util.List;

/**
 * Компаундирование товарного бензина как задача ЛП (09 §9.9.14, 03d §3d.10.3, 14 §14.8.5). Переменные v_i ≥ 0 —
 * расход компонента i в смесь (м³/ч), 0 ≤ v_i ≤ A_i. Ограничения марки линейны по объёму (по массе — сера):
 *
 * <pre>
 * Σ(BON^R_i − R)·v_i ≥ 0,  Σ(BON^M_i − M)·v_i ≥ 0                  смесительные ОЧИ и ОЧМ
 * Σ(RVP_i^1,25 − P_max^1,25)·v_i ≤ 0,  то же с P_min ≥ 0             индекс Chevron RVP^1,25
 * Σρ_i(S_i − S_max)·v_i ≤ 0                                         сера по массе
 * Σ(x_i − x_max)·v_i ≤ 0  (бензол, ароматика, олефины)             по объёму
 * Σ(ρ_i − ρ_min)·v_i ≥ 0,  Σ(ρ_i − ρ_max)·v_i ≤ 0                  плотность
 * </pre>
 *
 * Две постановки: максимум маржи Σ(p − c_i)·v_i (09 §9.9.14, эталоны A–C, тесты T9.27–T9.29) и минимум себестоимости
 * партии заданного расхода Σv_i = V (03d §3d.10.3, T-3d-31). Смесь без объёма считается недопустимой (случай B: АИ-92
 * без алкилата не сделать). Двойственные цены строк — в порядке {@link Spec}.
 *
 * <p><b>Истинное смешение.</b> Смесительные ОЧ уже учитывают большую часть взаимодействия, но не всё: «настоящее»
 * октановое число смеси (стенд и анализатор качества) — линейное по объёму плюс {@value #INTERACTION} поправок
 * Ethyl RT-70 [сверить: Healy et al., 1959]: ОЧИ — a₁(r̄s − r̄·s̄) + a₂(Ō² − Ō²), ОЧМ — b₁(m̄s − m̄·s̄) +
 * b₂(Ō² − Ō²) + b₃(Ā² − Ā²), s = ОЧИ − ОЧМ (чувствительность), O и A — олефины и ароматика, % об. Доля поправки
 * {@value #INTERACTION} — [BALANCE]: рецепт по ЛП на границе ОЧМ уходит из спецификации на ≈ 0,5 пункта, и его
 * приходится корректировать по анализатору (03d §3d.10.3 п. 4); исправленный рецепт дороже оптимума ЛП меньше чем на 1 %.
 */
public final class GasolineBlending {
    /** Коэффициенты RT-70: ОЧИ a₁, a₂ (a₃ = 0); ОЧМ b₁, b₂, b₃. */
    public static final double A1 = 0.03224, A2 = 0.00101, B1 = 0.04450, B2 = 0.00081, B3 = -0.00645;
    /** Доля поправок RT-70 в «истинном» смешении [BALANCE]. */
    public static final double INTERACTION = 0.2;

    private GasolineBlending() {
    }

    /**
     * Компонент смешения ({@code fuel_quality} 09): смесительные ОЧИ и ОЧМ, ДНП (кПа), сера (ppm масс.), бензол,
     * ароматика и олефины (% об.), плотность (кг/м³), наличие (м³/ч), альтернативная стоимость (у.е./м³).
     */
    public record Component(String id, double ron, double mon, double rvpKpa, double sulfurPpm, double benzenePct,
            double aromaticsPct, double olefinsPct, double density, double availableM3h, double cost) {
    }

    /** Требования марки (ГОСТ 32513-2013 класс 5, ГОСТ Р 51105). */
    public record Grade(String id, double ron, double mon, double rvpMinKpa, double rvpMaxKpa, double sulfurMaxPpm,
            double benzeneMaxPct, double aromaticsMaxPct, double olefinsMaxPct, double densityMin, double densityMax) {
    }

    /** Вход оптимизатора (09 §9.15.4 {@code BlendOptimizerInput}): компоненты и марка. */
    public record BlendOptimizerInput(List<Component> components, Grade grade) {
        public BlendOptimizerInput {
            components = List.copyOf(components);
        }
    }

    /** Порядок строк ограничений (и двойственных цен). */
    public enum Spec { RON, MON, RVP_MAX, RVP_MIN, SULFUR, BENZENE, AROMATICS, OLEFINS, DENSITY_MIN, DENSITY_MAX }

    /** Свойства смеси. */
    public record Properties(double volume, double ron, double mon, double rvpKpa, double sulfurPpm, double benzenePct,
            double aromaticsPct, double olefinsPct, double density, double costPerM3) {
        /** В спецификации марки; допуск 1e-4 — округление уставок клапанов, а не послабление марки. */
        public boolean onSpec(Grade g) {
            double e = 1e-4;
            return volume > 0 && ron >= g.ron() - e && mon >= g.mon() - e && rvpKpa <= g.rvpMaxKpa() + e
                    && rvpKpa >= g.rvpMinKpa() - e && sulfurPpm <= g.sulfurMaxPpm() + e && benzenePct <= g.benzeneMaxPct() + e
                    && aromaticsPct <= g.aromaticsMaxPct() + e && olefinsPct <= g.olefinsMaxPct() + e
                    && density >= g.densityMin() - e && density <= g.densityMax() + e;
        }
    }

    /** Решение: расходы компонентов, цель (маржа или себестоимость), свойства по линейной модели, двойственные цены. */
    public record Solution(LinearProgram.Status status, double[] v, double objective, Properties properties, double[] duals) {
        public boolean feasible() {
            return status == LinearProgram.Status.OPTIMAL;
        }
    }

    // ------------------------------------------------------------------ эталонные данные 09 §9.9.14

    public static final Component LSR = new Component("lsr", 68, 66, 80, 3, 1.5, 3, 0, 660, 4, 400);
    public static final Component REFORMATE = new Component("reformate", 98, 88, 28, 0.5, 2.5, 68, 1, 815, 6, 620);
    public static final Component FCC_GASOLINE = new Component("fcc_gasoline", 91.5, 80.5, 55, 8, 0.6, 25, 22, 740, 5, 560);
    public static final Component BUTANE = new Component("butane", 93, 92, 360, 1, 0, 0, 0, 584, 1, 300);
    public static final Component ALKYLATE = new Component("alkylate", 95, 93, 30, 2, 0, 0, 0.5, 700, 2, 700);
    /** Компоненты без алкилата (случаи A, B) и с ним (C). */
    public static final List<Component> BASE = List.of(LSR, REFORMATE, FCC_GASOLINE, BUTANE),
            WITH_ALKYLATE = List.of(LSR, REFORMATE, FCC_GASOLINE, BUTANE, ALKYLATE);
    /** АИ-92 класса 5 (летний): ОЧИ ≥ 92, ОЧМ ≥ 83, ДНП 45–60 кПа, S ≤ 10 ppm; «Нормаль-80»: ОЧИ ≥ 80, ОЧМ ≥ 76. */
    public static final Grade AI92 = new Grade("ai92", 92, 83, 45, 60, 10, 1, 35, 18, 720, 775),
            NORMAL80 = new Grade("normal80", 80, 76, 45, 60, 10, 1, 35, 18, 720, 775);

    // ------------------------------------------------------------------ ЛП

    /** Максимум маржи Σ(p − c_i)·v_i при наличии A_i (09 §9.9.14). */
    public static Solution maximizeMargin(BlendOptimizerInput in, double price) {
        List<Component> cs = in.components();
        double[] obj = new double[cs.size()];
        for (int i = 0; i < obj.length; i++) obj[i] = price - cs.get(i).cost();
        LinearProgram lp = specRows(new LinearProgram(obj), in);
        for (int i = 0; i < obj.length; i++) lp.upperBound(i, cs.get(i).availableM3h());
        LinearProgram.Result r = lp.solve();
        return solution(in, r, r.objective());
    }

    /** Минимум себестоимости смеси расходом {@code totalM3h} (03d §3d.10.3). Цель решения — у.е./ч. */
    public static Solution minimizeCost(BlendOptimizerInput in, double totalM3h) {
        List<Component> cs = in.components();
        double[] obj = new double[cs.size()], ones = new double[cs.size()];
        for (int i = 0; i < obj.length; i++) {
            obj[i] = -cs.get(i).cost();
            ones[i] = 1;
        }
        LinearProgram lp = specRows(new LinearProgram(obj), in).constraint(ones, LinearProgram.Sense.EQ, totalM3h);
        for (int i = 0; i < obj.length; i++) lp.upperBound(i, cs.get(i).availableM3h());
        LinearProgram.Result r = lp.solve();
        return solution(in, r, -r.objective());
    }

    private static Solution solution(BlendOptimizerInput in, LinearProgram.Result r, double objective) {
        double total = 0;
        for (double x : r.x()) total += x;
        if (!r.optimal() || total < 1e-9)
            return new Solution(r.optimal() ? LinearProgram.Status.INFEASIBLE : r.status(), r.x(), Double.NaN, null, r.duals());
        return new Solution(r.status(), r.x(), objective, linear(in.components(), r.x()), r.duals());
    }

    private static LinearProgram specRows(LinearProgram lp, BlendOptimizerInput in) {
        List<Component> cs = in.components();
        Grade g = in.grade();
        int n = cs.size();
        double[][] rows = new double[Spec.values().length][n];
        double pMax = Math.pow(g.rvpMaxKpa(), 1.25), pMin = Math.pow(g.rvpMinKpa(), 1.25);
        for (int i = 0; i < n; i++) {
            Component c = cs.get(i);
            double idx = Math.pow(c.rvpKpa(), 1.25);
            rows[Spec.RON.ordinal()][i] = c.ron() - g.ron();
            rows[Spec.MON.ordinal()][i] = c.mon() - g.mon();
            rows[Spec.RVP_MAX.ordinal()][i] = idx - pMax;
            rows[Spec.RVP_MIN.ordinal()][i] = idx - pMin;
            rows[Spec.SULFUR.ordinal()][i] = c.density() * (c.sulfurPpm() - g.sulfurMaxPpm());
            rows[Spec.BENZENE.ordinal()][i] = c.benzenePct() - g.benzeneMaxPct();
            rows[Spec.AROMATICS.ordinal()][i] = c.aromaticsPct() - g.aromaticsMaxPct();
            rows[Spec.OLEFINS.ordinal()][i] = c.olefinsPct() - g.olefinsMaxPct();
            rows[Spec.DENSITY_MIN.ordinal()][i] = c.density() - g.densityMin();
            rows[Spec.DENSITY_MAX.ordinal()][i] = c.density() - g.densityMax();
        }
        for (Spec s : Spec.values()) {
            LinearProgram.Sense sense = switch (s) {
                case RON, MON, RVP_MIN, DENSITY_MIN -> LinearProgram.Sense.GE;
                default -> LinearProgram.Sense.LE;
            };
            lp.constraint(rows[s.ordinal()], sense, 0);
        }
        return lp;
    }

    // ------------------------------------------------------------------ свойства смеси

    /** Свойства смеси объёмов {@code v} по линейной модели ЛП (смесительные ОЧ, индекс RVP^1,25). */
    public static Properties linear(List<Component> cs, double[] v) {
        return properties(cs, v, 0);
    }

    /** «Истинные» свойства смеси: линейная модель плюс доля {@link #INTERACTION} поправок RT-70 к ОЧИ и ОЧМ. */
    public static Properties actual(List<Component> cs, double[] v) {
        return properties(cs, v, INTERACTION);
    }

    private static Properties properties(List<Component> cs, double[] v, double k) {
        double vol = 0, mass = 0;
        for (int i = 0; i < cs.size(); i++) {
            vol += Math.max(0, v[i]);
            mass += Math.max(0, v[i]) * cs.get(i).density();
        }
        if (vol <= 0) return new Properties(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        double r = 0, m = 0, s = 0, rs = 0, ms = 0, o = 0, o2 = 0, a = 0, a2 = 0, rvp = 0, sul = 0, bz = 0, rho = 0, cost = 0;
        for (int i = 0; i < cs.size(); i++) {
            Component c = cs.get(i);
            double x = Math.max(0, v[i]) / vol, sens = c.ron() - c.mon();
            r += x * c.ron();
            m += x * c.mon();
            s += x * sens;
            rs += x * c.ron() * sens;
            ms += x * c.mon() * sens;
            o += x * c.olefinsPct();
            o2 += x * c.olefinsPct() * c.olefinsPct();
            a += x * c.aromaticsPct();
            a2 += x * c.aromaticsPct() * c.aromaticsPct();
            rvp += x * Math.pow(c.rvpKpa(), 1.25);
            sul += Math.max(0, v[i]) * c.density() * c.sulfurPpm();
            bz += x * c.benzenePct();
            rho += x * c.density();
            cost += x * c.cost();
        }
        double ron = r + k * (A1 * (rs - r * s) + A2 * (o2 - o * o));
        double mon = m + k * (B1 * (ms - m * s) + B2 * (o2 - o * o) + B3 * (a2 - a * a));
        return new Properties(vol, ron, mon, Math.pow(rvp, 1 / 1.25), sul / mass, bz, a, o, rho, cost);
    }
}
