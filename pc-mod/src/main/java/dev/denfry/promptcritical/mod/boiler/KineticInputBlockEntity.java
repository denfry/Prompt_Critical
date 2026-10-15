package dev.denfry.promptcritical.mod.boiler;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.foundry.StressFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Потребитель Create на клетке вала машины (03a §3a.12.2): каждый серверный тик отдаёт скорость вала контроллеру
 * {@link ShaftConsumer}, обратно объявляет его мощность стрессом через фильтр R4.
 */
public class KineticInputBlockEntity extends KineticBlockEntity {
    private final StressFilter stress = new StressFilter();

    public KineticInputBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.KINETIC_INPUT.get(), pos, state);
    }

    @Nullable
    public ShaftConsumer owner() {
        if (level == null) return null;
        BlockPos c = BoilerGeometry.controllerOf(level, worldPosition);
        return c != null && level.getBlockEntity(c) instanceof ShaftConsumer o ? o : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel s)) return;
        ShaftConsumer o = owner();
        if (o == null) return;
        SimLevelBinding b = SimBindings.get(s);
        o.sampleShaft(getSpeed(), !isOverStressed(), b == null ? s.getGameTime() : b.world().clock().tick());
        if (stress.update(o.shaftPowerW(), getSpeed(), o.ratedShaftW(), o.nominalShaftRpm(), s.getGameTime()) && hasNetwork())
            getOrCreateNetwork().updateStressFor(this, stress.impact());
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = stress.impact();
        return lastStressApplied;
    }
}
