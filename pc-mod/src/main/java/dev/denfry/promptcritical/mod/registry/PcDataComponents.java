package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.MapTargetData;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.item.OreAssayData;
import dev.denfry.promptcritical.mod.item.RopeStateData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компоненты предметов (08 §8.20, 11 §11.11): анализы угля и руды, конструкция каната, регламент, цель карты. */
public final class PcDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoalAssayData>> COAL_ASSAY =
            COMPONENTS.registerComponentType("coal_assay", b -> b.persistent(CoalAssayData.CODEC)
                    .networkSynchronized(CoalAssayData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<OreAssayData>> ORE_ASSAY =
            COMPONENTS.registerComponentType("ore_assay", b -> b.persistent(OreAssayData.CODEC)
                    .networkSynchronized(OreAssayData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RopeStateData>> ROPE_STATE =
            COMPONENTS.registerComponentType("rope_state", b -> b.persistent(RopeStateData.CODEC)
                    .networkSynchronized(RopeStateData.STREAM_CODEC));

    /** Глава справочника, которую несёт {@code operating_procedure}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PROCEDURE_REF =
            COMPONENTS.registerComponentType("procedure_ref", b -> b.persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MapTargetData>> MAP_TARGET =
            COMPONENTS.registerComponentType("map_target", b -> b.persistent(MapTargetData.CODEC)
                    .networkSynchronized(MapTargetData.STREAM_CODEC));

    private PcDataComponents() {
    }
}
