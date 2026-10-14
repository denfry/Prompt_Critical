package dev.denfry.promptcritical.sim.protection;

/**
 * Ключи {@code protection.*} (01 §1.15, 03b §3b.2.3).
 *
 * @param highFluxSetpointFrac       {@code high_flux.setpoint_frac}
 * @param highFluxLowSetpointFrac    {@code high_flux.low_setpoint_frac} — пусковая уставка
 * @param lowPowerPermissiveFrac     граница разрешения {@code PERM_LOW_POWER} (03b), 10 %
 * @param shortPeriodSetpointS       {@code short_period.setpoint_s}
 * @param highPressureFactor         {@code high_pressure.factor}
 * @param lowPressureMarginPa        {@code low_pressure.margin_pa}
 * @param lowFlowSetpointFrac        {@code low_flow.setpoint_frac}
 * @param highTempSubcoolingMarginK  {@code high_temp.subcooling_margin_k}
 * @param lowDnbrSetpoint            {@code low_dnbr.setpoint}
 * @param lowSgLevelSetpointFrac     {@code low_sg_level.setpoint_frac}
 * @param highContainmentSetpointPa  {@code high_containment.setpoint_pa}
 * @param pumpUnderspeedSetpointFrac {@code pump_underspeed.setpoint_frac}
 * @param turbineTripPermissiveFrac  {@code turbine_trip.permissive_frac}
 * @param seismicPgaG                {@code seismic.pga_g}
 * @param dnbrUncertaintyPPa         {@code dnbr_uncertainty_p_pa} [BALANCE]
 * @param dnbrUncertaintyFlow        {@code dnbr_uncertainty_flow} [BALANCE]
 * @param dnbrUncertaintyTinK        {@code dnbr_uncertainty_tin_k} [BALANCE]
 * @param dnbrUncertaintyPower       {@code dnbr_uncertainty_power} [BALANCE]
 * @param resetDelayS                {@code reset_delay_s} (03b) [BALANCE] — выдержка после снятия последнего сигнала
 * @param latchTimeS                 t_latch — от размыкания питания до освобождения стержней (§1.11.2)
 */
public record ProtectionConfig(double highFluxSetpointFrac, double highFluxLowSetpointFrac,
        double lowPowerPermissiveFrac, double shortPeriodSetpointS, double highPressureFactor,
        double lowPressureMarginPa, double lowFlowSetpointFrac, double highTempSubcoolingMarginK,
        double lowDnbrSetpoint, double lowSgLevelSetpointFrac, double highContainmentSetpointPa,
        double pumpUnderspeedSetpointFrac, double turbineTripPermissiveFrac, double seismicPgaG,
        double dnbrUncertaintyPPa, double dnbrUncertaintyFlow, double dnbrUncertaintyTinK,
        double dnbrUncertaintyPower, double resetDelayS, double latchTimeS) {
    public ProtectionConfig {
        if (!(highFluxSetpointFrac > 0)) throw new IllegalArgumentException("highFluxSetpointFrac");
        if (!(highFluxLowSetpointFrac > 0)) throw new IllegalArgumentException("highFluxLowSetpointFrac");
        if (!(lowPowerPermissiveFrac >= 0)) throw new IllegalArgumentException("lowPowerPermissiveFrac");
        if (!(shortPeriodSetpointS > 0)) throw new IllegalArgumentException("shortPeriodSetpointS");
        if (!(highPressureFactor > 1)) throw new IllegalArgumentException("highPressureFactor");
        if (!(lowPressureMarginPa >= 0)) throw new IllegalArgumentException("lowPressureMarginPa");
        if (!(lowFlowSetpointFrac > 0 && lowFlowSetpointFrac < 1)) throw new IllegalArgumentException("lowFlowSetpointFrac");
        if (!(highTempSubcoolingMarginK >= 0)) throw new IllegalArgumentException("highTempSubcoolingMarginK");
        if (!(lowDnbrSetpoint > 1)) throw new IllegalArgumentException("lowDnbrSetpoint");
        if (!(lowSgLevelSetpointFrac > 0 && lowSgLevelSetpointFrac < 1)) throw new IllegalArgumentException("lowSgLevelSetpointFrac");
        if (!(highContainmentSetpointPa > 0)) throw new IllegalArgumentException("highContainmentSetpointPa");
        if (!(pumpUnderspeedSetpointFrac > 0 && pumpUnderspeedSetpointFrac < 1)) throw new IllegalArgumentException("pumpUnderspeedSetpointFrac");
        if (!(turbineTripPermissiveFrac >= 0)) throw new IllegalArgumentException("turbineTripPermissiveFrac");
        if (!(seismicPgaG > 0)) throw new IllegalArgumentException("seismicPgaG");
        if (!(dnbrUncertaintyPPa >= 0)) throw new IllegalArgumentException("dnbrUncertaintyPPa");
        if (!(dnbrUncertaintyFlow >= 0 && dnbrUncertaintyFlow < 1)) throw new IllegalArgumentException("dnbrUncertaintyFlow");
        if (!(dnbrUncertaintyTinK >= 0)) throw new IllegalArgumentException("dnbrUncertaintyTinK");
        if (!(dnbrUncertaintyPower >= 0)) throw new IllegalArgumentException("dnbrUncertaintyPower");
        if (!(resetDelayS >= 0)) throw new IllegalArgumentException("resetDelayS");
        if (!(latchTimeS >= 0)) throw new IllegalArgumentException("latchTimeS");
    }

    public static ProtectionConfig defaults() {
        return new ProtectionConfig(1.07, 0.25, 0.10, 10, 1.07, 2.4e6, 0.90, 15, 1.45, 0.25, 1.3e5, 0.93, 0.5, 0.1,
                2e5, 0.05, 2, 0.02, 10, 0.15);
    }

    /** Без погрешностей вычислителя DNBR — для сравнения с «настоящей» DNBR §1.4.4. */
    public ProtectionConfig withoutDnbrUncertainty() {
        return new ProtectionConfig(highFluxSetpointFrac, highFluxLowSetpointFrac, lowPowerPermissiveFrac,
                shortPeriodSetpointS, highPressureFactor, lowPressureMarginPa, lowFlowSetpointFrac,
                highTempSubcoolingMarginK, lowDnbrSetpoint, lowSgLevelSetpointFrac, highContainmentSetpointPa,
                pumpUnderspeedSetpointFrac, turbineTripPermissiveFrac, seismicPgaG, 0, 0, 0, 0, resetDelayS, latchTimeS);
    }
}
