package dev.denfry.promptcritical.mod.config;

import dev.denfry.promptcritical.sim.consequence.ServerMode;
import dev.denfry.promptcritical.sim.mining.HoistConfig;
import dev.denfry.promptcritical.sim.mining.VeinCatalog;
import dev.denfry.promptcritical.sim.mining.VeinType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code promptcritical-server.toml} (05 §5.3.6). Ключи — {@code section.key} (00 §0.10). Секции 07 §7.21 и 08 §8.19
 * — только для эпохи I0 (вехи M2); остальные добавляются вместе с подсистемами, которые их читают.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;

    // --- 00, 01, 02 ---
    public static final ModConfigSpec.DoubleValue K_SLOW;
    public static final ModConfigSpec.DoubleValue K_STORAGE;
    public static final ModConfigSpec.DoubleValue K_BURN;
    public static final ModConfigSpec.EnumValue<DecayHeatClock> DECAY_HEAT_CLOCK;
    public static final ModConfigSpec.DoubleValue W_PER_SU;
    public static final ModConfigSpec.DoubleValue J_PER_FE;
    public static final ModConfigSpec.IntValue CC_TOKEN_RATE;
    public static final ModConfigSpec.IntValue CC_TOKEN_BURST;

    // --- 05 perf ---
    public static final ModConfigSpec.IntValue PERF_WORKER_THREADS;
    public static final ModConfigSpec.IntValue PERF_SLOW_JOIN_TIMEOUT_MS;
    public static final ModConfigSpec.IntValue PERF_COMMAND_QUEUE_CAPACITY;
    public static final ModConfigSpec.IntValue PERF_MAX_COMMANDS_PER_TICK;
    public static final ModConfigSpec.IntValue PERF_EVENT_BUFFER_CAPACITY;
    public static final ModConfigSpec.IntValue PERF_SNAPSHOT_BUFFERS;
    public static final ModConfigSpec.DoubleValue PERF_BUDGET_MS;
    public static final ModConfigSpec.IntValue PERF_TOPOLOGY_BUDGET_US;

    // --- 04d ---
    public static final ModConfigSpec.EnumValue<ServerMode> SERVER_MODE;
    public static final ModConfigSpec.IntValue DAMAGE_MAX_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue DAMAGE_MAX_BLOCKS_PER_EVENT;
    public static final ModConfigSpec.IntValue DAMAGE_MAX_BLOCKS_PER_CHUNK_PER_DAY;
    public static final ModConfigSpec.IntValue DAMAGE_MAX_BLOCKS_PER_REGION_PER_DAY;
    public static final ModConfigSpec.IntValue DAMAGE_MAX_PENDING;
    public static final ModConfigSpec.DoubleValue DAMAGE_MAX_EXPLOSION_POWER;
    public static final ModConfigSpec.DoubleValue DAMAGE_TNT_REFERENCE_KG;
    public static final ModConfigSpec.DoubleValue DAMAGE_ITEM_DROP_FRACTION;
    public static final ModConfigSpec.IntValue DAMAGE_MAX_ITEM_ENTITIES_PER_EVENT;

    // --- 07 §7.21 (I0) ---
    public static final ModConfigSpec.DoubleValue COMBUSTION_SOLID_FUEL_ITEM_MASS_KG;
    public static final ModConfigSpec.DoubleValue COMBUSTION_THERMAL_NOX_REF_G_PER_GJ;
    public static final ModConfigSpec.DoubleValue COMBUSTION_CO_TRANSITION_WIDTH;
    public static final ModConfigSpec.DoubleValue BOILER_EXPLOSION_BLAST_FRACTION;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_MAX_ANGLE_DEG;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_MAX_DV;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_MAX_SLIP_HZ;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_TORQUE_DAMAGE_PU;
    public static final ModConfigSpec.DoubleValue GRID_COUPLING_SHEAR_PU;
    public static final ModConfigSpec.DoubleValue GRID_AR_SUCCESS_LIGHTNING;
    public static final ModConfigSpec.DoubleValue GRID_LV_BREAKER_THERMAL_K_S;
    public static final ModConfigSpec.DoubleValue BOILER_FURNACE_EXPLOSION_BLAST_FRACTION;
    public static final ModConfigSpec.DoubleValue BOILER_PURGE_MIN_S;
    public static final ModConfigSpec.IntValue BOILER_PURGE_MIN_VOLUME_CHANGES;

    // --- 08 §8.19 (I0) ---
    public static final ModConfigSpec.DoubleValue METALLURGY_BULK_ITEM_MASS_KG;
    public static final ModConfigSpec.DoubleValue METALLURGY_INGOT_MASS_KG;
    public static final ModConfigSpec.DoubleValue METALLURGY_WIRE_ITEM_MASS_KG;
    public static final ModConfigSpec.BooleanValue VEINS_ENABLED;
    public static final ModConfigSpec.IntValue VEINS_CELL_CHUNKS;
    public static final ModConfigSpec.DoubleValue VEINS_FILL_PROBABILITY;
    public static final Map<String, ModConfigSpec.BooleanValue> VEIN_ENABLED = new LinkedHashMap<>();
    public static final Map<String, ModConfigSpec.DoubleValue> VEIN_WEIGHT = new LinkedHashMap<>();
    public static final ModConfigSpec.IntValue COAL_SEAM_THICKNESS_MIN;
    public static final ModConfigSpec.IntValue COAL_SEAM_THICKNESS_MAX;
    public static final ModConfigSpec.DoubleValue COAL_RANK_GRADIENT_PER_BLOCK;
    public static final ModConfigSpec.IntValue MINING_ROOF_SPAN_STABLE;
    public static final ModConfigSpec.IntValue MINING_ROOF_SPAN_MEDIUM;
    public static final ModConfigSpec.IntValue MINING_ROOF_SPAN_WEAK;
    public static final ModConfigSpec.DoubleValue MINING_METHANE_EMISSION_SCALE;
    public static final ModConfigSpec.DoubleValue MINING_DESORPTION_TAU_S;
    public static final ModConfigSpec.DoubleValue MINING_METHANE_UTILIZATION_MIN_FRAC;
    public static final ModConfigSpec.DoubleValue MINING_AGZ_TRIP_FRAC;
    public static final ModConfigSpec.DoubleValue MINING_LOCAL_CH4_LIMIT_FRAC;
    public static final ModConfigSpec.DoubleValue MINING_CANARY_UPTAKE_RATIO;
    public static final ModConfigSpec.DoubleValue MINING_DUST_PER_TON_KG;
    public static final ModConfigSpec.DoubleValue MINING_EXPLOSION_BLAST_FRACTION;
    public static final ModConfigSpec.DoubleValue MINING_AIR_VELOCITY_MIN_MS;
    public static final ModConfigSpec.DoubleValue MINING_AIR_VELOCITY_FACE_MAX_MS;
    public static final ModConfigSpec.DoubleValue MINING_HOIST_SF_PEOPLE;
    public static final ModConfigSpec.DoubleValue MINING_HOIST_SF_MIXED;
    public static final ModConfigSpec.DoubleValue MINING_HOIST_SF_CARGO;
    public static final ModConfigSpec.DoubleValue MINING_ROPE_N0_CYCLES;
    public static final ModConfigSpec.DoubleValue MINING_ROPE_CORROSION_PER_DAY;
    public static final ModConfigSpec.DoubleValue MINING_ROPE_ITEM_LENGTH_M;
    public static final ModConfigSpec.EnumValue<HoistConfig.SafetyMode> MINING_SAFETY_MODE;
    public static final ModConfigSpec.DoubleValue MINING_SEAM_GAS_CONTENT;
    public static final ModConfigSpec.DoubleValue MINING_BARRIER_INERT_KG_PER_BLOCK;
    public static final ModConfigSpec.IntValue MINING_MAX_MINE_BLOCKS;
    public static final ModConfigSpec.BooleanValue MINING_ROOF_COLLAPSE;
    public static final ModConfigSpec.DoubleValue MINING_WATER_YIELD;
    public static final ModConfigSpec.DoubleValue MINING_WATER_FILTRATION;
    public static final ModConfigSpec.DoubleValue MINING_SUMP_CAPACITY;
    public static final ModConfigSpec.DoubleValue MINING_WATER_RELEASE_TAU;
    public static final ModConfigSpec.DoubleValue MINING_ACID_WATER_SULFUR;
    public static final ModConfigSpec.DoubleValue MINING_LONGWALL_SHEARER_KW;
    public static final ModConfigSpec.DoubleValue MINING_LONGWALL_MAX_FEED;
    public static final ModConfigSpec.IntValue MINING_LONGWALL_CUT_HEIGHT;
    public static final ModConfigSpec.DoubleValue MINING_LONGWALL_ROCK_F;
    public static final ModConfigSpec.DoubleValue MINING_LONGWALL_SPRAY_WATER;
    public static final ModConfigSpec.DoubleValue MINING_LONGWALL_SPRAY_DUST_FACTOR;
    public static final ModConfigSpec.DoubleValue MINING_LONGWALL_SUPPORT_SET_S;
    public static final ModConfigSpec.DoubleValue CASTING_CHVORINOV_B_SAND_MIN_CM2;
    public static final ModConfigSpec.DoubleValue MACHINING_TAYLOR_N;
    public static final ModConfigSpec.DoubleValue MACHINING_TAYLOR_C_M_MIN;

    // --- 04d §4d.11, 11 §11.10 ---
    public static final ModConfigSpec.EnumValue<DifficultyPreset> DIFFICULTY_PRESET;
    public static final ModConfigSpec.BooleanValue CONTENT_GIVE_HANDBOOK_ON_JOIN;
    public static final ModConfigSpec.BooleanValue CONTENT_HANDBOOK_ALL_UNLOCKED;
    public static final ModConfigSpec.BooleanValue CONTENT_HINTS_BY_DIFFICULTY;

    static {
        var b = new ModConfigSpec.Builder();

        b.push("time");
        K_SLOW = b.comment("[BALANCE] Physical seconds per real second in the SLOW domain (00 §0.4)")
                .defineInRange("k_slow", 72.0, 1.0, 3600.0);
        b.pop();

        b.push("storage");
        K_STORAGE = b.comment("[BALANCE] Physical seconds per real second in the STORAGE domain; 8766 = 1 real hour per physical year (02)")
                .defineInRange("k_storage", 8766.0, 1.0, 1.0e6);
        b.pop();

        b.push("nuclide");
        K_BURN = b.comment("[BALANCE] Burnup acceleration; deliberately breaks the heat/burnup energy balance (01 §1.9)")
                .defineInRange("k_burn", 4.0, 1.0, 100.0);
        DECAY_HEAT_CLOCK = b.comment("[BALANCE] SPLIT: slow ANS groups run on K_slow; REAL_TIME: hardcore (01 §1.9.1)")
                .defineEnum("decay_heat_clock", DecayHeatClock.SPLIT);
        b.pop();

        b.push("energy");
        W_PER_SU = b.comment("[BALANCE] Watts of shaft power per Create stress unit (00 §0.9, 00 §0.13 R4)")
                .defineInRange("w_per_su", 1000.0, 1.0, 1.0e6);
        J_PER_FE = b.comment("[BALANCE] Joules per Forge Energy unit (00 §0.9)")
                .defineInRange("j_per_fe", 2.5, 0.01, 1.0e6);
        b.pop();

        b.push("cc");
        CC_TOKEN_RATE = b.comment("[BALANCE] Peripheral call token refill rate, tokens/s (03b §3b.1.4)")
                .defineInRange("token_rate", 400, 10, 100_000);
        CC_TOKEN_BURST = b.comment("[BALANCE] Peripheral call token bucket capacity (03b §3b.1.4)")
                .defineInRange("token_burst", 800, 10, 200_000);
        b.pop();

        b.push("perf");
        PERF_WORKER_THREADS = b.comment("Worker threads for SLOW/STORAGE jobs; -1 = max(1, min(2, CPUs - 2)), 0 = server thread only (05 §5.4.2). Restart required")
                .defineInRange("worker_threads", -1, -1, 64);
        PERF_SLOW_JOIN_TIMEOUT_MS = b.comment("Wait for a late SLOW job before the server thread runs the rest itself, ms (05 §5.4.2)")
                .defineInRange("slow_join_timeout_ms", 5, 0, 1000);
        PERF_COMMAND_QUEUE_CAPACITY = b.comment("Command queue capacity, rounded up to a power of two (05 §5.4.3). Restart required")
                .defineInRange("command_queue_capacity", 4096, 16, 1 << 20);
        PERF_MAX_COMMANDS_PER_TICK = b.comment("Commands applied per tick; the rest wait for the next tick (05 §5.4.3)")
                .defineInRange("max_commands_per_tick", 512, 1, 1 << 20);
        PERF_EVENT_BUFFER_CAPACITY = b.comment("Simulation events per tick (05 §5.2.6). Restart required")
                .defineInRange("event_buffer_capacity", 4096, 16, 1 << 20);
        PERF_SNAPSHOT_BUFFERS = b.comment("Snapshot ring size for lock-free readers (05 §5.2.7). Restart required")
                .defineInRange("snapshot_buffers", 4, 3, 16);
        PERF_BUDGET_MS = b.comment("FAST tick budget per dimension for degradation levels, ms (00 §0.6, 05 §5.4.6)")
                .defineInRange("budget_ms", 2.0, 0.1, 50.0);
        PERF_TOPOLOGY_BUDGET_US = b.comment("Incremental topology rebuild budget per tick, us (05 §5.3.3)")
                .defineInRange("topology_budget_us", 300, 10, 100_000);
        b.pop();

        b.push("server");
        SERVER_MODE = b.comment("Consequence policy: REALISTIC, CONTAINED or CREATIVE_SAFE (04d §4d.1)")
                .defineEnum("mode", ServerMode.CONTAINED);
        b.pop();

        b.push("damage");
        DAMAGE_MAX_BLOCKS_PER_TICK = b.comment("World block changes allowed per tick from accidents (04d §4d.2.3)")
                .defineInRange("max_blocks_per_tick", 64, 0, 4096);
        DAMAGE_MAX_BLOCKS_PER_EVENT = b.comment("Blocks one accident may change (04d §4d.2.3)")
                .defineInRange("max_blocks_per_event", 4096, 0, 1 << 20);
        DAMAGE_MAX_BLOCKS_PER_CHUNK_PER_DAY = b.comment("Blocks one chunk may lose per game day (04d §4d.2.3)")
                .defineInRange("max_blocks_per_chunk_per_day", 1024, 0, 1 << 20);
        DAMAGE_MAX_BLOCKS_PER_REGION_PER_DAY = b.comment("Blocks one 32x32-chunk region may lose per game day (04d §4d.2.3)")
                .defineInRange("max_blocks_per_region_per_day", 8192, 0, 1 << 24);
        DAMAGE_MAX_PENDING = b.comment("Queued block changes (memory bound, 04d §4d.2.3)")
                .defineInRange("max_pending", 32768, 0, 1 << 24);
        DAMAGE_MAX_EXPLOSION_POWER = b.comment("Maximum MC-equivalent explosion power; -1 = by server.mode: 12 / 8 / 0 (04d §4d.1)")
                .defineInRange("max_explosion_power", -1.0, -1.0, 24.0);
        DAMAGE_TNT_REFERENCE_KG = b.comment("TNT mass equivalent to a vanilla TNT block, kg (04d §4d.2.4)")
                .defineInRange("tnt_reference_kg", 50.0, 1.0, 1.0e4);
        DAMAGE_ITEM_DROP_FRACTION = b.comment("Fraction of destroyed blocks that drop an item (04d §4d.2.3)")
                .defineInRange("item_drop_fraction", 0.1, 0.0, 1.0);
        DAMAGE_MAX_ITEM_ENTITIES_PER_EVENT = b.comment("Item entities one accident may spawn (04d §4d.2.3)")
                .defineInRange("max_item_entities_per_event", 32, 0, 4096);
        b.pop();

        b.push("combustion");
        COMBUSTION_SOLID_FUEL_ITEM_MASS_KG = b.comment("[BALANCE] Mass of one solid fuel item, kg (07 §7.0.2)")
                .defineInRange("solid_fuel_item_mass_kg", 100.0, 1.0, 10_000.0);
        COMBUSTION_THERMAL_NOX_REF_G_PER_GJ = b.comment("[BALANCE] Thermal NOx reference emission, g/GJ (07 §7.8.4)")
                .defineInRange("thermal_nox_ref_g_per_gj", 80.0, 0.0, 10_000.0);
        COMBUSTION_CO_TRANSITION_WIDTH = b.comment("[BALANCE] Width of the CO transition near the critical excess air (07 §7.8.3)")
                .defineInRange("co_transition_width", 0.03, 0.001, 1.0);
        b.pop();

        b.push("grid");
        GRID_SYNC_MAX_ANGLE_DEG = b.comment("Synchronism check window: phase angle at closing, degrees (07 §7.3.5, IEEE C50.13)")
                .defineInRange("sync_max_angle_deg", 10.0, 1.0, 90.0);
        GRID_SYNC_MAX_DV = b.comment("Synchronism check window: generator voltage above the bus, pu")
                .defineInRange("sync_max_dv", 0.05, 0.005, 0.5);
        GRID_SYNC_MAX_SLIP_HZ = b.comment("Synchronism check window: slip, Hz")
                .defineInRange("sync_max_slip_hz", 0.067, 0.005, 2.0);
        GRID_SYNC_TORQUE_DAMAGE_PU = b.comment("[BALANCE] Closing torque above which shaft fatigue accumulates, pu")
                .defineInRange("sync_torque_damage_pu", 1.5, 0.5, 10.0);
        GRID_COUPLING_SHEAR_PU = b.comment("[BALANCE] Closing torque that shears a rigid coupling, pu")
                .defineInRange("coupling_shear_pu", 5.0, 1.0, 20.0);
        GRID_AR_SUCCESS_LIGHTNING = b.comment("[BALANCE] Share of lightning flashovers that clear in the auto-reclose dead time (07 §7.15.1)")
                .defineInRange("ar_success_lightning", 0.8, 0.0, 1.0);
        GRID_LV_BREAKER_THERMAL_K_S = b.comment("Thermal constant of 0.4 kV breaker and fuse curves, s (07 §7.4.6)")
                .defineInRange("lv_breaker_thermal_k_s", 40.0, 1.0, 1000.0);
        b.pop();

        b.push("boiler");
        BOILER_EXPLOSION_BLAST_FRACTION = b.comment("[BALANCE] Share of boiler burst energy that goes into the blast (07 §7.9.4)")
                .defineInRange("explosion_blast_fraction", 0.3, 0.0, 1.0);
        BOILER_FURNACE_EXPLOSION_BLAST_FRACTION = b.comment("[BALANCE] Share of furnace gas deflagration energy that goes into the blast (07 §7.9.6)")
                .defineInRange("furnace_explosion_blast_fraction", 0.1, 0.0, 1.0);
        BOILER_PURGE_MIN_S = b.comment("Minimum furnace purge time before ignition, s (NFPA 85, 07 §7.9.6)")
                .defineInRange("purge_min_s", 300.0, 0.0, 3600.0);
        BOILER_PURGE_MIN_VOLUME_CHANGES = b.comment("Minimum furnace volume changes during purge (NFPA 85, 07 §7.9.6)")
                .defineInRange("purge_min_volume_changes", 5, 0, 100);
        b.pop();

        b.push("metallurgy");
        METALLURGY_BULK_ITEM_MASS_KG = b.comment("[BALANCE] Mass of one bulk material item, kg (08 §8.0.2)")
                .defineInRange("bulk_item_mass_kg", 100.0, 1.0, 10_000.0);
        METALLURGY_INGOT_MASS_KG = b.comment("[BALANCE] Mass of one ingot, kg (08 §8.0.2)")
                .defineInRange("ingot_mass_kg", 100.0, 0.01, 10_000.0);
        METALLURGY_WIRE_ITEM_MASS_KG = b.comment("[BALANCE] Mass of one wire item, kg (08 §8.0.2)")
                .defineInRange("wire_item_mass_kg", 10.0, 0.01, 10_000.0);
        b.pop();

        b.push("worldgen");
        b.push("veins");
        VEINS_ENABLED = b.comment("Ore vein generation (08 §8.1.1)").define("enabled", true);
        VEINS_CELL_CHUNKS = b.comment("Vein cell side, chunks (08 §8.1.1)").defineInRange("cell_chunks", 3, 1, 64);
        VEINS_FILL_PROBABILITY = b.comment("[BALANCE] Probability that a cell holds an ore body (08 §8.1.1)")
                .defineInRange("fill_probability", 0.45, 0.0, 1.0);
        for (VeinType v : VeinCatalog.M2) {
            b.push(v.id());
            VEIN_ENABLED.put(v.id(), b.define("enabled", true));
            VEIN_WEIGHT.put(v.id(), b.comment("Relative weight (08 §8.1.2)").defineInRange("weight", v.weight(), 0.0, 1.0e4));
            b.pop();
        }
        b.pop();
        b.push("coal");
        COAL_SEAM_THICKNESS_MIN = b.comment("Minimum coal seam thickness, blocks (08 §8.1.4)").defineInRange("seam_thickness_min", 1, 1, 16);
        COAL_SEAM_THICKNESS_MAX = b.comment("Maximum coal seam thickness, blocks (08 §8.1.4)").defineInRange("seam_thickness_max", 4, 1, 16);
        COAL_RANK_GRADIENT_PER_BLOCK = b.comment("Volatile matter drop with depth, % per block (Hilt's rule, 08 §8.1.4)")
                .defineInRange("rank_gradient_per_block", 0.32, 0.0, 10.0);
        b.pop();
        b.pop();

        b.push("mining");
        MINING_ROOF_SPAN_STABLE = b.comment("[BALANCE] Unsupported roof span, sandstone, blocks (08 §8.2.2)").defineInRange("roof_span_stable", 6, 1, 64);
        MINING_ROOF_SPAN_MEDIUM = b.comment("[BALANCE] Unsupported roof span, mudstone, blocks").defineInRange("roof_span_medium", 4, 1, 64);
        MINING_ROOF_SPAN_WEAK = b.comment("[BALANCE] Unsupported roof span, carbonaceous shale, blocks").defineInRange("roof_span_weak", 2, 1, 64);
        MINING_METHANE_EMISSION_SCALE = b.comment("[BALANCE] Methane emission multiplier (08 §8.2.4)").defineInRange("methane_emission_scale", 1.0, 0.0, 100.0);
        MINING_DESORPTION_TAU_S = b.comment("Desorption time constant of fresh coal faces, physical s (08 §8.2.4)")
                .defineInRange("desorption_tau_s", 172_800.0, 1.0, 1.0e9);
        MINING_METHANE_UTILIZATION_MIN_FRAC = b.comment("[verify] Minimum CH4 fraction for drainage gas utilisation (08 §8.2.4)")
                .defineInRange("methane_utilization_min_frac", 0.25, 0.0, 1.0);
        MINING_AGZ_TRIP_FRAC = b.comment("Automatic gas protection trip, CH4 volume fraction (08 §8.2.5)").defineInRange("agz_trip_frac", 0.01, 0.0, 1.0);
        MINING_LOCAL_CH4_LIMIT_FRAC = b.comment("Local CH4 accumulation limit, volume fraction (08 §8.2.5)").defineInRange("local_ch4_limit_frac", 0.02, 0.0, 1.0);
        MINING_CANARY_UPTAKE_RATIO = b.comment("[verify] How much faster a canary takes up CO than a human (08 §8.2.5)")
                .defineInRange("canary_uptake_ratio", 20.0, 1.0, 1000.0);
        MINING_DUST_PER_TON_KG = b.comment("[BALANCE] Coal dust per ton mined, kg (08 §8.2.3)").defineInRange("dust_per_ton_kg", 0.5, 0.0, 100.0);
        MINING_EXPLOSION_BLAST_FRACTION = b.comment("[BALANCE] Share of mine explosion heat that goes into the blast (08 §8.2.6)")
                .defineInRange("explosion_blast_fraction", 0.03, 0.0, 1.0);
        MINING_AIR_VELOCITY_MIN_MS = b.comment("Minimum airway air velocity, m/s (08 §8.2.7)").defineInRange("air_velocity_min_ms", 0.25, 0.0, 10.0);
        MINING_AIR_VELOCITY_FACE_MAX_MS = b.comment("Maximum longwall face air velocity, m/s (08 §8.2.7)").defineInRange("air_velocity_face_max_ms", 4.0, 0.1, 30.0);
        MINING_HOIST_SF_PEOPLE = b.comment("Rope safety factor, man riding (08 §8.2.8)").defineInRange("hoist_sf_people", 9.0, 1.0, 50.0);
        MINING_HOIST_SF_MIXED = b.comment("Rope safety factor, mixed duty").defineInRange("hoist_sf_mixed", 7.5, 1.0, 50.0);
        MINING_HOIST_SF_CARGO = b.comment("Rope safety factor, cargo").defineInRange("hoist_sf_cargo", 6.5, 1.0, 50.0);
        MINING_ROPE_N0_CYCLES = b.comment("[BALANCE] Rope fatigue curve N0, cycles (08 §8.2.8)").defineInRange("rope_n0_cycles", 2.0e5, 1.0, 1.0e9);
        MINING_ROPE_CORROSION_PER_DAY = b.comment("Rope corrosion in a wet shaft, D_k per day (08 §8.2.8)").defineInRange("rope_corrosion_per_day", 0.002, 0.0, 1.0);
        MINING_ROPE_ITEM_LENGTH_M = b.comment("Length of one wire_rope item, m (08 §8.2.8)").defineInRange("rope_item_length_m", 10.0, 0.1, 1000.0);
        MINING_SAFETY_MODE = b.comment("ENFORCED: unsafe hoisting is refused; ADVISORY: allowed and journaled (08 §8.2.8)")
                .defineEnum("safety_mode", HoistConfig.SafetyMode.ENFORCED);
        MINING_SEAM_GAS_CONTENT = b.comment("[BALANCE] Seam gas content x0, m3/t; x = x0(1 - exp(-(72 - y)/40)) (08 §8.2.4)")
                .defineInRange("seam_gas_content_m3_per_t", 15.0, 0.0, 100.0);
        MINING_BARRIER_INERT_KG_PER_BLOCK = b.comment("[BALANCE] Inert mass held by one explosion_barrier block, kg; >= 400 kg per m2 of airway quenches the flame (08 §8.2.6)")
                .defineInRange("barrier_inert_kg_per_block", 500.0, 0.0, 1.0e4);
        MINING_MAX_MINE_BLOCKS = b.comment("Largest mine airway volume traced by the flood fill, blocks; beyond it the mine is truncated (08 §8.2.2)")
                .defineInRange("max_mine_blocks", 32_768, 64, 1 << 20);
        MINING_ROOF_COLLAPSE = b.comment("Unsupported roof spans collapse after the rock's delay (08 §8.2.2)").define("roof_collapse", true);
        MINING_WATER_YIELD = b.comment("Water opened up per ton of coal or ore mined, m3/t (08 §8.2.9: 0.5-10)")
                .defineInRange("water_yield_m3_per_t", 1.0, 0.0, 100.0);
        MINING_WATER_FILTRATION = b.comment("[BALANCE] Filtration inflow into every mine, m3/h (08 §8.2.9)")
                .defineInRange("water_filtration_m3_per_h", 2.0, 0.0, 1.0e4);
        MINING_SUMP_CAPACITY = b.comment("[BALANCE] Sump capacity of a mine, m3; water above it floods the lowest workings (08 §8.2.9)")
                .defineInRange("sump_capacity_m3", 400.0, 1.0, 1.0e6);
        MINING_WATER_RELEASE_TAU = b.comment("[BALANCE] Time constant for opened-up water to drain into the sump, physical s (08 §8.2.9)")
                .defineInRange("water_release_tau_s", 21_600.0, 1.0, 1.0e7);
        MINING_ACID_WATER_SULFUR = b.comment("[assumption] Coal sulfur S^d, %, from which seam water is acidic mine_water (08 §8.2.9)")
                .defineInRange("acid_water_sulfur_pct", 1.5, 0.0, 100.0);
        MINING_LONGWALL_SHEARER_KW = b.comment("[BALANCE] Longwall shearer drive power at 64 RPM of the gantry shaft, kW (08 §8.2.3)")
                .defineInRange("longwall_shearer_kw", 220.0, 1.0, 1.0e4);
        MINING_LONGWALL_MAX_FEED = b.comment("Longwall shearer maximum feed rate, m/min (08 §8.2.3: 2-6)")
                .defineInRange("longwall_max_feed_m_per_min", 6.0, 0.1, 60.0);
        MINING_LONGWALL_CUT_HEIGHT = b.comment("Height of the strip the shearer cuts, blocks: the leading drum takes the upper rows, the trailing one the floor row")
                .defineInRange("longwall_cut_height_blocks", 3, 1, 4);
        MINING_LONGWALL_ROCK_F = b.comment("[BALANCE] Protodyakonov hardness of non-coal blocks cut by the shearer drums")
                .defineInRange("longwall_rock_protodyakonov", 4.0, 0.1, 20.0);
        MINING_LONGWALL_SPRAY_WATER = b.comment("[BALANCE] Shearer dust suppression water, l/t, drawn from fluid tanks on the contraption")
                .defineInRange("longwall_spray_water_l_per_t", 30.0, 0.0, 1000.0);
        MINING_LONGWALL_SPRAY_DUST_FACTOR = b.comment("Share of coal dust left with water spray (08 §8.2.3: 1/5)")
                .defineInRange("longwall_spray_dust_factor", 0.2, 0.0, 1.0);
        MINING_LONGWALL_SUPPORT_SET_S = b.comment("[BALANCE] Time to set or lower a powered roof support, s")
                .defineInRange("longwall_support_set_s", 2.0, 0.0, 60.0);
        b.pop();

        b.push("casting");
        CASTING_CHVORINOV_B_SAND_MIN_CM2 = b.comment("Chvorinov constant B for steel in sand moulds, min/cm2 (08 §8.9)")
                .defineInRange("chvorinov_b_sand_min_cm2", 2.5, 0.01, 100.0);
        b.pop();

        b.push("difficulty");
        DIFFICULTY_PRESET = b.comment("Difficulty preset (04d §4d.11); for now it only selects handbook hints (11 §11.1.1)")
                .defineEnum("preset", DifficultyPreset.STANDARD);
        b.pop();

        b.push("content");
        CONTENT_GIVE_HANDBOOK_ON_JOIN = b.comment("Give engineering_handbook to every player once, on first join (11 §11.1.1)")
                .define("give_handbook_on_join", true);
        CONTENT_HANDBOOK_ALL_UNLOCKED = b.comment("Open every handbook chapter at once (11 §11.1.2)").define("handbook_all_unlocked", false);
        CONTENT_HINTS_BY_DIFFICULTY = b.comment("Show 'what next' hints at the end of chapters on TRAINING (11 §11.1.1)")
                .define("hints_by_difficulty", true);
        b.pop();

        b.push("machining");
        MACHINING_TAYLOR_N = b.comment("Taylor tool life exponent, HSS (08 §8.10)").defineInRange("taylor_n", 0.125, 0.01, 1.0);
        MACHINING_TAYLOR_C_M_MIN = b.comment("Taylor constant C_T, HSS on steel, m/min (08 §8.10)").defineInRange("taylor_c_m_min", 70.0, 1.0, 10_000.0);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }
}
