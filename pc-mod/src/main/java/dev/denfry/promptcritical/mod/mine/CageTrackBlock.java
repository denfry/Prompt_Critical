package dev.denfry.promptcritical.mod.mine;

import com.mojang.serialization.MapCodec;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.FootprintCell;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Встроенный путь клети шахты: клетки пола под дверью — рельсы вдоль оси клети, вровень с рельсами посадки, так что
 * вагонетка закатывается в клеть, едет в ней и выкатывается на другом горизонте (клеть в шахте возит вагонетки).
 * Это клетка конструкции клети ({@link FootprintCell}): ставится, едет контрапцией и разбирается вместе с ней.
 *
 * <p>Стопоры (как кулаки-задержки настоящей клети): при закрытой двери вагонетка стоит; въезжающая тормозится и
 * встаёт внутри; к двери она катится свободно — её выталкивают руками или запитанным рельсом посадки перед открытой
 * дверью.
 */
public final class CageTrackBlock extends BaseRailBlock implements FootprintCell {
    public static final MapCodec<CageTrackBlock> CODEC = simpleCodec(CageTrackBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    /** Скорость выталкивания запитанным рельсом посадки, блоков за тик (как у разогнанной вагонетки). */
    static final double PUSH = 0.4, BRAKE = 0.55;

    public CageTrackBlock(Properties props) {
        super(true, props);
        registerDefaultState(stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, false)
                .setValue(MultiblockPartBlock.CELL, 13));
    }

    @Override
    protected MapCodec<? extends BaseRailBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED, MultiblockPartBlock.CELL);
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    /** Состояние пути для клетки со смещением {@code offsetFromAnchor} в клети с фасадом {@code facing}. */
    public BlockState forCell(BlockPos offsetFromAnchor, Direction facing) {
        return defaultBlockState().setValue(MultiblockPartBlock.CELL, MultiblockPartBlock.forOffset(offsetFromAnchor).getValue(MultiblockPartBlock.CELL))
                .setValue(SHAPE, facing.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
    }

    // --- путь не перекладывается и не требует опоры: он часть клети и ездит с ней по стволу ---

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {}

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {}

    @Override
    protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean alwaysPlace) {
        return state;
    }

    @Override
    public boolean isValidRailShape(RailShape shape) {
        return shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST;
    }

    @Override
    public boolean canMakeSlopes(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    // --- форма: пол и стенки клети (решает клеть), а не плоский рельс ---

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos anchor = MultiblockPartBlock.anchor(state, pos);
        BlockState a = level.getBlockState(anchor);
        return a.getBlock() instanceof PcBlock pc ? pc.partShape(a, level, anchor, pos.subtract(anchor)) : Shapes.block();
    }

    /** Пол пути — 1 px: людям и грузам на ходу клети это опора, а вагонетку на рельсе ведёт путь, ступенька её бы держала. */
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 1, 16);

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = getShape(state, level, pos, context);
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec && ec.getEntity() instanceof AbstractMinecart)
            return Shapes.join(s, FLOOR, net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        return s;
    }

    @Override
    public boolean hasDynamicShape() {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // --- стопоры ---

    @Override
    public void onMinecartPass(BlockState state, Level level, BlockPos pos, AbstractMinecart cart) {
        if (level.isClientSide) return;
        BlockPos anchor = MultiblockPartBlock.anchor(state, pos);
        BlockState a = level.getBlockState(anchor);
        if (!(a.getBlock() instanceof MineCageBlock cage)) return;
        Direction door = cage.facing(a);
        if (!cage.open(a)) {
            cart.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (landingPowered(level, pos, door)) {
            cart.setDeltaMovement(door.getStepX() * PUSH, cart.getDeltaMovement().y, door.getStepZ() * PUSH);
            return;
        }
        Vec3 v = cart.getDeltaMovement();
        double along = v.x * door.getStepX() + v.z * door.getStepZ();
        if (along < -1e-3) {
            Vec3 slow = v.scale(BRAKE);
            cart.setDeltaMovement(slow.horizontalDistance() < 0.02 ? Vec3.ZERO : slow);
        }
    }

    /** Рельс посадки прямо перед дверью запитан (сам или сигналом рядом). */
    private static boolean landingPowered(Level level, BlockPos pos, Direction door) {
        BlockPos p = pos;
        while (level.getBlockState(p.relative(door)).getBlock() instanceof CageTrackBlock) p = p.relative(door);
        BlockPos landing = p.relative(door);
        BlockState s = level.getBlockState(landing);
        if (!(s.getBlock() instanceof BaseRailBlock)) return false;
        return s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED) || level.hasNeighborSignal(landing);
    }

    // --- как у невидимой клетки: руки и разрушение — к клети ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos anchor = MultiblockPartBlock.anchor(state, pos);
        return level.getBlockState(anchor).useWithoutItem(level, player, hit.withPosition(anchor));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        BlockPos anchor = MultiblockPartBlock.anchor(state, pos);
        return level.getBlockState(anchor).useItemOn(stack, level, player, hand, hit.withPosition(anchor));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos anchor = MultiblockPartBlock.anchor(state, pos);
        BlockState a = level.getBlockState(anchor);
        return a.getBlock() instanceof PcBlock b ? b.getCloneItemStack(level, anchor, a) : ItemStack.EMPTY;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos controller = PcBlock.structureController(level, MultiblockPartBlock.anchor(state, pos));
            if (controller != null) level.removeBlock(controller, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && !PcBlock.isRelocating() && !movedByPiston) {
            @Nullable BlockPos controller = PcBlock.structureController(level, MultiblockPartBlock.anchor(state, pos));
            if (controller != null) {
                BlockState cs = level.getBlockState(controller);
                if (cs.getBlock() instanceof PcBlock b && b.isController(cs)) level.destroyBlock(controller, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
