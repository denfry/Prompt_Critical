package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE промышленной безопасности 13. */
public final class SafetyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManwayBlockEntity>> MANWAY =
            type("manway", ManwayBlockEntity::new, PcBlocks.MANWAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RescueTripodBlockEntity>> RESCUE_TRIPOD =
            type("rescue_tripod", RescueTripodBlockEntity::new, PcBlocks.RESCUE_TRIPOD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortableVentilatorBlockEntity>> PORTABLE_VENTILATOR =
            type("portable_ventilator", PortableVentilatorBlockEntity::new, PcBlocks.PORTABLE_VENTILATOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatrolCheckpointBlockEntity>> PATROL_CHECKPOINT =
            type("patrol_checkpoint", PatrolCheckpointBlockEntity::new, PcBlocks.PATROL_CHECKPOINT);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlareIgnitionPanelBlockEntity>> FLARE_IGNITION_PANEL =
            type("flare_ignition_panel", FlareIgnitionPanelBlockEntity::new, PcBlocks.FLARE_IGNITION_PANEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlareKnockoutDrumBlockEntity>> FLARE_KNOCKOUT_DRUM =
            type("flare_knockout_drum", FlareKnockoutDrumBlockEntity::new, PcBlocks.FLARE_KNOCKOUT_DRUM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlareSealDrumBlockEntity>> FLARE_SEAL_DRUM =
            type("flare_seal_drum", FlareSealDrumBlockEntity::new, PcBlocks.FLARE_SEAL_DRUM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurningGearBlockEntity>> TURBINE_TURNING_GEAR =
            type("turbine_turning_gear", TurningGearBlockEntity::new, PcBlocks.TURBINE_TURNING_GEAR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VentStackBlockEntity>> VENT_STACK =
            type("vent_stack", VentStackBlockEntity::new, PcBlocks.VENT_STACK);

    private SafetyBlockEntities() {
    }

    static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SafetyBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MANWAY.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLARE_IGNITION_PANEL.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLARE_KNOCKOUT_DRUM.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLARE_SEAL_DRUM.get(), FoundryDevice::fluids);
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
