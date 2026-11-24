package dev.denfry.promptcritical.mod.config;

import dev.denfry.promptcritical.sim.consequence.ServerMode;
import dev.denfry.promptcritical.sim.mining.HoistConfig;
import dev.denfry.promptcritical.sim.ops.CommissioningMode;
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
    public static final ModConfigSpec.DoubleValue ISOTOPES_GAMMA_HEATING_W_PER_G, ISOTOPES_FILM_EXPOSURE_GY, ISOTOPES_RTG_TE_SYSTEM_FACTOR,
            ISOTOPES_RTG_TE_DEGRADATION_PER_YEAR, TRANSPORT_TRACKED_ACTIVITY_BQ;
    public static final ModConfigSpec.IntValue ISOTOPES_ORPHAN_AFTER_S;
    public static final ModConfigSpec.DoubleValue K_STORAGE;
    public static final ModConfigSpec.DoubleValue K_BURN;
    public static final ModConfigSpec.EnumValue<DecayHeatClock> DECAY_HEAT_CLOCK;
    public static final ModConfigSpec.DoubleValue W_PER_SU;
    public static final ModConfigSpec.DoubleValue CREATE_GENERATOR_FACTOR, CREATE_STEAM_ENGINE_FACTOR;
    public static final ModConfigSpec.DoubleValue DIESEL_FAIL_TO_START_BASE;
    public static final ModConfigSpec.IntValue OPS_MAX_LOCKS_PER_PLAYER;
    public static final ModConfigSpec.EnumValue<dev.denfry.promptcritical.sim.ops.PermitEnforcement> OPS_PERMIT_ENFORCEMENT;
    public static final ModConfigSpec.DoubleValue J_PER_FE;
    /** Паритет SU↔FE (16 §16.7.1, R56). */
    public enum FeSuParity { AUTO, OFF }
    public static final ModConfigSpec.EnumValue<FeSuParity> FE_SU_PARITY;
    /** Интеграция с необязательным модом (16 §16.14): {@code auto} — включена, если мод загружен. */
    public enum CompatMode { AUTO, OFF }
    /** Кто владеет температурой тела (16 §16.10.1, R60): {@code auto} уступает Cold Sweat или Tough As Nails. */
    public enum BodyTemperatureOwner { AUTO, PROMPTCRITICAL, EXTERNAL }
    public static final ModConfigSpec.EnumValue<CompatMode> COMPAT_SEASONS, COMPAT_CURIOS, COMPAT_ACCESSORIES, COMPAT_JOURNEYMAP,
            COMPAT_VOICECHAT;
    public static final ModConfigSpec.BooleanValue VOICE_NOISE_MASKING;
    public static final ModConfigSpec.DoubleValue VOICE_SPEECH_SPL_DB, VOICE_MIN_RANGE_M;
    public static final ModConfigSpec.DoubleValue PA_SPEAKER_SPL_DB, PA_SNR_DB, PA_MAX_RANGE_M;
    public static final ModConfigSpec.IntValue PA_MAX_SPEAKERS_PER_ZONE, PA_PAGE_COOLDOWN_S;
    public static final ModConfigSpec.EnumValue<BodyTemperatureOwner> COMPAT_BODY_TEMPERATURE;
    /** Машины берут мощность от розеток, силовых щитов и ИБП. */
    public static final ModConfigSpec.BooleanValue MACHINES_NEED_POWER;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Double>> GRID_FE_RATINGS_W;
    public static final ModConfigSpec.DoubleValue GRID_FE_EFFICIENCY, GRID_FE_F_MIN, GRID_FE_F_MAX, GRID_FE_V_MIN, GRID_FE_V_MAX,
            GRID_FE_MAX_SHARE;
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
    public static final ModConfigSpec.EnumValue<CommissioningMode> OPS_COMMISSIONING;
    public static final ModConfigSpec.DoubleValue OPS_DEFECT_RATE;
    public static final ModConfigSpec.DoubleValue SITE_MATERIAL_SCALE;
    public static final ModConfigSpec.DoubleValue OPS_TRIAL_RUN_S;
    public static final ModConfigSpec.DoubleValue OPS_TRIAL_MIN_LOAD;
    public static final ModConfigSpec.DoubleValue OPS_TALLOW_INTERVAL_H;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_MAX_ANGLE_DEG;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_MAX_DV;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_MAX_SLIP_HZ;
    public static final ModConfigSpec.DoubleValue GRID_SYNC_TORQUE_DAMAGE_PU;
    public static final ModConfigSpec.DoubleValue GRID_COUPLING_SHEAR_PU;
    public static final ModConfigSpec.DoubleValue GRID_AR_SUCCESS_LIGHTNING;
    public static final ModConfigSpec.DoubleValue GRID_LV_BREAKER_THERMAL_K_S;
    public static final ModConfigSpec.IntValue GRID_LOAD_PROFILE_DAYS;
    public static final ModConfigSpec.DoubleValue BOILER_FURNACE_EXPLOSION_BLAST_FRACTION;
    public static final ModConfigSpec.DoubleValue BOILER_PURGE_MIN_S;
    public static final ModConfigSpec.IntValue BOILER_PURGE_MIN_VOLUME_CHANGES;

    // --- 08 §8.19 (I0) ---
    public static final ModConfigSpec.DoubleValue METALLURGY_BULK_ITEM_MASS_KG;
    public static final ModConfigSpec.DoubleValue METALLURGY_INGOT_MASS_KG;
    public static final ModConfigSpec.DoubleValue METALLURGY_HOLD_K;
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
    public static final ModConfigSpec.BooleanValue LICENSING_ENABLED;
    public static final ModConfigSpec.BooleanValue CONTENT_GIVE_HANDBOOK_ON_JOIN;
    public static final ModConfigSpec.BooleanValue CONTENT_HANDBOOK_ALL_UNLOCKED;
    public static final ModConfigSpec.IntValue TWIN_MAX_SESSIONS, TWIN_MAX_SPEED, TWIN_MAX_DURATION_S, CONTENT_MAX_SIMULATORS;
    public static final ModConfigSpec.DoubleValue TWIN_CPU_MS_PER_TICK, CONTENT_LICENSE_PASS_SCORE;
    public static final ModConfigSpec.BooleanValue CONTENT_HINTS_BY_DIFFICULTY;
    public static final ModConfigSpec.BooleanValue CONTENT_CC_RECIPE_OVERRIDE;
    public static final ModConfigSpec.IntValue ENVIRONMENT_DAYS_PER_SEASON;
    public static final ModConfigSpec.BooleanValue BODY_ELECTRIC_SHOCK;
    public static final ModConfigSpec.BooleanValue RADIATION_ENFORCE_ZONES;
    public static final ModConfigSpec.DoubleValue RADIATION_WORKER_ANNUAL_SV;

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
                .defineInRange("w_per_su", 190.0, 1.0, 1.0e6);
        CREATE_GENERATOR_FACTOR = b.comment("[BALANCE] Multiplier on the stress capacity of Create water wheels and windmills, so the",
                        "mod's engines out-produce them (owner decision 2026-11-23). 1 = Create as shipped")
                .defineInRange("create_generator_factor", 0.25, 0.0, 1.0);
        CREATE_STEAM_ENGINE_FACTOR = b.comment("[BALANCE] Multiplier on the stress capacity of the Create steam engine on a Create boiler.",
                        "At 0.03 one Create engine at its lowest boiler level gives about as much as the mod's I0 steam engine")
                .defineInRange("create_steam_engine_factor", 0.03, 0.0, 1.0);
        J_PER_FE = b.comment("[BALANCE] Joules per Forge Energy unit (00 §0.9)")
                .defineInRange("j_per_fe", 2.5, 0.01, 1.0e6);
        FE_SU_PARITY = b.comment("SU to FE parity (16 §16.7.1, R56): AUTO uses 2 x w_per_su joules per FE at the grid FE interface",
                        "when a mod bridging Create shafts and FE is loaded, so no loop makes energy; OFF keeps j_per_fe")
                .defineEnum("fe_su_parity", FeSuParity.AUTO);
        MACHINES_NEED_POWER = b.comment("Machines take power from sockets, power boards and UPS cabinets on the 0.4 kV grid and slow down",
                        "when they get less than they ask for; false runs every machine for free")
                .define("machines_need_power", true);
        b.pop();

        b.push("ops");
        OPS_MAX_LOCKS_PER_PLAYER = b.comment("LOTO padlocks one player can have hung at once (13 §13.14)")
                .defineInRange("max_locks_per_player", 8, 1, 64);
        OPS_PERMIT_ENFORCEMENT = b.comment("Work permits: OFF — no checks; ADVISORY — hot work without a permit is warned and journaled; ENFORCED — it is refused (13 §13.0.3)")
                .defineEnum("permit_enforcement", dev.denfry.promptcritical.sim.ops.PermitEnforcement.ADVISORY);
        b.pop();

        b.push("diesel");
        DIESEL_FAIL_TO_START_BASE = b.comment("[BALANCE] Emergency diesel failure-to-start probability per demand, p0 (07 §7.11.3)")
                .defineInRange("fail_to_start_base", 0.005, 0.0, 1.0);
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
                .defineInRange("sync_torque_damage_pu", 3.0, 0.5, 10.0);
        GRID_COUPLING_SHEAR_PU = b.comment("[BALANCE] Closing torque that shears a rigid coupling, pu")
                .defineInRange("coupling_shear_pu", 7.0, 1.0, 20.0);
        GRID_AR_SUCCESS_LIGHTNING = b.comment("[BALANCE] Share of lightning flashovers that clear in the auto-reclose dead time (07 §7.15.1)")
                .defineInRange("ar_success_lightning", 0.8, 0.0, 1.0);
        GRID_LV_BREAKER_THERMAL_K_S = b.comment("Thermal constant of 0.4 kV breaker and fuse curves, s (07 §7.4.6)")
                .defineInRange("lv_breaker_thermal_k_s", 40.0, 1.0, 1000.0);
        GRID_LOAD_PROFILE_DAYS = b.comment("Minecraft days per cycle of the daily load profile of grid consumers; 0 — flat contracted load (07 §7.6.4, §7.17)")
                .defineInRange("load_profile_days_per_cycle", 1, 0, 30);
        GRID_FE_RATINGS_W = b.comment("Rated power of the small, standard and large grid FE interface, W (07 §7.7.1)")
                .defineList("fe_interface_ratings_w", java.util.List.of(1.0e5, 1.0e6, 1.0e7),
                        o -> o instanceof Double d && d > 0 && d <= 1e9);
        GRID_FE_EFFICIENCY = b.comment("Conversion efficiency of the grid FE interface (07 §7.7.1)")
                .defineInRange("fe_interface_efficiency", 0.97, 0.5, 1.0);
        GRID_FE_F_MIN = b.comment("Grid FE interface works only between these frequencies, Hz").defineInRange("fe_window_f_min_hz", 49.0, 40.0, 60.0);
        GRID_FE_F_MAX = b.defineInRange("fe_window_f_max_hz", 51.0, 40.0, 70.0);
        GRID_FE_V_MIN = b.comment("Grid FE interface works only between these bus voltages, pu").defineInRange("fe_window_v_min_pu", 0.9, 0.5, 1.0);
        GRID_FE_V_MAX = b.defineInRange("fe_window_v_max_pu", 1.1, 1.0, 1.5);
        GRID_FE_MAX_SHARE = b.comment("Share of island generation from FE export above which the dispatcher is warned (07 §7.7.1)")
                .defineInRange("fe_max_share", 0.5, 0.05, 1.0);
        b.pop();

        b.push("ops");
        OPS_COMMISSIONING = b.comment("Commissioning: OFF — no defects; ADVISORY — unaccepted units may start (journaled); ENFORCED — start is refused until all checks but the trial run pass (13 §13.6.6)")
                .defineEnum("commissioning", CommissioningMode.ADVISORY);
        OPS_DEFECT_RATE = b.comment("[BALANCE] Multiplier of installation defect probabilities; 0 — no defects (13 §13.6.6)")
                .defineInRange("defect_rate", 1.0, 0.0, 10.0);
        OPS_TRIAL_RUN_S = b.comment("Trial run length, real seconds (13 §13.6.6)").defineInRange("trial_run_s", 600.0, 1.0, 86400.0);
        OPS_TRIAL_MIN_LOAD = b.comment("Minimum load in the trial run, share of rated (13 §13.6.6)").defineInRange("trial_min_load", 0.7, 0.0, 1.0);
        OPS_TALLOW_INTERVAL_H = b.comment("[BALANCE] Tallow top-up interval, physics hours (13 §13.6.7)")
                .defineInRange("tallow_interval_h", 500.0, 1.0, 1.0e5);
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
        METALLURGY_HOLD_K = b.comment("[BALANCE] Physical seconds per real second for hot vessels that only wait (ladles, the converter "
                + "between blows): their heat loss runs on this clock, not on time.k_slow, since carrying and tilting are real time (00 §0.4)")
                .defineInRange("hold_k", 4.0, 1.0, 72.0);
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

        b.push("licensing");
        LICENSING_ENABLED = b.comment("Licensing profile: era licences held by a team gate unit starts, commissioning is always ENFORCED. Read once when a world is created and fixed for that world afterwards (modpack design, hardcore profile)")
                .define("enabled", false);
        b.pop();

        b.push("content");
        CONTENT_GIVE_HANDBOOK_ON_JOIN = b.comment("Give engineering_handbook to every player once, on first join (11 §11.1.1)")
                .define("give_handbook_on_join", true);
        CONTENT_HANDBOOK_ALL_UNLOCKED = b.comment("Open every handbook chapter at once (11 §11.1.2)").define("handbook_all_unlocked", false);
        CONTENT_HINTS_BY_DIFFICULTY = b.comment("Show 'what next' hints at the end of chapters on TRAINING (11 §11.1.1)")
                .define("hints_by_difficulty", true);
        CONTENT_CC_RECIPE_OVERRIDE = b.comment("CC: Tweaked computers and monitors are crafted from this mod's chips, boards and tubes (11 §11.5.4, K20). "
                + "Read when datapacks load; the default applies to a world's first load").define("cc_recipe_override", true);
        CONTENT_MAX_SIMULATORS = b.comment("Training simulators running at once on the server (11 §11.3)")
                .defineInRange("max_simulators_running", 2, 0, 64);
        CONTENT_LICENSE_PASS_SCORE = b.comment("Lesson score an operator license needs (11 §11.3.5)")
                .defineInRange("license_pass_score", 0.75, 0.0, 1.0);
        b.pop();

        b.push("twin");
        TWIN_MAX_SESSIONS = b.comment("Digital twin sessions open at once on the server (03d §3d.9.1)").defineInRange("max_sessions", 4, 0, 64);
        TWIN_MAX_SPEED = b.comment("Twin steps per server tick at most (03d §3d.9.1)").defineInRange("max_speed", 10, 1, 100);
        TWIN_CPU_MS_PER_TICK = b.comment("Server time all twins and simulators may spend per tick, ms; short of it they slow down, not the world")
                .defineInRange("cpu_ms_per_tick", 2.0, 0.1, 50.0);
        TWIN_MAX_DURATION_S = b.comment("Real seconds a twin session or a lesson may run (03d §3d.9.1)").defineInRange("max_duration_s", 3600, 60, 86400);
        b.pop();

        b.push("environment");
        ENVIRONMENT_DAYS_PER_SEASON = b.comment("Game days per season of the built-in calendar (24 = Serene Seasons default)")
                .defineInRange("days_per_season", 24, 1, 1000);
        b.pop();

        b.push("body");
        BODY_ELECTRIC_SHOCK = b.comment("A player touching a live bare busbar while earthed gets an electric shock (IEC 60479-1, Dalziel)")
                .define("electric_shock", true);
        b.pop();

        b.push("radiation");
        RADIATION_ENFORCE_ZONES = b.comment("Zone access doors stay shut for a player without an electronic dosimeter or over the annual limit, "
                + "and on the way out while hands or shoes are contaminated; off — the door opens and only warns (04b §4b.12.2)")
                .define("enforce_zones", false);
        b.push("limits");
        RADIATION_WORKER_ANNUAL_SV = b.comment("Annual dose limit of a radiation worker, Sv per 365 game days (04b §4b.5.2: 20 mSv, NRB-99/2009, ICRP 103)")
                .defineInRange("worker_annual_sv", 0.02, 0.001, 1.0);
        b.pop();
        b.pop();

        b.push("isotopes");
        ISOTOPES_GAMMA_HEATING_W_PER_G = b.comment("Gamma heating of a capsule in a PWR-like core at 100 W/cm3, W/g; scaled by the local power density (12 §12.1.4)")
                .defineInRange("gamma_heating_w_per_g", 5.0, 0.0, 100.0);
        ISOTOPES_FILM_EXPOSURE_GY = b.comment("[BALANCE] Air kerma behind the weld a radiography film needs, Gy (12 §12.2.3; industrial film is a few mGy)")
                .defineInRange("film_exposure_gy", 5e-3, 1e-6, 1.0);
        ISOTOPES_RTG_TE_SYSTEM_FACTOR = b.comment("System to material efficiency ratio of a thermoelectric RTG (12 §12.5.4)")
                .defineInRange("rtg_te_system_factor", 0.8, 0.1, 1.0);
        ISOTOPES_RTG_TE_DEGRADATION_PER_YEAR = b.comment("[BALANCE] Thermoelement degradation per physical year (12 §12.5.4)")
                .defineInRange("rtg_te_degradation_per_year", 0.03, 0.0, 0.5);
        ISOTOPES_ORPHAN_AFTER_S = b.comment("A tracked source unseen this long becomes an orphan source: alarm to its last holder and the server log (16 §16.8.4)")
                .defineInRange("orphan_after_s", 600, 10, 86_400);
        b.pop();

        b.push("transport");
        TRANSPORT_TRACKED_ACTIVITY_BQ = b.comment("[BALANCE] Items above this activity, Bq, or in IAEA categories 1-3, are tracked sources (04d §4d.6.1)")
                .defineInRange("tracked_activity_bq", 1e12, 1e6, 1e20);
        b.pop();

        b.push("site");
        SITE_MATERIAL_SCALE = b.comment("[BALANCE] Share of the real material of a real-scale machine its construction site asks for: 1 — the prototype's mass in 100 kg ingots and plates, lower for lighter play (real-scale machines; until the heavy parts of GDD 18 exist)")
                .defineInRange("material_scale", 1.0, 0.001, 1.0);
        b.pop();

        b.push("machining");
        MACHINING_TAYLOR_N = b.comment("Taylor tool life exponent, HSS (08 §8.10)").defineInRange("taylor_n", 0.125, 0.01, 1.0);
        MACHINING_TAYLOR_C_M_MIN = b.comment("Taylor constant C_T, HSS on steel, m/min (08 §8.10)").defineInRange("taylor_c_m_min", 70.0, 1.0, 10_000.0);
        b.pop();

        b.push("mod");
        b.push("compat");
        COMPAT_SEASONS = b.comment("Read the season from Serene Seasons or Ecliptic Seasons when one is installed, Serene Seasons if both (16 §16.10.2);",
                        "off keeps the built-in calendar")
                .defineEnum("seasons", CompatMode.AUTO);
        COMPAT_CURIOS = b.comment("Read worn gear from Curios slots when it is installed (16 §16.9)").defineEnum("curios", CompatMode.AUTO);
        COMPAT_ACCESSORIES = b.comment("Read worn gear from Accessories slots when it is installed (16 §16.9)")
                .defineEnum("accessories", CompatMode.AUTO);
        COMPAT_JOURNEYMAP = b.comment("A read geological map in hand sets a JourneyMap waypoint on the ore body (16 §16.5.1)")
                .defineEnum("journeymap", CompatMode.AUTO);
        COMPAT_VOICECHAT = b.comment("Simple Voice Chat integration (16 §16.4)").defineEnum("voicechat", CompatMode.AUTO);
        // 16 §16.14 пишет mod.compat.voicechat.noise_masking, но в TOML ключ voicechat не может быть и значением, и таблицей
        VOICE_NOISE_MASKING = b.comment("Workshop noise at the speaker limits how far direct speech carries (16 §16.4.2)")
                .define("voicechat_noise_masking", true);
        b.pop();
        b.pop();

        b.push("ops");
        VOICE_SPEECH_SPL_DB = b.comment("[BALANCE] A shout at 1 m, dB(A): speech range is 10^((this - noise)/20) metres (16 §16.4.2)")
                .defineInRange("voice_speech_spl_db", 84.0, 40.0, 130.0);
        VOICE_MIN_RANGE_M = b.comment("Speech range never falls below this, m (16 §16.4.2)")
                .defineInRange("voice_min_range_m", 2.0, 0.5, 16.0);
        PA_SPEAKER_SPL_DB = b.comment("[BALANCE] PA horn loudspeaker at 1 m, dB(A) (13 §13.3.4)")
                .defineInRange("pa_speaker_spl_db", 110.0, 80.0, 140.0);
        PA_SNR_DB = b.comment("Margin over the noise for intelligible voice alarm, dB (13 §13.3.4; STI 0.5, IEC 60268-16)")
                .defineInRange("pa_snr_db", 10.0, 0.0, 30.0);
        PA_MAX_RANGE_M = b.comment("Farthest a PA loudspeaker carries, m; not above the Simple Voice Chat voice range (16 §16.4.1)")
                .defineInRange("pa_max_range_m", 48.0, 4.0, 256.0);
        PA_MAX_SPEAKERS_PER_ZONE = b.comment("Loudspeakers one PA zone may hold (13 §13.3.4)")
                .defineInRange("pa_max_speakers_per_zone", 16, 1, 256);
        PA_PAGE_COOLDOWN_S = b.comment("Pause between typed calls from one PA station, s (13 §13.3.4)")
                .defineInRange("pa_page_cooldown_s", 10, 0, 600);
        b.pop();
        b.push("mod");
        b.push("compat");
        COMPAT_BODY_TEMPERATURE = b.comment("Who owns body temperature (16 §16.10.1, R60): auto hands it to Cold Sweat or Tough As Nails when one is",
                "installed and turns our hypothermia off, so the player is not punished twice; promptcritical always keeps ours; external always defers")
                .defineEnum("body_temperature", BodyTemperatureOwner.AUTO);
        b.pop();
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }
}
