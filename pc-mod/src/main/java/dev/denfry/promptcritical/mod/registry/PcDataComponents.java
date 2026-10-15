package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.item.OreAssayData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компоненты предметов (08 §8.20): анализы угля и руды. */
public final class PcDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoalAssayData>> COAL_ASSAY =
            COMPONENTS.registerComponentType("coal_assay", b -> b.persistent(CoalAssayData.CODEC)
                    .networkSynchronized(CoalAssayData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<OreAssayData>> ORE_ASSAY =
            COMPONENTS.registerComponentType("ore_assay", b -> b.persistent(OreAssayData.CODEC)
                    .networkSynchronized(OreAssayData.STREAM_CODEC));

    private PcDataComponents() {
    }
}
