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

    /**
     * Доля U-235 в уранините, ppm атомов (11 §11.4.2): природная 7200; руда природного реактора — обеднённая, 6000.
     * Нет компонента — природная.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> U235_PPM =
            COMPONENTS.registerComponentType("u235_ppm", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
    /** Бесхозный источник (12 §12.3.3): найден вне контейнера и вне владения станции. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ORPHAN_SOURCE =
            COMPONENTS.registerComponentType("orphan_source", b -> b.persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));

    /** Глава справочника, которую несёт {@code operating_procedure}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PROCEDURE_REF =
            COMPONENTS.registerComponentType("procedure_ref", b -> b.persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MapTargetData>> MAP_TARGET =
            COMPONENTS.registerComponentType("map_target", b -> b.persistent(MapTargetData.CODEC)
                    .networkSynchronized(MapTargetData.STREAM_CODEC));

    /** Керн 09 §9.2.6: интервал, литология, свойства залежи и измерения станции. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.denfry.promptcritical.mod.drilling.ReservoirCoreItem.Data>>
            CORE_DATA = COMPONENTS.registerComponentType("core_data",
                    b -> b.persistent(dev.denfry.promptcritical.mod.drilling.ReservoirCoreItem.Data.CODEC)
                            .networkSynchronized(dev.denfry.promptcritical.mod.drilling.ReservoirCoreItem.Data.STREAM_CODEC));
    /** Глубина и давление открытия газлифтного клапана 09 §9.6.4. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.denfry.promptcritical.mod.production.GasLiftValveItem.Data>>
            GAS_LIFT_VALVE = COMPONENTS.registerComponentType("gas_lift_valve",
                    b -> b.persistent(dev.denfry.promptcritical.mod.production.GasLiftValveItem.Data.CODEC)
                            .networkSynchronized(dev.denfry.promptcritical.mod.production.GasLiftValveItem.Data.STREAM_CODEC));
    /** Интерпретация сейсмической съёмки на {@code seismic_section}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.denfry.promptcritical.mod.drilling.SeismicSectionData>>
            SEISMIC_SECTION = COMPONENTS.registerComponentType("seismic_section",
                    b -> b.persistent(dev.denfry.promptcritical.mod.drilling.SeismicSectionData.CODEC)
                            .networkSynchronized(dev.denfry.promptcritical.mod.drilling.SeismicSectionData.STREAM_CODEC));
    /** Батарея и фара головного светильника. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.denfry.promptcritical.mod.item.CapLampData>> CAP_LAMP =
            COMPONENTS.registerComponentType("cap_lamp", b -> b.persistent(dev.denfry.promptcritical.mod.item.CapLampData.CODEC)
                    .networkSynchronized(dev.denfry.promptcritical.mod.item.CapLampData.STREAM_CODEC));

    /** Батарея и фильтр носимого электроснаряжения (§15.8). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.denfry.promptcritical.mod.item.ElectricGearData>> ELECTRIC_GEAR =
            COMPONENTS.registerComponentType("electric_gear", b -> b.persistent(dev.denfry.promptcritical.mod.item.ElectricGearData.CODEC)
                    .networkSynchronized(dev.denfry.promptcritical.mod.item.ElectricGearData.STREAM_CODEC));

    private PcDataComponents() {
    }
}
