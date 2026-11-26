package dev.denfry.promptcritical.mod.fx;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Частицы мода. Клубы — крупные медленные облака пара и дыма, которых нет у ванильных частиц: шлейф выброса, пар,
 * заполняющий зал, пыль взрыва. Живут десятки секунд, растут и тают; видны издалека (не урезаются настройкой частиц).
 */
public final class PcParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, PromptCritical.MOD_ID);
    /** Белый клуб пара. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM_PUFF = PARTICLES.register("steam_puff",
            () -> new SimpleParticleType(true));
    /** Серо-бурый клуб дыма и аэрозоля (горящие оболочки, бетон под расплавом, пыль взрыва). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKE_PUFF = PARTICLES.register("smoke_puff",
            () -> new SimpleParticleType(true));

    private PcParticles() {
    }
}
