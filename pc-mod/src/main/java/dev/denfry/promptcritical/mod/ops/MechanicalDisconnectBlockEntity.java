package dev.denfry.promptcritical.mod.ops;

import com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.EnergyKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Расцепная муфта: передача Create как у муфты (выход стоит, пока муфта расцеплена) и точка изоляции механической
 * энергии. Замок из реестра попадает в модель раз в полсекунды.
 */
public class MechanicalDisconnectBlockEntity extends ClutchBlockEntity implements IsolationPoint {
    private int ticks;

    public MechanicalDisconnectBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.MECHANICAL_DISCONNECT.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || ++ticks % 10 != 0) return;
        boolean locked = Lockouts.locked(level, worldPosition);
        BlockState s = getBlockState();
        if (s.hasProperty(MechanicalDisconnectBlock.LOCKED) && s.getValue(MechanicalDisconnectBlock.LOCKED) != locked)
            level.setBlock(worldPosition, s.setValue(MechanicalDisconnectBlock.LOCKED, locked), 2);
    }

    @Override
    public EnergyKind energy() {
        return EnergyKind.MECHANICAL;
    }

    @Override
    public boolean isolatedPosition() {
        return getBlockState().getValue(MechanicalDisconnectBlock.POWERED);
    }

    /** Выход расцепленной муфты не вращается от входа; проверка — что и сам вход не держит выходную половину. */
    @Override
    public boolean noEnergy() {
        if (!isolatedPosition()) return false;
        Direction source = getSourceFacing();
        return source == null || getRotationSpeedModifier(source.getOpposite()) == 0;
    }

    @Override
    public SimId simId() {
        return SimId.NONE;
    }
}
