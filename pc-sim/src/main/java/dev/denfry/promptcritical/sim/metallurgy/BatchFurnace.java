package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.CombustionResult;
import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;

/**
 * Камерная газовая печь с садкой отдельных заготовок: нагревательная {@code reheating_furnace} (08 §8.7.2) и
 * термическая {@code heat_treatment_furnace} (08 §8.7.5). Лумпед SLOW, места садки пронумерованы — мир держит
 * предметы в тех же номерах.
 *
 * <p>Кладка: C·dT/dt = η·Q_гор − Σq_i − k·(T − T₀). Заготовка греется излучением кладки:
 * q_i = h_r·A_i·(T − T_i)/(1 + Bi/3), h_r = εσ(T² + T_i²)(T + T_i), Bi = h_r·(t/2)/λ, A_i = 3m/(ρt) (между плитой
 * 2V/t и квадратом 4V/t) [моё]. Шаг заготовки — точная экспонента при h_r начала шага (тонкий пруток за шаг SLOW
 * прогревается целиком, явная схема бы разошлась). Заготовка 150 мм от 20 °C до 1200 °C — ≈ 1 ч физ. [BALANCE: в жизни 1,5–2 ч].
 */
public final class BatchFurnace {
    public static final double EMISSIVITY = 0.7, SIGMA = 5.670e-8, STEEL_CP = 650, STEEL_LAMBDA = 30, AREA_FACTOR = 3;
    private static final double T0 = 293.15;

    private final int capacity;
    private final double heatCapacityJK, lossWK, efficiency;
    private final double[] massKg, thicknessM, pieceK;
    private final boolean[] used;
    private final CombustionResult burn = new CombustionResult();
    private double tempK = T0, fuelKgS, airKgS, pieceW, firingW;

    /**
     * @param heatCapacityJK теплоёмкость кладки, Дж/К
     * @param lossWK         потери через кладку, Вт/К
     * @param efficiency     доля тепла горения, оставшаяся в печи (остальное — с дымом)
     */
    public BatchFurnace(int capacity, double heatCapacityJK, double lossWK, double efficiency) {
        if (capacity < 1 || !(heatCapacityJK > 0 && lossWK >= 0 && efficiency > 0 && efficiency <= 1))
            throw new IllegalArgumentException("furnace parameters");
        this.capacity = capacity;
        this.heatCapacityJK = heatCapacityJK;
        this.lossWK = lossWK;
        this.efficiency = efficiency;
        massKg = new double[capacity];
        thicknessM = new double[capacity];
        pieceK = new double[capacity];
        used = new boolean[capacity];
    }

    /** Посадить заготовку на место {@code slot}; {@code false} — место занято или вне печи. */
    public boolean load(int slot, double kg, double thickM, double tK) {
        if (slot < 0 || slot >= capacity || used[slot] || !(kg > 0 && thickM > 0 && tK > 0)) return false;
        used[slot] = true;
        massKg[slot] = kg;
        thicknessM[slot] = thickM;
        pieceK[slot] = tK;
        return true;
    }

    /** Снять заготовку с места; вернуть её температуру, К (NaN — место пусто). */
    public double unload(int slot) {
        if (slot < 0 || slot >= capacity || !used[slot]) return Double.NaN;
        used[slot] = false;
        return pieceK[slot];
    }

    public void setHeating(double fuelKgS, double airKgS) {
        this.fuelKgS = Math.max(0, fuelKgS);
        this.airKgS = Math.max(0, airKgS);
    }

    public void stepSlow(double dt, Combustor heating) {
        firingW = 0;
        if (heating != null && fuelKgS > 0) {
            heating.burn(fuelKgS, airKgS, T0, T0, burn);
            firingW = efficiency * burn.heatReleaseW;
        }
        double toPieces = 0;
        for (int i = 0; i < capacity; i++) {
            if (!used[i]) continue;
            double m = massKg[i], t = pieceK[i];
            double hr = EMISSIVITY * SIGMA * (tempK * tempK + t * t) * (tempK + t);
            double bi = hr * thicknessM[i] / 2 / STEEL_LAMBDA;
            double ha = hr * AREA_FACTOR * m / (StockState.DENSITY * thicknessM[i]) / (1 + bi / 3);
            double next = tempK + (t - tempK) * Math.exp(-ha * dt / (m * STEEL_CP));
            toPieces += m * STEEL_CP * (next - t);
            pieceK[i] = next;
        }
        pieceW = toPieces / dt;
        tempK += (firingW * dt - toPieces - lossWK * (tempK - T0) * dt) / heatCapacityJK;
    }

    public double tempK() {
        return tempK;
    }

    public double pieceTempK(int slot) {
        return slot >= 0 && slot < capacity && used[slot] ? pieceK[slot] : Double.NaN;
    }

    public boolean occupied(int slot) {
        return slot >= 0 && slot < capacity && used[slot];
    }

    public int capacity() {
        return capacity;
    }

    public int loaded() {
        int n = 0;
        for (boolean u : used) if (u) n++;
        return n;
    }

    /** Тепло, ушедшее в садку на последнем шаге, Вт. */
    public double pieceHeatW() {
        return pieceW;
    }

    /** Тепло горения, оставшееся в печи на последнем шаге, Вт. */
    public double firingW() {
        return firingW;
    }

    public double heatCapacityJK() {
        return heatCapacityJK;
    }

    public double lossWK() {
        return lossWK;
    }

    public double efficiency() {
        return efficiency;
    }

    public void save(StateWriter out) {
        out.putDoubles("t", new double[] {tempK, fuelKgS, airKgS, pieceW}, 4);
        int[] u = new int[capacity];
        for (int i = 0; i < capacity; i++) u[i] = used[i] ? 1 : 0;
        out.putInts("used", u, capacity);
        out.putDoubles("mass", massKg, capacity);
        out.putDoubles("thick", thicknessM, capacity);
        out.putDoubles("piece_k", pieceK, capacity);
    }

    public void load(StateReader in) {
        double[] t = in.getDoubles("t");
        tempK = t[0];
        fuelKgS = t[1];
        airKgS = t[2];
        pieceW = t[3];
        int[] u = in.getInts("used");
        double[] m = in.getDoubles("mass"), th = in.getDoubles("thick"), pk = in.getDoubles("piece_k");
        Arrays.fill(used, false);
        for (int i = 0; i < Math.min(capacity, u.length); i++) {
            used[i] = u[i] != 0;
            massKg[i] = m[i];
            thicknessM[i] = th[i];
            pieceK[i] = pk[i];
        }
    }
}
