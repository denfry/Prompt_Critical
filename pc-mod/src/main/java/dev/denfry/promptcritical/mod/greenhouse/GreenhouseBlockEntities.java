package dev.denfry.promptcritical.mod.greenhouse;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Сбросное тепло (17 §17.3.6): типы BE регистра теплицы, садка и сетевого подогревателя ТЭЦ, подписка на рост растений. */
public final class GreenhouseBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GreenhouseCoilBlockEntity>> GREENHOUSE_COIL =
            BLOCK_ENTITIES.register("greenhouse_coil",
                    () -> BlockEntityType.Builder.of(GreenhouseCoilBlockEntity::new, PcBlocks.GREENHOUSE_COIL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishCageBlockEntity>> FISH_CAGE =
            BLOCK_ENTITIES.register("fish_cage", () -> BlockEntityType.Builder.of(FishCageBlockEntity::new, PcBlocks.FISH_CAGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistrictHeatExchangerBlockEntity>> DISTRICT_HEAT_EXCHANGER =
            BLOCK_ENTITIES.register("district_heat_exchanger",
                    () -> BlockEntityType.Builder.of(DistrictHeatExchangerBlockEntity::new, PcBlocks.DISTRICT_HEAT_EXCHANGER.get()).build(null));

    private GreenhouseBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        GreenhouseGrowth.register();
        // корм в садок — воронкой
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> e.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, FISH_CAGE.get(), FishCageBlockEntity::items));
    }
}
