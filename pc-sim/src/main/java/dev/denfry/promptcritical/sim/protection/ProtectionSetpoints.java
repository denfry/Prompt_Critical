package dev.denfry.promptcritical.sim.protection;

import static dev.denfry.promptcritical.sim.protection.Comparison.ABOVE;
import static dev.denfry.promptcritical.sim.protection.Comparison.BELOW;
import static dev.denfry.promptcritical.sim.protection.Voting.ONE_OF_ONE;
import static dev.denfry.promptcritical.sim.protection.Voting.ONE_OF_TWO;
import static dev.denfry.promptcritical.sim.protection.Voting.TWO_OF_THREE;
import static dev.denfry.promptcritical.sim.protection.Voting.TWO_OF_TWO;

import java.util.List;

/**
 * Уставки по умолчанию (01 §1.11.3) из ключей конфига и номинала установки.
 *
 * <p>Единицы каналов: мощность, расход, уровень, обороты — доли номинала; давление — Па; температура — К;
 * период — с; ускорение — g; концевики стопорных клапанов — 1 (закрыт) / 0. Зоны возврата (гистерезис)
 * в GDD не заданы — [BALANCE]: порядка погрешности канала, чтобы шум не «дребезжал» срабатыванием.
 */
public final class ProtectionSetpoints {
    private ProtectionSetpoints() {}

    /** Установка с водой под давлением (T2a, ПР-300). */
    public static List<TripSetpoint> pressurizedWater(ProtectionConfig cfg, PlantNominals nom) {
        double highPressure = Math.min(cfg.highPressureFactor() * nom.nominalPressure(),
                nom.safetyValvePressure() - 0.3e6);
        double lowPressure = nom.saturationPressureAtTout() + cfg.lowPressureMarginPa();
        double highTemp = nom.saturationTempAtPnom() - cfg.highTempSubcoolingMarginK();
        return List.of(
                TripSetpoint.discrete(TripReason.MANUAL, ONE_OF_TWO),
                sp(TripReason.HIGH_FLUX, cfg.highFluxSetpointFrac(), ABOVE, TWO_OF_THREE, 0, 0.01, 0),
                sp(TripReason.SHORT_PERIOD, cfg.shortPeriodSetpointS(), BELOW, TWO_OF_THREE, 0, 2, 0),
                sp(TripReason.HIGH_PRIMARY_PRESSURE, highPressure, ABOVE, TWO_OF_THREE, 0, 0.05e6, 0),
                sp(TripReason.LOW_PRIMARY_PRESSURE, lowPressure, BELOW, TWO_OF_THREE, 0.5, 0.05e6, 0),
                sp(TripReason.LOW_COOLANT_FLOW, cfg.lowFlowSetpointFrac(), BELOW, TWO_OF_THREE, 0.3, 0.01,
                        cfg.lowPowerPermissiveFrac()),
                sp(TripReason.HIGH_COOLANT_TEMP, highTemp, ABOVE, TWO_OF_THREE, 0, 0.5, 0),
                sp(TripReason.LOW_DNBR, cfg.lowDnbrSetpoint(), BELOW, TWO_OF_THREE, 0, 0.02, 0),
                sp(TripReason.LOW_SG_LEVEL, cfg.lowSgLevelSetpointFrac(), BELOW, TWO_OF_THREE, 2, 0.01, 0),
                sp(TripReason.HIGH_CONTAINMENT_PRESSURE, cfg.highContainmentSetpointPa(), ABOVE, TWO_OF_THREE, 0, 2e3, 0),
                sp(TripReason.LOSS_OF_DRIVE_POWER, cfg.pumpUnderspeedSetpointFrac(), BELOW, TWO_OF_THREE, 0.5, 0.01, 0),
                sp(TripReason.TURBINE_TRIP, 0.5, ABOVE, TWO_OF_TWO, 0, 0, cfg.turbineTripPermissiveFrac()),
                sp(TripReason.SEISMIC, cfg.seismicPgaG(), ABOVE, TWO_OF_THREE, 0, 0.01, 0),
                TripSetpoint.discrete(TripReason.LUA_REQUEST, ONE_OF_ONE));
    }

    private static TripSetpoint sp(TripReason r, double value, Comparison cmp, Voting voting, double delay,
            double hysteresis, double permissive) {
        return new TripSetpoint(r, value, cmp, voting, delay, hysteresis, permissive, true);
    }
}
