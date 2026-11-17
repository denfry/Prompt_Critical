package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.reactor.ReactorSubsystem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Привязка графитового котла T0 (I1): блок-сущности, подсистема реакторов мира, склады для воронок. */
public final class ReactorBinding {
    private ReactorBinding() {
    }

    public static void register(IEventBus modBus) {
        ReactorBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ReactorBinding::registerCapabilities);
        SimBindings.registerSubsystem(level -> new ReactorSubsystem(level.getSeed()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        items(event, ReactorBlockEntities.FUEL_CHANNEL.get());
        items(event, ReactorBlockEntities.ACHESON_FURNACE.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void items(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, FoundryDevice::items);
    }
}
