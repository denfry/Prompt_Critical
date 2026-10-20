package dev.denfry.promptcritical.sim.utilities;

/**
 * Ключи водоподготовки и ВРУ (08 §8.19): {@code water.*}, {@code asu.*}. Ёмкость анионита
 * {@code water.resin_capacity_sba_eq_l} появится вместе с обессоливанием (M9).
 *
 * @param resinCapacitySacEqL       {@code water.resin_capacity_sac_eq_l} — q_раб катионита, экв/л (1,0)
 * @param resinFoulingPerYear       {@code water.resin_fouling_per_year} — потеря ёмкости смолы, доля в физ. год (0,1)
 * @param asuSpecificEnergyKwhNm3   {@code asu.specific_energy_o2_kwh_nm3} — энергия на нм³ O₂ (0,42)
 * @param asuCooldownHoursPhys      {@code asu.cooldown_hours_phys} — захолаживание блока при номинальном
 *                                  компрессоре, физ. ч (36)
 * @param asuLoxHydrocarbonTripPpm  {@code asu.lox_hydrocarbon_trip_ppm} — уставка останова по углеводородам в LOX (1,0)
 */
public record UtilitiesConfig(double resinCapacitySacEqL, double resinFoulingPerYear, double asuSpecificEnergyKwhNm3,
        double asuCooldownHoursPhys, double asuLoxHydrocarbonTripPpm) {
    public UtilitiesConfig {
        if (!(resinCapacitySacEqL > 0)) throw new IllegalArgumentException("resinCapacitySacEqL");
        if (!(resinFoulingPerYear >= 0 && resinFoulingPerYear < 1)) throw new IllegalArgumentException("resinFoulingPerYear");
        if (!(asuSpecificEnergyKwhNm3 > 0)) throw new IllegalArgumentException("asuSpecificEnergyKwhNm3");
        if (!(asuCooldownHoursPhys > 0)) throw new IllegalArgumentException("asuCooldownHoursPhys");
        if (!(asuLoxHydrocarbonTripPpm > 0)) throw new IllegalArgumentException("asuLoxHydrocarbonTripPpm");
    }

    public static UtilitiesConfig defaults() {
        return new UtilitiesConfig(1.0, 0.1, 0.42, 36, 1.0);
    }
}
