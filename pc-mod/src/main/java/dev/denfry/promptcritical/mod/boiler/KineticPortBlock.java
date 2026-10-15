package dev.denfry.promptcritical.mod.boiler;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Клетка конструкции, через которую машина обменивается вращением с сетью Create (03a §3a.12): невидимая, как
 * {@link MultiblockPartBlock}, но с валом по оси {@link #AXIS}. Источник ({@link KineticPortBlockEntity}) — у паровой
 * машины, при {@link #INPUT} — потребитель ({@link KineticInputBlockEntity}) у машин на валу. Вал Create подводят к
 * ней снаружи конструкции. Ключ Create меняет передачу машины.
 */
public final class KineticPortBlock extends MultiblockPartBlock implements IRotate, EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty INPUT = BooleanProperty.create("input");
    public static final MapCodec<KineticPortBlock> PORT_CODEC = simpleCodec(KineticPortBlock::new);

    public KineticPortBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X).setValue(INPUT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return PORT_CODEC;
    }

    public static BlockState forOffset(BlockPos offsetFromAnchor, Direction.Axis axis) {
        int idx = (offsetFromAnchor.getX() + 1) * 9 + (offsetFromAnchor.getY() + 1) * 3 + (offsetFromAnchor.getZ() + 1);
        return PcBlocks.KINETIC_PORT.get().defaultBlockState().setValue(CELL, idx).setValue(AXIS, axis);
    }

    /** Клетка-потребитель: вал входит в машину. */
    public static BlockState inputForOffset(BlockPos offsetFromAnchor, Direction.Axis axis) {
        return forOffset(offsetFromAnchor, axis).setValue(INPUT, true);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS, INPUT);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(INPUT) ? new KineticInputBlockEntity(pos, state) : new KineticPortBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (l, p, s, be) -> {
            if (be instanceof KineticBlockEntity k) k.tick();
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SmartBlockEntity sbe) sbe.destroy();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kbe && kbe.preventSpeedUpdate <= 0) {
            kbe.warnOfMovement();
            kbe.clearKineticInformation();
            kbe.updateSpeed = true;
        }
    }

    /** Ключом — передача машины, а не поворот клетки. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        BlockPos c = BoilerGeometry.controllerOf(context.getLevel(), context.getClickedPos());
        if (c == null) return InteractionResult.PASS;
        BlockEntity be = context.getLevel().getBlockEntity(c);
        if (be instanceof SteamEngineBlockEntity e) return e.cycleGear(context.getPlayer());
        return be instanceof KineticMachineBlock.Wrenchable w ? w.onWrenched(context) : InteractionResult.PASS;
    }
}
