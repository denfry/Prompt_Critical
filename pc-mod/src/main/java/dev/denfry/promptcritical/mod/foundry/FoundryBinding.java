package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.mining.CoalPrepSubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Привязка литейки, механического цеха и углеобогащения (этап 8 M2): реестры, подсистемы мира, склады и баки для
 * автоматики — на контроллере и на любой клетке конструкции.
 */
public final class FoundryBinding {
    private FoundryBinding() {
    }

    public static void register(IEventBus modBus) {
        FoundryComponents.COMPONENTS.register(modBus);
        FoundryBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(FoundryBinding::registerCapabilities);
        SimBindings.registerSubsystem(level -> new FoundrySubsystem());
        SimBindings.registerSubsystem(level -> new CoalPrepSubsystem());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        caps(event, FoundryBlockEntities.COAL_JIG.get());
        caps(event, FoundryBlockEntities.CUPOLA.get());
        caps(event, FoundryBlockEntities.CRUCIBLE.get());
        caps(event, FoundryBlockEntities.MOLDING_STATION.get());
        caps(event, FoundryBlockEntities.SHAKEOUT_GRATE.get());
        caps(event, FoundryBlockEntities.MACHINE_LATHE.get());
        caps(event, FoundryBlockEntities.DRILL_PRESS.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            FoundryDevice d = controller(level, pos, state);
            return d == null ? null : d.items(side);
        }, PcBlocks.MULTIBLOCK_PART.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            FoundryDevice d = controller(level, pos, state);
            return d == null ? null : d.fluids(side);
        }, PcBlocks.MULTIBLOCK_PART.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void caps(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }

    @Nullable
    private static FoundryDevice controller(Level level, BlockPos pos, BlockState state) {
        BlockPos c = PcBlock.structureController(level, MultiblockPartBlock.anchor(state, pos));
        return c != null && level.getBlockEntity(c) instanceof FoundryDevice d ? d : null;
    }
}
