package dev.denfry.promptcritical.mod.foundry;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Машина на валу Create (03a §3a.12.2): вал входит в контроллер с одной стороны. Повторяет то, что делает
 * {@code KineticBlock} Create (сохранение сети при смене состояния, перестройка при смене соседей, снятие BE),
 * поверх объёма и свойств {@link dev.denfry.promptcritical.mod.block.PcBlock}.
 */
public class KineticMachineBlock extends FoundryBlock implements IRotate {
    /** Где вал: слева от фасада (−x модели), сзади (+z модели), спереди (выход двигателя) или по оси X без фасада. */
    public enum ShaftSide { LEFT, BACK, FRONT, X_AXIS }

    private final ShaftSide side;

    protected KineticMachineBlock(Properties props, Spec spec, ShaftSide side,
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory) {
        super(props, spec, factory);
        this.side = side;
    }

    public static BiFunction<Properties, Spec, PcDeviceBlock> with(ShaftSide side,
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new KineticMachineBlock(p, s, side, be));
    }

    /** Грань контроллера, в которую входит вал (у {@link ShaftSide#X_AXIS} — западная, вал проходит насквозь). */
    public Direction shaftFace(BlockState state) {
        return switch (side) {
            case LEFT -> facing(state).getCounterClockWise();
            case BACK -> facing(state).getOpposite();
            case FRONT -> facing(state);
            case X_AXIS -> Direction.WEST;
        };
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        if (!isController(state)) return false;
        return side == ShaftSide.X_AXIS ? face.getAxis() == Direction.Axis.X : face == shaftFace(state);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return shaftFace(state).getAxis();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kbe) {
            kbe.preventSpeedUpdate = 0;
            if (oldState.getBlock() == state.getBlock() && state.hasBlockEntity() == oldState.hasBlockEntity()
                    && getRotationAxis(oldState) == getRotationAxis(state)) {
                kbe.preventSpeedUpdate = 2;                          // смена running/lit — сеть не перестраивается
            }
        }
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

    /** Ключом Create машину не поворачивают: объём многоклеточный. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return context.getLevel().getBlockEntity(controllerPos(state, context.getClickedPos())) instanceof Wrenchable w
                ? w.onWrenched(context) : InteractionResult.PASS;
    }

    /** BE, у которого ключ меняет настройку (плотность разделения отсадки). */
    public interface Wrenchable {
        InteractionResult onWrenched(UseOnContext context);
    }
}
