package dev.denfry.promptcritical.sim.plant;

/**
 * Ключи котельной I0 (07 §7.21) и допущения биндинга.
 *
 * @param explosionBlastFraction  {@code boiler.explosion_blast_fraction} — доля энергии вскипания в ударную волну
 * @param feedOpenKgS             подпитка открытого (без давления) котла, кг/с: наливом через питательный клапан
 * @param feedPressurizedKgS      подпитка котла под давлением инжектором, кг/с [BALANCE]
 */
public record SteamPlantConfig(double explosionBlastFraction, double feedOpenKgS, double feedPressurizedKgS) {
    public SteamPlantConfig {
        if (!(explosionBlastFraction >= 0 && explosionBlastFraction <= 1)) throw new IllegalArgumentException("blast fraction");
        if (!(feedOpenKgS > 0 && feedPressurizedKgS > 0)) throw new IllegalArgumentException("feed rates");
    }

    /**
     * 0,3 в ударную волну (07 §7.9.4); налив 50 кг/с — 10 т воды за ≈ 3,5 мин; инжектор Жиффара 5 кг/с — в 2,7 раза
     * больше номинального испарения котла (1,87 кг/с), как и требовалось от питательных средств паровых котлов.
     */
    public static SteamPlantConfig defaults() {
        return new SteamPlantConfig(0.3, 50, 5);
    }
}
