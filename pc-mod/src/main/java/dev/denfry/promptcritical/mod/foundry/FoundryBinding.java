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
import net.minecraft.world.level.block.Block;
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
        dev.denfry.promptcritical.mod.multiblock.PortCellMigration.register();
        SimBindings.registerSubsystem(level -> new FoundrySubsystem());
        SimBindings.registerSubsystem(level -> new CoalPrepSubsystem());
        SimBindings.registerSubsystem(level -> new dev.denfry.promptcritical.sim.mining.DenseMediumSubsystem());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        caps(event, FoundryBlockEntities.COAL_JIG.get());
        caps(event, FoundryBlockEntities.DENSE_MEDIUM_SEPARATOR.get());
        caps(event, FoundryBlockEntities.CUPOLA.get());
        // вагранка реального масштаба: склад колоши — только с порта площадки (WholePorts), клетка контроллера у жёлоба закрыта
        caps(event, FoundryBlockEntities.CRUCIBLE.get());
        caps(event, FoundryBlockEntities.MOLDING_STATION.get());
        caps(event, FoundryBlockEntities.SHAKEOUT_GRATE.get());
        caps(event, FoundryBlockEntities.MACHINE_LATHE.get());
        caps(event, FoundryBlockEntities.DRILL_PRESS.get());
        caps(event, FoundryBlockEntities.MILLING_MACHINE.get());
        caps(event, FoundryBlockEntities.SHAPING_MACHINE.get());
        caps(event, FoundryBlockEntities.SURFACE_GRINDER.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            FoundryDevice d = port(level, pos, MultiblockPartBlock.anchor(state, pos), side);
            return d == null ? null : d.items(side);
        }, PcBlocks.MULTIBLOCK_PART.get(), PcBlocks.PORT_CELL.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> portFluids(level, pos, MultiblockPartBlock.anchor(state, pos), side), PcBlocks.MULTIBLOCK_PART.get(),
                PcBlocks.PORT_CELL.get());
        // машины реального масштаба: якорь сегмента тоже бывает клеткой патрубка (тогда у него пустой BE клетки-патрубка)
        Block[] whole = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(PcBlocks::isWholeModel).toArray(Block[]::new);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            FoundryDevice d = ownEntity(be) ? null : port(level, pos, pos, side);
            return d == null ? null : d.items(side);
        }, whole);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> ownEntity(be) ? null : portFluids(level, pos, pos, side), whole);
    }

    /** У клетки свой BE машины (контроллер): его баки отдаёт провайдер BE, а не порт. */
    private static boolean ownEntity(@Nullable BlockEntity be) {
        return be != null && !(be instanceof dev.denfry.promptcritical.mod.multiblock.PortCellBlockEntity);
    }

    /**
     * Устройство за клеткой {@code pos} конструкции с якорем {@code anchor}; у машины реального масштаба — только если
     * клетка и грань — объявленный порт ({@link dev.denfry.promptcritical.mod.multiblock.WholePorts}), иначе трубы
     * цеплялись бы к любой невидимой клетке объёма.
     */
    @Nullable
    private static FoundryDevice port(Level level, BlockPos pos, BlockPos anchor, @Nullable net.minecraft.core.Direction side) {
        BlockPos c = PcBlock.structureController(level, anchor);
        if (c == null || !(level.getBlockEntity(c) instanceof FoundryDevice d)) return null;
        BlockState cs = level.getBlockState(c);
        if (PcBlocks.isWholeModel(cs.getBlock()) && cs.getBlock() instanceof PcBlock pc) {
            if (side == null) return null;
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cs.getBlock()).getPath();
            if (!dev.denfry.promptcritical.mod.multiblock.WholePorts.allows(id, pc.facing(cs), pos.getX() - c.getX(), pos.getY() - c.getY(),
                    pos.getZ() - c.getZ(), side)) return null;
        }
        return d;
    }

    /** Бак за патрубком в клетке {@code pos} машины реального масштаба: машина различает патрубки по клетке модели. */
    @Nullable
    private static net.neoforged.neoforge.fluids.capability.IFluidHandler portFluids(Level level, BlockPos pos, BlockPos anchor,
            @Nullable net.minecraft.core.Direction side) {
        FoundryDevice d = port(level, pos, anchor, side);
        if (d == null || side == null) return d == null ? null : d.fluids(side);
        BlockPos c = PcBlock.structureController(level, anchor);
        BlockState cs = c == null ? null : level.getBlockState(c);
        if (cs == null || !(cs.getBlock() instanceof PcBlock pc)) return d.fluids(side);
        net.minecraft.core.Direction f = pc.facing(cs);
        double[] m = dev.denfry.promptcritical.mod.multiblock.WholeShapes.unrotate(pos.getX() - c.getX() + 0.5, pos.getZ() - c.getZ() + 0.5, f);
        net.minecraft.core.Direction face = side.getAxis().isVertical() ? side
                : dev.denfry.promptcritical.mod.multiblock.WholePorts.modelFace(side, f);
        return d.fluidsAt((int) Math.floor(m[0]), pos.getY() - c.getY(), (int) Math.floor(m[1]), face, side);
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
