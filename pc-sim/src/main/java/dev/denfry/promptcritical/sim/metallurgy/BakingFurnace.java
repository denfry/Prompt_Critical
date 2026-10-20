package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.CombustionResult;
import dev.denfry.promptcritical.sim.combustion.Combustor;
import java.util.ArrayList;
import java.util.List;

/**
 * Обжиговая печь {@code baking_furnace} (08 §8.4.5): садка сырых электродов в засыпке, обогрев {@link Combustor},
 * 800–1000 °C. Лумпед SLOW: C·dT/dt = η·Q_гор − k·(T − T₀). Нагрев быстрее 10 К/ч физ. в зоне 300–600 °C (выход
 * летучих пека) — трещины у всей садки. Обжиг засчитан после выдержки ≥ 800 °C 24 ч физ. [BALANCE: в жизни весь
 * цикл 2–3 нед.]. Выгрузка горячей садки (&gt; 300 °C) — окисление и термоудар, тоже трещины (моё).
 */
public final class BakingFurnace {
    public static final double CRACK_ZONE_LO_K = 573.15, CRACK_ZONE_HI_K = 873.15, MAX_RATE_K_PER_H = 10;
    public static final double BAKED_K = 1073.15, SOAK_S = 86_400, SAFE_UNLOAD_K = 573.15;
    /** Теплоёмкость печи с засыпкой, Дж/К; потери, Вт/К; доля тепла горения в садку [BALANCE]. */
    public static final double HEAT_CAPACITY_J_K = 5e7, LOSS_W_K = 200, EFFICIENCY = 0.6;

    private static final double T0 = 293.15;
    private final int capacity;
    private final List<ElectrodeState> charge = new ArrayList<>();
    private final CombustionResult burn = new CombustionResult();
    private double tempK = T0, soakS, rateKH, fuelKgS, airKgS;
    private boolean crackedRun;

    public BakingFurnace(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    /** Посадить сырой электрод в холодную печь; {@code false} — печь полна, горяча или электрод не сырой. */
    public boolean load(ElectrodeState e) {
        if (e == null || e.stage() != ElectrodeState.GREEN || charge.size() >= capacity || tempK > SAFE_UNLOAD_K)
            return false;
        if (charge.isEmpty()) {
            soakS = 0;
            crackedRun = false;
        }
        charge.add(e);
        return true;
    }

    public void setHeating(double fuelKgS, double airKgS) {
        this.fuelKgS = Math.max(0, fuelKgS);
        this.airKgS = Math.max(0, airKgS);
    }

    public void stepSlow(double dt, Combustor heating) {
        double heat = 0;
        if (heating != null && fuelKgS > 0) {
            heating.burn(fuelKgS, airKgS, T0, T0, burn);
            heat = EFFICIENCY * burn.heatReleaseW;
        }
        double before = tempK;
        tempK += (heat - LOSS_W_K * (tempK - T0)) * dt / HEAT_CAPACITY_J_K;
        rateKH = (tempK - before) / dt * 3600;
        if (charge.isEmpty()) return;
        boolean inZone = Math.max(before, tempK) > CRACK_ZONE_LO_K && Math.min(before, tempK) < CRACK_ZONE_HI_K;
        if (inZone && rateKH > MAX_RATE_K_PER_H) crackedRun = true;
        if (tempK >= BAKED_K) soakS += dt;
    }

    /** Выгрузить садку: обожжённые — {@code BAKED}, недообожжённые остаются сырыми. */
    public List<ElectrodeState> unload() {
        boolean baked = soakS >= SOAK_S, cracked = crackedRun || tempK > SAFE_UNLOAD_K;
        List<ElectrodeState> out = new ArrayList<>(charge.size());
        for (ElectrodeState e : charge) {
            ElectrodeState r = baked ? e.withStage(ElectrodeState.BAKED) : e;
            out.add(cracked ? r.withCracked() : r);
        }
        charge.clear();
        soakS = 0;
        crackedRun = false;
        return out;
    }

    public double tempK() {
        return tempK;
    }

    /** Скорость нагрева последнего шага, К/ч физ. */
    public double heatingRateKPerH() {
        return rateKH;
    }

    /** Выдержка ≥ 800 °C, с физ. */
    public double soakSeconds() {
        return soakS;
    }

    /** Садка уже треснула. */
    public boolean crackedRun() {
        return crackedRun;
    }

    public int loaded() {
        return charge.size();
    }
}
