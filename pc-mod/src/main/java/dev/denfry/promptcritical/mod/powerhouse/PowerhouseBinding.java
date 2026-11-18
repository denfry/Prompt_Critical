package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Привязка котла I1 и деаэратора (этап 11б M3): реестр BE, баки и склады для автоматики — щит, горелки, барабан и
 * стены котла принимают газ, воду и угольную пыль в щит. Подсистема — {@code SteamPlant} котельной I0.
 */
public final class PowerhouseBinding {
    private PowerhouseBinding() {
    }

    public static void register(IEventBus modBus) {
        PowerhouseBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(PowerhouseBinding::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        caps(event, PowerhouseBlockEntities.WATER_TUBE_BOILER.get());
        caps(event, PowerhouseBlockEntities.DEAERATOR.get());
        caps(event, PowerhouseBlockEntities.GAS_TURBINE.get());
        caps(event, PowerhouseBlockEntities.DIESEL_GENERATOR.get());
        // части ДГ без BE: труба дизтоплива к любой из них идёт в расходный бак контроллера
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            DieselGeneratorBlockEntity d = DieselGeneratorBlockEntity.at(level, pos);
            return d == null ? null : d.fluids(side);
        }, PcBlocks.DIESEL_GENERATOR.get());
        // якоря второй и третьей секций ГТУ — без BE: трубы к ним идут на контроллер
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            GasTurbineBlockEntity g = GasTurbineBlockEntity.at(level, pos);
            return g == null ? null : g.fluids(side);
        }, PcBlocks.GAS_TURBINE.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            WaterTubeBoilerBlockEntity b = WaterTubeBoilerBlockEntity.of(level, pos);
            return b == null ? null : b.fluids(side);
        }, PcBlocks.BOILER_BURNER.get(), PcBlocks.STEAM_DRUM.get(), PcBlocks.BOILER_WALL.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            WaterTubeBoilerBlockEntity b = WaterTubeBoilerBlockEntity.of(level, pos);
            return b == null ? null : b.items(side);
        }, PcBlocks.BOILER_BURNER.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void caps(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }
}
