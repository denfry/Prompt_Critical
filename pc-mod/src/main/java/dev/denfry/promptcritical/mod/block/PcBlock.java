package dev.denfry.promptcritical.mod.block;

import com.mojang.serialization.MapCodec;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Блок мода с набором свойств из спецификации и занимаемым объёмом {@link Footprint}. Многоклеточный блок ставится
 * целиком: якоря сегментов — этот же блок со своим значением свойства сегмента, остальные клетки —
 * {@link MultiblockPartBlock}. Разрушение любой клетки разрушает всю конструкцию; предмет выпадает с контроллера
 * (сегмент 0). Изменения блока помечают окрестность «грязной» для пересборки топологии (05 §5.3.3).
 */
public class PcBlock extends Block {
    /**
     * Свойства состояния; {@code segment} — свойство сегмента, если сегментов больше одного (значения по порядку
     * сегментов {@link Footprint}).
     */
    public record Spec(List<Property<?>> properties, boolean hasFacing, Footprint footprint,
            @Nullable EnumProperty<?> segment) {
        public Spec {
            properties = List.copyOf(properties);
            if (footprint.segments().size() > 1 && (segment == null || segment.getPossibleValues().size() != footprint.segments().size()))
                throw new IllegalArgumentException("segment property must have one value per footprint segment");
        }

        public static Spec of(Property<?>... props) {
            return new Spec(List.of(props), false, Footprint.SINGLE, null);
        }

        public static Spec facing(Property<?>... props) {
            return new Spec(List.of(props), true, Footprint.SINGLE, null);
        }

        public Spec footprint(Footprint fp) {
            return new Spec(properties, hasFacing, fp, segment);
        }

        public Spec segments(EnumProperty<?> segmentProperty, Footprint fp) {
            return new Spec(properties, hasFacing, fp, segmentProperty);
        }
    }

    /** Спецификация на время конструктора: {@link #createBlockStateDefinition} вызывается до присвоения полей. */
    private static final ThreadLocal<Spec> PENDING = new ThreadLocal<>();

    protected final Spec spec;
    private final MapCodec<PcBlock> codec;

    protected PcBlock(Properties props, Spec spec) {
        super(props);
        this.spec = spec;
        this.codec = simpleCodec(p -> PcBlock.create(p, spec));
        registerDefaultState(defaults(stateDefinition.any()));
    }

    public static PcBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new PcBlock(props, spec));
    }

    protected static <B extends PcBlock> B construct(Spec spec, java.util.function.Supplier<B> ctor) {
        PENDING.set(spec);
        try {
            return ctor.get();
        } finally {
            PENDING.remove();
        }
    }

    public Spec spec() {
        return spec;
    }

    @Override
    protected MapCodec<? extends PcBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Spec s = PENDING.get();
        if (s == null) throw new IllegalStateException("PcBlock must be created through PcBlock.create");
        if (s.hasFacing) builder.add(BlockStateProperties.HORIZONTAL_FACING);
        if (s.segment != null) builder.add(s.segment);
        for (Property<?> p : s.properties) builder.add(p);
    }

    /** Значения по умолчанию: первое значение каждого свойства, кроме оговорённых. */
    protected BlockState defaults(BlockState state) {
        if (state.hasProperty(PcProperties.RANK)) state = state.setValue(PcProperties.RANK, PcProperties.CoalRank.BITUMINOUS);
        if (state.hasProperty(PcProperties.CHIMNEY_PART)) state = state.setValue(PcProperties.CHIMNEY_PART, PcProperties.ChimneyPart.MIDDLE);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        for (Property<?> p : state.getProperties()) {
            if (p instanceof net.minecraft.world.level.block.state.properties.BooleanProperty b) state = state.setValue(b, false);
        }
        return state;
    }

    // --- сегменты и клетки ---

    public Direction facing(BlockState state) {
        return spec.hasFacing ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    public int segmentIndex(BlockState state) {
        if (spec.segment == null) return 0;
        return ((Enum<?>) state.getValue(spec.segment)).ordinal();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockState withSegment(BlockState state, int index) {
        if (spec.segment == null) return state;
        EnumProperty p = spec.segment;
        Enum<?> value = (Enum<?>) p.getValueClass().getEnumConstants()[index];
        return state.setValue(p, (Comparable) value);
    }

    public boolean isController(BlockState state) {
        return segmentIndex(state) == 0;
    }

    public BlockPos controllerPos(BlockState state, BlockPos pos) {
        return spec.footprint.controllerFrom(pos, facing(state), segmentIndex(state));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState();
        if (spec.hasFacing) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, ctx.getHorizontalDirection().getOpposite());
        if (spec.footprint.isSingle()) return state;
        Level level = ctx.getLevel();
        for (Footprint.Cell c : spec.footprint.cells(ctx.getClickedPos(), facing(state))) {
            if (c.pos().equals(ctx.getClickedPos())) continue;
            if (!level.isInWorldBounds(c.pos()) || !level.getBlockState(c.pos()).canBeReplaced(BlockPlaceContext.at(ctx, c.pos(), Direction.UP)))
                return null;
        }
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || oldState.is(this)) return;
        SimBindings.markDirty(level, pos);
        if (spec.footprint.isSingle() || !isController(state)) return;
        Direction f = facing(state);
        for (Footprint.Cell c : spec.footprint.cells(pos, f)) {
            if (c.pos().equals(pos)) continue;
            BlockState target = c.isAnchor()
                    ? withSegment(state, c.segment())
                    : partState(state, c);
            BlockState current = level.getBlockState(c.pos());
            // Игрока проверил getStateForPlacement; /setblock и шаблоны чужие блоки не затирают.
            if (current != target && (current.canBeReplaced() || current.getBlock() instanceof MultiblockPartBlock))
                level.setBlock(c.pos(), target, Block.UPDATE_ALL);
        }
    }

    /** Состояние клетки-части; машины ставят на место вала порт Create ({@code kinetic_port}). */
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        return MultiblockPartBlock.forOffset(cell.offsetFromAnchor());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            SimBindings.markDirty(level, pos);
            if (!spec.footprint.isSingle()) breakStructure(state, level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Убрать остальные клетки конструкции; если ушла не контроллерная клетка — разрушить контроллер с выпадением. */
    private void breakStructure(BlockState state, Level level, BlockPos pos) {
        BlockPos controller = controllerPos(state, pos);
        if (!isController(state)) {
            BlockState cs = level.getBlockState(controller);
            if (cs.is(this) && isController(cs)) level.destroyBlock(controller, true);
            return;
        }
        for (Footprint.Cell c : spec.footprint.cells(controller, facing(state))) {
            if (c.pos().equals(pos)) continue;
            BlockState s = level.getBlockState(c.pos());
            if (s.is(this) || s.getBlock() instanceof MultiblockPartBlock) level.removeBlock(c.pos(), false);
        }
    }

    /** Контроллер конструкции для клетки-части; {@code null}, если её нет. */
    @Nullable
    public static BlockPos structureController(Level level, BlockPos anchor) {
        BlockState s = level.getBlockState(anchor);
        if (s.getBlock() instanceof PcBlock b && !b.spec.footprint.isSingle()) return b.controllerPos(s, anchor);
        return null;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // В творческом режиме конструкция разбирается без выпадения предмета контроллера.
        if (!level.isClientSide && player.isCreative() && !spec.footprint.isSingle() && !isController(state)) {
            BlockPos controller = controllerPos(state, pos);
            if (level.getBlockState(controller).is(this)) level.removeBlock(controller, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (!spec.hasFacing || !spec.footprint.isSingle()) return state;
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (!spec.hasFacing || !spec.footprint.isSingle()) return state;
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
}
