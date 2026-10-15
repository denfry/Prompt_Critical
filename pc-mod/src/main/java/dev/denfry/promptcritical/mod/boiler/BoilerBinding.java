package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.plant.SteamPlantConfig;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Привязка котельной I0 (этап 6 M2): реестр BE, подсистема {@link SteamPlant} в каждом измерении, баки и склады для
 * автоматики — питательный бак и шлак котла на любой клетке, бункер цепной решётки.
 */
public final class BoilerBinding {
    private BoilerBinding() {
    }

    public static void register(IEventBus modBus) {
        BoilerBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(BoilerBinding::registerCapabilities);
        SimBindings.registerSubsystem(level -> new SteamPlant(
                SimConfigs.fromServerConfig().section(SteamPlantConfig.class, SteamPlantConfig.defaults())));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            FireTubeBoilerBlockEntity b = FireTubeBoilerBlockEntity.at(level, pos);
            return b == null ? null : b.feedInput();
        }, PcBlocks.FIRE_TUBE_BOILER.get(), PcBlocks.MULTIBLOCK_PART.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            FireTubeBoilerBlockEntity b = FireTubeBoilerBlockEntity.at(level, pos);
            return b == null ? null : b.slagOutput();
        }, PcBlocks.FIRE_TUBE_BOILER.get(), PcBlocks.MULTIBLOCK_PART.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BoilerBlockEntities.GRATE_STOKER.get(),
                (be, side) -> be.items((Direction) side));
    }
}
