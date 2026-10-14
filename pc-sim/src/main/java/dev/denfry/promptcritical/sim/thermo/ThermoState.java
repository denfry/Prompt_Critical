package dev.denfry.promptcritical.sim.thermo;

/**
 * Изменяемый буфер результата EOS (01 §1.13.1): горячий цикл переиспользует
 * один экземпляр на узел, без аллокаций. Единицы — СИ.
 *
 * <p>В двухфазной области {@code cp}, {@code viscosity}, {@code conductivity}
 * — свойства насыщенной жидкости (их берут корреляции теплообмена). Выше
 * p_c поля насыщения равны значениям в критической точке, а {@code quality}
 * и {@code voidFraction} — 0 или 1 по стороне от критической энтальпии.
 */
public final class ThermoState {
    public double pressure, temperature, density, specificEnergy, enthalpy;
    /** Равновесное паросодержание x_e = (h − h')/(h'' − h'); может быть &lt; 0 и &gt; 1. */
    public double quality;
    /** Объёмное паросодержание (гомогенное равновесие). */
    public double voidFraction;
    public double rhoF, rhoG, hF, hG, tSat, cp, viscosity, conductivity, surfaceTension;
    /** Вязкость насыщенного пара в двухфазной области (двухфазные множители 01 §1.2.4); вне купола — {@code viscosity}. */
    public double viscosityG;
    /** Производные для линеаризации схемы 01 §1.2.6: (∂p/∂ρ)_u и (∂p/∂u)_ρ. */
    public double dPdRhoAtU, dPdUAtRho;
    public int flags;

    public static final int TWO_PHASE = 1, OUT_OF_RANGE = 2, NEAR_CRITICAL = 4;

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }
}
