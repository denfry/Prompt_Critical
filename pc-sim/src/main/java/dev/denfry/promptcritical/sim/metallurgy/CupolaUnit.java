package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;

/**
 * Двойник {@code cupola_furnace} (08 §8.9): {@link CupolaFurnace} и встроенная воздуходувка на валу Create. Дутьё по
 * законам подобия: Q ∝ n, мощность ∝ n³ [BALANCE: 0,25 нм³/с и 2,5 кВт при 64 об/мин — вагранка ≈ 1 т/ч физ.].
 * Печь — класс 1 §8.0.3 (SLOW, ×{@code k_slow}).
 */
public final class CupolaUnit implements FoundryUnit, FoundryUnit.MeltSource {
    /** Ёмкость горна и шахты (металл + кокс), кг. */
    public static final double WELL_CAPACITY_KG = 500, STACK_CAPACITY_KG = 1_000;
    public static final double NOMINAL_RPM = 64, MIN_RPM = 8, MAX_SPEED_RATIO = 4;
    /** Дутьё при номинальной скорости, нм³/с физ. */
    public static final double AIR_NOMINAL_NM3S = 0.25;
    /** Мощность воздуходувки при номинальной скорости, Вт. */
    public static final double BLOWER_NOMINAL_W = 2_500;
    /** Горн светится выше этой температуры, К. */
    private static final double GLOW_K = 1_000;

    public final ShaftInput shaft = new ShaftInput();
    private final CupolaFurnace furnace = new CupolaFurnace(WELL_CAPACITY_KG);
    private double speedRatio;

    @Override
    public String deviceType() {
        return FoundrySubsystem.CUPOLA;
    }

    @Override
    public void step(FoundrySubsystem shop, FastContext ctx, double slowDt) {
        double rpm = shaft.rpm(ctx.tick());
        speedRatio = rpm < MIN_RPM ? 0 : Math.min(MAX_SPEED_RATIO, rpm / NOMINAL_RPM);
        if (slowDt > 0) furnace.stepSlow(slowDt, airNm3S());
    }

    /** Свободное место в шахте, кг. */
    public double stackRoomKg() {
        return Math.max(0, STACK_CAPACITY_KG - furnace.stackIronKg() - furnace.stackCokeKg());
    }

    /** Загрузить колошу; {@code false} — нет места. */
    public boolean charge(double ironKg, double cokeKg) {
        if (ironKg + cokeKg > stackRoomKg()) return false;
        furnace.charge(ironKg, cokeKg);
        return true;
    }

    public double airNm3S() {
        return AIR_NOMINAL_NM3S * speedRatio;
    }

    /** Мощность на валу, Вт. */
    public double powerW() {
        return BLOWER_NOMINAL_W * speedRatio * speedRatio * speedRatio;
    }

    /** Горит кокс под дутьём или светится горн. */
    public boolean lit() {
        return speedRatio > 0 && furnace.stackCokeKg() > 0 || furnace.wellKg() > 0 && furnace.wellTemperatureK() > GLOW_K;
    }

    public CupolaFurnace furnace() {
        return furnace;
    }

    @Override
    public CastAlloy meltAlloy() {
        return furnace.wellKg() > 0 ? CastAlloy.GRAY_IRON : null;
    }

    @Override
    public double liquidKg() {
        return furnace.superheatK() > 0 ? furnace.wellKg() : 0;
    }

    @Override
    public double superheatK() {
        return furnace.superheatK();
    }

    @Override
    public double tap(double kg) {
        return furnace.tap(kg);
    }

    @Override
    public void save(StateWriter out) {
        furnace.save(out);
    }

    @Override
    public void load(StateReader in) {
        furnace.load(in);
    }
}
