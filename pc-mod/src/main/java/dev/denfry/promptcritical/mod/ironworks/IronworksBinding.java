package dev.denfry.promptcritical.mod.ironworks;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingSubsystem;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Привязка коксохима и домны (этап 10 M3): реестры, подсистема мира {@link IronmakingSubsystem}, склады и баки для
 * автоматики, штанга выталкивателя и ковш на контрапциях Create.
 */
public final class IronworksBinding {
    private IronworksBinding() {
    }

    public static void register(IEventBus modBus) {
        IronworksComponents.COMPONENTS.register(modBus);
        IronworksBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(IronworksBinding::registerCapabilities);
        modBus.addListener(IronworksBinding::setup);
        SimBindings.registerSubsystem(level -> new IronmakingSubsystem());
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MovementBehaviour.REGISTRY.register(PcBlocks.COKE_PUSHER_RAM.get(), new CokePusherRamMovementBehaviour());
            BlockMovementChecks.registerMovementAllowedCheck(IronworksBinding::movementAllowed);
            BlockMovementChecks.registerAttachedCheck(IronworksBinding::attached);
        });
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        caps(event, IronworksBlockEntities.COKE_OVEN.get());
        caps(event, IronworksBlockEntities.GAS_EXHAUSTER.get());
        caps(event, IronworksBlockEntities.BYPRODUCT_PLANT.get());
        caps(event, IronworksBlockEntities.QUENCH_TOWER.get());
        caps(event, IronworksBlockEntities.ELECTRODE_EXTRUDER.get());
        caps(event, IronworksBlockEntities.BAKING_FURNACE.get());
        caps(event, IronworksBlockEntities.MAGNETIC_SEPARATOR.get());
        caps(event, IronworksBlockEntities.SINTER_STRAND.get());
        caps(event, IronworksBlockEntities.BLAST_FURNACE_CONTROLLER.get());
        caps(event, IronworksBlockEntities.BLAST_FURNACE_TOP.get());
        caps(event, IronworksBlockEntities.HOT_BLAST_STOVE.get());
        caps(event, IronworksBlockEntities.BLAST_BLOWER.get());
        caps(event, IronworksBlockEntities.DUST_CATCHER.get());
        caps(event, IronworksBlockEntities.CAST_HOUSE_MACHINE.get());
        caps(event, IronworksBlockEntities.LADLE.get());
        caps(event, IronworksBlockEntities.SLAG_GRANULATOR.get());
        // якоря верхних сегментов воздухонагревателя — без BE: трубы к ним идут на контроллер
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            FoundryDevice d = segmentController(level, pos, state);
            return d == null ? null : d.fluids(side);
        }, PcBlocks.HOT_BLAST_STOVE.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void caps(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }

    @Nullable
    private static FoundryDevice segmentController(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PcBlock b) || b.isController(state)) return null;
        return level.getBlockEntity(b.controllerPos(state, pos)) instanceof FoundryDevice d ? d : null;
    }

    // --- ковш на контрапции: конструкция едет целиком ---

    @Nullable
    private static BlockPos ladleController(Level level, BlockPos pos, BlockState state) {
        if (state.is(PcBlocks.LADLE.get())) return ((PcBlock) state.getBlock()).controllerPos(state, pos);
        if (!(state.getBlock() instanceof MultiblockPartBlock)) return null;
        BlockPos c = PcBlock.structureController(level, MultiblockPartBlock.anchor(state, pos));
        return c != null && level.getBlockState(c).is(PcBlocks.LADLE.get()) ? c : null;
    }

    private static CheckResult movementAllowed(BlockState state, Level level, BlockPos pos) {
        return ladleController(level, pos, state) == null ? CheckResult.PASS : CheckResult.SUCCESS;
    }

    private static CheckResult attached(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockPos c = ladleController(level, pos, state);
        if (c == null) return CheckResult.PASS;
        BlockPos n = pos.relative(direction);
        return Objects.equals(c, ladleController(level, n, level.getBlockState(n))) ? CheckResult.SUCCESS : CheckResult.PASS;
    }
}
