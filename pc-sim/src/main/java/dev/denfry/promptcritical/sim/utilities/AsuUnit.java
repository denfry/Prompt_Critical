package dev.denfry.promptcritical.sim.utilities;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник ВРУ {@code air_separator} (08 §8.11): {@link AirSeparationUnit} под компрессор {@link #SPEC} и то, что
 * задаёт мир — мощность компрессора на валу, охлаждающая вода концевых холодильников, собранность установки
 * (компрессор, блок осушки, детандер рядом). Захолаживание и адсорберы — SLOW (×k_slow); продукт — электротехнология
 * класса 2 §8.0.3: нм³ в реальную секунду по реальной мощности, мир забирает его каждый тик ({@link #productRate}).
 */
public final class AsuUnit implements UtilitiesUnit {
    /** Блок под компрессор 1 МВт (≈ 2380 нм³/ч O₂), 1,15 т LOX в кубе, с аргоном [BALANCE]. */
    public static final AsuSpec SPEC = new AsuSpec(1e6, 1_150, true);

    private final UtilitiesLog log = new UtilitiesLog();
    private final AirSeparationUnit asu;
    private double compressorW, cwKgS;
    private boolean formed;

    public AsuUnit(UtilitiesConfig cfg) {
        asu = new AirSeparationUnit(SPEC, cfg, log);
    }

    @Override
    public String deviceType() {
        return UtilitiesSubsystem.AIR_SEPARATOR;
    }

    @Override
    public void step(UtilitiesSubsystem utilities, FastContext ctx, double slowDt) {
        if (slowDt > 0) asu.stepSlow(slowDt, formed ? compressorW : 0, cwKgS);
    }

    /** Мощность компрессора на валу, Вт, и охлаждающая вода, кг/с. */
    public void setCompressor(double watts, double coolingWaterKgS) {
        compressorW = Math.max(0, watts);
        cwKgS = Math.max(0, coolingWaterKgS);
    }

    /** Охлаждающая вода, которую просит компрессор при мощности {@code watts}, кг/с. */
    public static double coolingWaterNeedKgS(double watts) {
        return Math.max(0, watts) / (4186 * AirSeparationUnit.COOLING_WATER_DT);
    }

    public void setFormed(boolean formed) {
        this.formed = formed;
    }

    public boolean formed() {
        return formed;
    }

    /**
     * Продукт, нм³ (газы) или кг (жидкости) в реальную секунду: {@code oxygen}, {@code nitrogen}, {@code argon},
     * {@code liquid_oxygen}, {@code liquid_nitrogen}.
     */
    public double productRate(String fluidId) {
        return switch (fluidId) {
            case "oxygen" -> asu.oxygenNm3S();
            case "nitrogen" -> asu.nitrogenNm3S();
            case "argon" -> asu.argonNm3S();
            default -> asu.productKgS(fluidId);
        };
    }

    public AirSeparationUnit asu() {
        return asu;
    }

    public UtilitiesLog log() {
        return log;
    }

    public double compressorW() {
        return compressorW;
    }

    @Override
    public void save(StateWriter out) {
        out.putBoolean("formed", formed);
        asu.save(out);
    }

    @Override
    public void load(StateReader in) {
        formed = in.getBoolean("formed");
        asu.load(in);
    }
}
