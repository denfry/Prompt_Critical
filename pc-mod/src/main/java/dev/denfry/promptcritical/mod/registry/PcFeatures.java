package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.worldgen.VeinOreFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Генераторы мира: смешанные жилы и пласты 08 §8.1 (размещение — {@code data/promptcritical/worldgen} и модификатор биомов). */
public final class PcFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, PromptCritical.MOD_ID);

    public static final DeferredHolder<Feature<?>, VeinOreFeature> VEIN_ORES = FEATURES.register("vein_ores", VeinOreFeature::new);

    private PcFeatures() {
    }
}
