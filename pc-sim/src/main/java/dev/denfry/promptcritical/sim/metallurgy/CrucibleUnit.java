package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник {@code crucible_furnace} (08 §8.9): {@link CrucibleFurnace} на угле. Вместо {@code Combustor} 07 — горн с
 * постоянной тепловой мощностью и КПД тигельной печи [допущение: 400 кВт по топливу, КПД 0,15 — у тигельных печей
 * 7–15 %; уголь 25 МДж/кг]. Горит, только пока в тигле есть шихта. Класс 1 §8.0.3 (SLOW).
 */
public final class CrucibleUnit implements FoundryUnit, FoundryUnit.MeltSource {
    public static final double CAPACITY_KG = 100, LOSS_W_PER_K = 40;
    public static final double BURNER_W = 400e3, EFFICIENCY = 0.15, COAL_LHV = 25e6;
    /** Запас топлива в горне, кг. */
    public static final double FUEL_CAPACITY_KG = 200;

    private final CrucibleFurnace furnace = new CrucibleFurnace(CAPACITY_KG, LOSS_W_PER_K);
    private double fuelKg;
    private boolean burning;

    @Override
    public String deviceType() {
        return FoundrySubsystem.CRUCIBLE;
    }

    @Override
    public void step(FoundrySubsystem shop, FastContext ctx, double slowDt) {
        if (slowDt <= 0) return;
        burning = fuelKg > 0 && furnace.massKg() > 0;
        double share = 0;
        if (burning) {
            double need = BURNER_W / COAL_LHV * slowDt;
            double burnt = Math.min(fuelKg, need);
            fuelKg -= burnt;
            share = burnt / need;
        }
        furnace.stepSlow(slowDt, BURNER_W * EFFICIENCY * share);
    }

    /** Добавить топливо; {@code false} — горн полон. */
    public boolean addFuel(double kg) {
        if (fuelKg + kg > FUEL_CAPACITY_KG) return false;
        fuelKg += kg;
        return true;
    }

    /** Загрузить шихту; {@code false} — другой сплав или не влезает целиком. */
    public boolean charge(CastAlloy alloy, double kg) {
        if (furnace.massKg() + kg > furnace.capacityKg() || furnace.massKg() > 0 && furnace.alloy() != alloy) return false;
        furnace.charge(alloy, kg);
        return true;
    }

    public double fuelKg() {
        return fuelKg;
    }

    public boolean lit() {
        return burning;
    }

    public CrucibleFurnace furnace() {
        return furnace;
    }

    @Override
    public CastAlloy meltAlloy() {
        return furnace.massKg() > 0 ? furnace.alloy() : null;
    }

    @Override
    public double liquidKg() {
        return furnace.liquidFraction() >= 1 ? furnace.massKg() : 0;
    }

    @Override
    public double superheatK() {
        return furnace.superheatK();
    }

    @Override
    public double tap(double kg) {
        return furnace.pour(kg);
    }

    @Override
    public void save(StateWriter out) {
        furnace.save(out);
        out.putDouble("fuel", fuelKg);
        out.putBoolean("burning", burning);
    }

    @Override
    public void load(StateReader in) {
        furnace.load(in);
        fuelKg = in.getDouble("fuel");
        burning = in.getBoolean("burning");
    }
}
