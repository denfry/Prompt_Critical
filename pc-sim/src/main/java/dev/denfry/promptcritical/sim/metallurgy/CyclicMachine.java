package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;

/**
 * Машина циклического действия на валу Create (08 §8.9): {@code molding_station} — встряхивающая формовочная машина,
 * {@code shakeout_grate} — выбивная решётка. Цикл идёт в реальном времени (механообработка — FAST, §8.0.1),
 * темп ∝ скорости вала; мощность ∝ скорости при номинальном моменте [BALANCE: формовка 8 с и 7,5 кВт, выбивка
 * 4 с и 5,5 кВт при 64 об/мин]. Без работы машина крутится вхолостую — 2 % мощности (03a §3a.2.4).
 */
public final class CyclicMachine implements FoundryUnit {
    public static final double NOMINAL_RPM = 64, MIN_RPM = 8, MAX_SPEED_RATIO = 4, IDLE_SHARE = 0.02;

    private final String deviceType;
    private final double cycleS, ratedW;
    public final ShaftInput shaft = new ShaftInput();
    private boolean loaded;
    private double progress, speedRatio;

    public CyclicMachine(String deviceType, double cycleS, double ratedW) {
        this.deviceType = deviceType;
        this.cycleS = cycleS;
        this.ratedW = ratedW;
    }

    public static CyclicMachine moldingStation() {
        return new CyclicMachine(FoundrySubsystem.MOLDING_STATION, 8, 7_500);
    }

    public static CyclicMachine shakeoutGrate() {
        return new CyclicMachine(FoundrySubsystem.SHAKEOUT_GRATE, 4, 5_500);
    }

    @Override
    public String deviceType() {
        return deviceType;
    }

    @Override
    public void step(FoundrySubsystem shop, FastContext ctx, double slowDt) {
        double rpm = shaft.rpm(ctx.tick());
        speedRatio = rpm < MIN_RPM ? 0 : Math.min(MAX_SPEED_RATIO, rpm / NOMINAL_RPM);
        if (loaded && progress < 1) progress = Math.min(1, progress + ctx.dt() * speedRatio / cycleS);
        else if (!loaded) progress = 0;
    }

    /** Есть ли работа (заготовка, форма); пишет биндинг. */
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    /** Цикл завершён: сбросить и вернуть {@code true}. */
    public boolean takeCycle() {
        if (progress < 1) return false;
        progress = 0;
        return true;
    }

    public double progress() {
        return progress;
    }

    public boolean running() {
        return loaded && speedRatio > 0;
    }

    /** Мощность на валу, Вт. */
    public double powerW() {
        return ratedW * speedRatio * (loaded ? 1 : IDLE_SHARE);
    }

    public double ratedW() {
        return ratedW;
    }

    @Override
    public void save(StateWriter out) {
        out.putDouble("progress", progress);
        out.putBoolean("loaded", loaded);
    }

    @Override
    public void load(StateReader in) {
        progress = in.getDouble("progress");
        loaded = in.getBoolean("loaded");
    }
}
