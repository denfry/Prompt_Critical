package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Теги мода (05 §5.3.7) и общие теги {@code c:}. */
public final class PcTags {
    private PcTags() {
    }

    public static final class Blocks {
        /** Изменение блока помечает окрестность для пересборки топологии (05 §5.3.3). */
        public static final TagKey<Block> TOPOLOGY_RELEVANT = mod("topology_relevant");
        /** Блоки, которые {@code DamageBudget} не трогает (04d §4d.2.3, {@code damage.protected_tag}). */
        public static final TagKey<Block> DAMAGE_IMMUNE = mod("damage_immune");
        /** Открытое пламя в выработке воспламеняет метан (08 §8.2.6). */
        public static final TagKey<Block> OPEN_FLAME = mod("open_flame");
        /** Кровля устойчивая — пролёт {@code mining.roof_span_stable} (08 §8.2.2). */
        public static final TagKey<Block> ROOF_STABLE = mod("roof_stable");
        /** Кровля неустойчивая — {@code mining.roof_span_weak}; остальное — средняя. */
        public static final TagKey<Block> ROOF_WEAK = mod("roof_weak");
        public static final TagKey<Block> ORES = common("ores");
        public static final TagKey<Block> COAL_ORES = common("ores/coal");

        private Blocks() {
        }

        private static TagKey<Block> mod(String path) {
            return TagKey.create(Registries.BLOCK, PromptCritical.id(path));
        }

        public static TagKey<Block> common(String path) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> ORES = common("ores");
        public static final TagKey<Item> RAW_MATERIALS = common("raw_materials");

        private Items() {
        }

        public static TagKey<Item> common(String path) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }
}
