package dev.denfry.promptcritical.mod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code promptcritical-server.toml} (05 §5.3.6). Ключи — {@code section.key} (00 §0.10).
 * Остальные секции добавляются вместе с подсистемами, которые их читают.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue K_SLOW;
    public static final ModConfigSpec.DoubleValue K_STORAGE;
    public static final ModConfigSpec.DoubleValue K_BURN;
    public static final ModConfigSpec.EnumValue<DecayHeatClock> DECAY_HEAT_CLOCK;
    public static final ModConfigSpec.DoubleValue W_PER_SU;
    public static final ModConfigSpec.DoubleValue J_PER_FE;
    public static final ModConfigSpec.IntValue CC_TOKEN_RATE;
    public static final ModConfigSpec.IntValue CC_TOKEN_BURST;
    public static final ModConfigSpec.EnumValue<ServerMode> SERVER_MODE;
    public static final ModConfigSpec.IntValue DAMAGE_MAX_BLOCKS_PER_TICK;

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

        b.push("server");
        SERVER_MODE = b.comment("Consequence policy: REALISTIC, CONTAINED or CREATIVE_SAFE (04d §4d.1)")
                .defineEnum("mode", ServerMode.CONTAINED);
        b.pop();

        b.push("damage");
        DAMAGE_MAX_BLOCKS_PER_TICK = b.comment("World block changes allowed per tick from accidents (04d)")
                .defineInRange("max_blocks_per_tick", 64, 0, 4096);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }
}
