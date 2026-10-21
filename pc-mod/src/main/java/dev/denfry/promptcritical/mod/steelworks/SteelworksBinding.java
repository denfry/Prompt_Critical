package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.utilities.UtilitiesSubsystem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Привязка стали, проката, труб и ВРУ (этап 10б M3): компоненты, BE, склады и баки для автоматики, подсистема
 * утилит {@link UtilitiesSubsystem}. Конвертер и печи с садкой живут в подсистеме коксохима и домны.
 */
public final class SteelworksBinding {
    private SteelworksBinding() {
    }

    public static void register(IEventBus modBus) {
        SteelworksComponents.COMPONENTS.register(modBus);
        SteelworksBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SteelworksBinding::registerCapabilities);
        SimBindings.registerSubsystem(level -> new UtilitiesSubsystem());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        caps(event, SteelworksBlockEntities.BOF_VESSEL.get());
        caps(event, SteelworksBlockEntities.OXYGEN_LANCE.get());
        caps(event, SteelworksBlockEntities.LADLE_STATION.get());
        caps(event, SteelworksBlockEntities.INGOT_MOLD.get());
        caps(event, SteelworksBlockEntities.REHEATING_FURNACE.get());
        caps(event, SteelworksBlockEntities.ROLLER_TABLE.get());
        caps(event, SteelworksBlockEntities.ROLLING_MILL_STAND.get());
        caps(event, SteelworksBlockEntities.PIERCING_MILL.get());
        caps(event, SteelworksBlockEntities.PIPE_WELDER.get());
        caps(event, SteelworksBlockEntities.HYDROTEST_BENCH.get());
        caps(event, SteelworksBlockEntities.HEAT_TREATMENT_FURNACE.get());
        caps(event, SteelworksBlockEntities.QUENCH_TANK.get());
        caps(event, SteelworksBlockEntities.AIR_SEPARATOR.get());
        caps(event, SteelworksBlockEntities.ASU_MAIN_COMPRESSOR.get());
        caps(event, SteelworksBlockEntities.CRYOGENIC_TANK.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void caps(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }
}
