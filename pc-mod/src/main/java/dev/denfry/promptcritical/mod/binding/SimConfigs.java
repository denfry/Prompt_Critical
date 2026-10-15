package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.consequence.DamageBudgetConfig;
import dev.denfry.promptcritical.sim.consequence.ServerMode;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.metallurgy.MetallurgyConfig;
import dev.denfry.promptcritical.sim.mining.DrainageConfig;
import dev.denfry.promptcritical.sim.mining.HoistConfig;
import dev.denfry.promptcritical.sim.mining.LongwallConfig;
import dev.denfry.promptcritical.sim.mining.MiningConfig;
import dev.denfry.promptcritical.sim.mining.VeinConfig;
import java.util.HashMap;
import java.util.Map;

/** Доменный адаптер конфига (05 §5.3.6): значения {@link ServerConfig} → {@link SimConfig} с секциями подсистем. */
public final class SimConfigs {
    private SimConfigs() {
    }

    public static SimConfig fromServerConfig() {
        Map<Class<?>, Object> sections = new HashMap<>();
        sections.put(DamageBudgetConfig.class, damage());
        sections.put(MiningConfig.class, new MiningConfig(
                ServerConfig.MINING_METHANE_EMISSION_SCALE.get(), ServerConfig.MINING_DESORPTION_TAU_S.get(),
                ServerConfig.MINING_EXPLOSION_BLAST_FRACTION.get(), ServerConfig.MINING_DUST_PER_TON_KG.get(),
                ServerConfig.MINING_CANARY_UPTAKE_RATIO.get(), ServerConfig.MINING_AIR_VELOCITY_MIN_MS.get(),
                ServerConfig.MINING_AIR_VELOCITY_FACE_MAX_MS.get(), ServerConfig.MINING_ROOF_SPAN_STABLE.get(),
                ServerConfig.MINING_ROOF_SPAN_MEDIUM.get(), ServerConfig.MINING_ROOF_SPAN_WEAK.get()));
        sections.put(HoistConfig.class, new HoistConfig(
                ServerConfig.MINING_HOIST_SF_PEOPLE.get(), ServerConfig.MINING_HOIST_SF_MIXED.get(),
                ServerConfig.MINING_HOIST_SF_CARGO.get(), ServerConfig.MINING_ROPE_N0_CYCLES.get(),
                ServerConfig.MINING_ROPE_CORROSION_PER_DAY.get(), ServerConfig.MINING_ROPE_ITEM_LENGTH_M.get(),
                ServerConfig.MINING_SAFETY_MODE.get()));
        sections.put(DrainageConfig.class, new DrainageConfig(ServerConfig.MINING_WATER_YIELD.get(),
                ServerConfig.MINING_WATER_FILTRATION.get(), ServerConfig.MINING_SUMP_CAPACITY.get(),
                ServerConfig.MINING_WATER_RELEASE_TAU.get(), ServerConfig.MINING_ACID_WATER_SULFUR.get()));
        sections.put(LongwallConfig.class, longwall());
        sections.put(VeinConfig.class, veins());
        sections.put(dev.denfry.promptcritical.sim.plant.SteamPlantConfig.class, new dev.denfry.promptcritical.sim.plant.SteamPlantConfig(
                ServerConfig.BOILER_EXPLOSION_BLAST_FRACTION.get(), dev.denfry.promptcritical.sim.plant.SteamPlantConfig.defaults().feedOpenKgS(),
                dev.denfry.promptcritical.sim.plant.SteamPlantConfig.defaults().feedPressurizedKgS()));
        sections.put(MetallurgyConfig.class, new MetallurgyConfig(ServerConfig.CASTING_CHVORINOV_B_SAND_MIN_CM2.get(),
                ServerConfig.MACHINING_TAYLOR_N.get(), ServerConfig.MACHINING_TAYLOR_C_M_MIN.get()));

        return new SimConfig(ServerConfig.K_SLOW.get(), ServerConfig.K_STORAGE.get(),
                Integer.highestOneBit(ServerConfig.PERF_COMMAND_QUEUE_CAPACITY.get() * 2 - 1),
                ServerConfig.PERF_MAX_COMMANDS_PER_TICK.get(), ServerConfig.PERF_EVENT_BUFFER_CAPACITY.get(),
                ServerConfig.PERF_SNAPSHOT_BUFFERS.get(), ServerConfig.PERF_SLOW_JOIN_TIMEOUT_MS.get(),
                ServerConfig.PERF_BUDGET_MS.get(), sections);
    }

    /** Ключи {@code mining.longwall_*}; до загрузки серверного конфига — по умолчанию. */
    public static LongwallConfig longwall() {
        if (!ServerConfig.SPEC.isLoaded()) return LongwallConfig.defaults();
        LongwallConfig d = LongwallConfig.defaults();
        return new LongwallConfig(ServerConfig.MINING_LONGWALL_SHEARER_KW.get() * 1e3, d.nominalRpm(),
                ServerConfig.MINING_LONGWALL_MAX_FEED.get(), ServerConfig.MINING_LONGWALL_CUT_HEIGHT.get(),
                ServerConfig.MINING_LONGWALL_ROCK_F.get(), ServerConfig.MINING_LONGWALL_SPRAY_WATER.get(),
                ServerConfig.MINING_LONGWALL_SPRAY_DUST_FACTOR.get(), ServerConfig.MINING_LONGWALL_SUPPORT_SET_S.get());
    }

    /** Ключи {@code worldgen.veins.*} и {@code worldgen.coal.*}; до загрузки серверного конфига — по умолчанию. */
    public static VeinConfig veins() {
        if (!ServerConfig.SPEC.isLoaded()) return VeinConfig.defaults();
        Map<String, Double> weights = new HashMap<>();
        ServerConfig.VEIN_WEIGHT.forEach((id, w) -> weights.put(id, ServerConfig.VEIN_ENABLED.get(id).get() ? w.get() : 0.0));
        int seamMin = ServerConfig.COAL_SEAM_THICKNESS_MIN.get();
        return new VeinConfig(ServerConfig.VEINS_ENABLED.get(), ServerConfig.VEINS_CELL_CHUNKS.get(),
                ServerConfig.VEINS_FILL_PROBABILITY.get(), weights, seamMin,
                Math.max(seamMin, ServerConfig.COAL_SEAM_THICKNESS_MAX.get()), ServerConfig.COAL_RANK_GRADIENT_PER_BLOCK.get());
    }

    public static DamageBudgetConfig damage() {
        ServerMode mode = ServerConfig.SERVER_MODE.get();
        double maxPower = ServerConfig.DAMAGE_MAX_EXPLOSION_POWER.get();
        return new DamageBudgetConfig(mode, ServerConfig.DAMAGE_MAX_BLOCKS_PER_TICK.get(),
                ServerConfig.DAMAGE_MAX_BLOCKS_PER_EVENT.get(), ServerConfig.DAMAGE_MAX_BLOCKS_PER_CHUNK_PER_DAY.get(),
                ServerConfig.DAMAGE_MAX_BLOCKS_PER_REGION_PER_DAY.get(), ServerConfig.DAMAGE_MAX_PENDING.get(),
                maxPower < 0 ? mode.defaultMaxExplosionPower : maxPower, ServerConfig.DAMAGE_TNT_REFERENCE_KG.get(),
                ServerConfig.DAMAGE_ITEM_DROP_FRACTION.get(), ServerConfig.DAMAGE_MAX_ITEM_ENTITIES_PER_EVENT.get());
    }

    public static int workerThreads() {
        int t = ServerConfig.PERF_WORKER_THREADS.get();
        return t >= 0 ? t : WorkerPool.defaultThreads(Runtime.getRuntime().availableProcessors());
    }

    public static long topologyBudgetNanos() {
        return ServerConfig.PERF_TOPOLOGY_BUDGET_US.get() * 1_000L;
    }
}
