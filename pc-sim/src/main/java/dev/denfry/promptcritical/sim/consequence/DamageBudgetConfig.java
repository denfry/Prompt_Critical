package dev.denfry.promptcritical.sim.consequence;

/**
 * Лимиты {@link DamageBudget} (04d §4d.2.3), ключи {@code damage.*} и {@code server.mode}.
 *
 * @param maxBlocksPerTick          {@code damage.max_blocks_per_tick}
 * @param maxBlocksPerEvent         {@code damage.max_blocks_per_event}
 * @param maxBlocksPerChunkPerDay   {@code damage.max_blocks_per_chunk_per_day}, игровые сутки = 24 000 тиков
 * @param maxBlocksPerRegionPerDay  {@code damage.max_blocks_per_region_per_day}, регион = 32×32 чанка
 * @param maxPending                {@code damage.max_pending}
 * @param maxExplosionPower         {@code damage.max_explosion_power}, по умолчанию — от режима
 * @param tntReferenceKg            {@code damage.tnt_reference_kg} (§4d.2.4)
 * @param itemDropFraction          {@code damage.item_drop_fraction}
 * @param maxItemEntitiesPerEvent   {@code damage.max_item_entities_per_event}
 */
public record DamageBudgetConfig(ServerMode mode, int maxBlocksPerTick, int maxBlocksPerEvent,
        int maxBlocksPerChunkPerDay, int maxBlocksPerRegionPerDay, int maxPending, double maxExplosionPower,
        double tntReferenceKg, double itemDropFraction, int maxItemEntitiesPerEvent) {

    public DamageBudgetConfig {
        if (maxBlocksPerTick < 0 || maxBlocksPerEvent < 0 || maxBlocksPerChunkPerDay < 0
                || maxBlocksPerRegionPerDay < 0 || maxPending < 0 || maxItemEntitiesPerEvent < 0)
            throw new IllegalArgumentException("damage limits must be ≥ 0");
        if (!(maxExplosionPower >= 0) || !(tntReferenceKg > 0) || !(itemDropFraction >= 0 && itemDropFraction <= 1))
            throw new IllegalArgumentException("damage config");
    }

    public static DamageBudgetConfig defaults(ServerMode mode) {
        return new DamageBudgetConfig(mode, 64, 4096, 1024, 8192, 32768, mode.defaultMaxExplosionPower, 50.0, 0.1, 32);
    }
}
