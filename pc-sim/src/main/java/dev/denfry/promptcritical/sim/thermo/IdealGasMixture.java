package dev.denfry.promptcritical.sim.thermo;

import static dev.denfry.promptcritical.sim.thermo.GasSpecies.R_U;

import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Идеальная газовая смесь постоянного объёма (01 §1.3.10): гермооболочка, помещения,
 * азотные подушки. Состояние — моли компонентов и внутренняя энергия U; температура
 * восстанавливается из U по теплоёмкостям NASA ({@link GasSpecies}). Энергия неводяных
 * компонентов отсчитывается от 298,15 К; энергия пара — в системе отсчёта IF97
 * (жидкость в тройной точке), чтобы массообмен с водяным пулом сохранял энергию.
 * Пар — идеальный газ с насыщением: см. {@link #condenseExcessSteam}. Не потокобезопасна.
 */
public final class IdealGasMixture {
    private static final GasSpecies[] SPECIES = GasSpecies.values();
    private static final int WATER = GasSpecies.H2O_VAPOR.ordinal();
    private static final double T_REF = 298.15;
    /** Отсчёт молярной энтальпии: e_k(T) = h_k(T) − OFFSET_k − R_u T. */
    private static final double[] OFFSET = new double[SPECIES.length];

    static {
        for (GasSpecies s : SPECIES) OFFSET[s.ordinal()] = s.enthalpy(T_REF);
        // пар при 298,15 К и p → 0 имеет энтальпию идеального газа IF97 (регион 2)
        OFFSET[WATER] -= GasSpecies.H2O_VAPOR.molarMass() * If97.inRegion(2, 1.0, T_REF).h();
    }

    private final double volume;
    private final double[] moles = new double[SPECIES.length];
    private final double[] sat = new double[WaterTableFormat.SAT_Q];
    private double energy;
    private double t = 293.15;

    /** Пустая смесь объёмом {@code volume}, м³; начальная температура 293,15 К. */
    public IdealGasMixture(double volume) {
        if (!(volume > 0)) throw new IllegalArgumentException("volume must be positive: " + volume);
        this.volume = volume;
    }

    /** Задаёт количество компонента, моль, при неизменной температуре. */
    public void setMoles(GasSpecies s, double n) {
        if (!(n >= 0)) throw new IllegalArgumentException("moles must be non-negative: " + n);
        double tt = temperature();
        moles[s.ordinal()] = n;
        energy = energyAt(tt);
    }

    public double moles(GasSpecies s) {
        return moles[s.ordinal()];
    }

    public double totalMoles() {
        double n = 0;
        for (double m : moles) n += m;
        return n;
    }

    /** Задаёт температуру, К, при неизменном составе (инициализация). */
    public void setTemperature(double temperature) {
        t = clampT(temperature);
        energy = energyAt(t);
    }

    /** Подводит теплоту, Дж (отрицательная — отвод). */
    public void addEnergy(double joules) {
        energy += joules;
    }

    /** Внутренняя энергия смеси, Дж (отсчёт — в описании класса). */
    public double internalEnergy() {
        return energy;
    }

    public double volume() {
        return volume;
    }

    /** Температура, К: Ньютон по U(T) = Σ n_k e_k(T) с тёплым стартом; область 200–6000 К. */
    public double temperature() {
        if (totalMoles() == 0) return t;
        double tt = t;
        for (int it = 0; it < 50; it++) {
            double f = energyAt(tt) - energy, d = heatCapacityV(tt);
            double next = clampT(tt - f / d);
            if (Math.abs(next - tt) < 1e-9 * tt) {
                tt = next;
                break;
            }
            tt = next;
        }
        t = tt;
        return tt;
    }

    /** P = R_u T Σ n_k / V, Па. */
    public double pressure() {
        return R_U * temperature() * totalMoles() / volume;
    }

    public double partialPressure(GasSpecies s) {
        return R_U * temperature() * moles[s.ordinal()] / volume;
    }

    public double moleFraction(GasSpecies s) {
        double n = totalMoles();
        return n > 0 ? moles[s.ordinal()] / n : 0;
    }

    /** Изохорная теплоёмкость смеси при температуре t, Дж/К. */
    public double heatCapacityV(double t) {
        double c = 0;
        for (int k = 0; k < SPECIES.length; k++) {
            if (moles[k] != 0) c += moles[k] * (SPECIES[k].cp(t) - R_U);
        }
        return c;
    }

    /**
     * Конденсирует избыток пара (p_H₂O &gt; p_sat(T)) в жидкость; возвращает массу
     * конденсата, кг. Конденсат уходит насыщенной жидкостью при новой температуре
     * {@link #temperature()} и уносит энергию m·u_f(T) (отсчёт IF97), которую вызывающий
     * передаёт пулу; теплота конденсации остаётся в газе и нагревает его. Новая
     * температура T' — корень баланса энергии с p_H₂O(T') = p_sat(T'). Ниже 273,16 К
     * (лёд не моделируется) и выше T_c конденсации нет.
     */
    public double condenseExcessSteam(WaterProperties w) {
        double nw = moles[WATER];
        if (nw <= 0) return 0;
        double t0 = temperature();
        if (t0 < 273.16 || t0 >= If97.T_C) return 0;
        if (nw <= vaporCapacity(w, t0)) return 0;
        double lo = t0, hi = If97.T_C - 0.01;
        if (vaporCapacity(w, hi) > nw) {
            // точка росы при текущем количестве пара ограничивает T' сверху
            double a = t0, b = hi;
            for (int it = 0; it < 100 && b - a > 1e-9 * b; it++) {
                double m = 0.5 * (a + b);
                if (vaporCapacity(w, m) < nw) a = m;
                else b = m;
            }
            hi = b;
        }
        for (int it = 0; it < 100 && hi - lo > 1e-10 * hi; it++) {
            double m = 0.5 * (lo + hi);
            if (balance(w, m, nw) < 0) lo = m;
            else hi = m;
        }
        double tt = 0.5 * (lo + hi);
        double nv = Math.min(nw, vaporCapacity(w, tt));
        double condensate = (nw - nv) * GasSpecies.H2O_VAPOR.molarMass();
        w.saturationP(w.saturationPressure(tt), sat);
        energy -= condensate * sat[WaterTableFormat.SAT_UF];
        moles[WATER] = nv;
        t = tt;
        return condensate;
    }

    /** Наибольшее количество пара, моль, которое объём держит при температуре t: p_sat(t)V/(R_u t). */
    private double vaporCapacity(WaterProperties w, double t) {
        return w.saturationPressure(t) * volume / (R_U * t);
    }

    /** Баланс энергии после конденсации до насыщения при температуре t: &lt; 0 — t ниже искомой. */
    private double balance(WaterProperties w, double t, double nw) {
        double nv = Math.min(nw, vaporCapacity(w, t));
        w.saturationP(w.saturationPressure(t), sat);
        double e = 0;
        for (int k = 0; k < SPECIES.length; k++) {
            double n = k == WATER ? nv : moles[k];
            if (n != 0) e += n * molarEnergy(k, t);
        }
        e += (nw - nv) * GasSpecies.H2O_VAPOR.molarMass() * sat[WaterTableFormat.SAT_UF];
        return e - energy;
    }

    private double energyAt(double t) {
        double e = 0;
        for (int k = 0; k < SPECIES.length; k++) {
            if (moles[k] != 0) e += moles[k] * molarEnergy(k, t);
        }
        return e;
    }

    private static double molarEnergy(int k, double t) {
        return SPECIES[k].enthalpy(t) - OFFSET[k] - R_U * t;
    }

    private static double clampT(double t) {
        return Math.min(Math.max(t, GasSpecies.T_MIN), GasSpecies.T_MAX);
    }
}
