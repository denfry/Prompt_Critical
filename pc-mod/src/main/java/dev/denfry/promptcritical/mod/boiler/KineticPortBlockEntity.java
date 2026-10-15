package dev.denfry.promptcritical.mod.boiler;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Источник Create на клетке вала паровой машины (03a §3a.12.3): скорость и ёмкость берёт у
 * {@link SteamEngineBlockEntity}, обратно отдаёт долю нагрузки сети
 * P_Create = W_PER_SU·min(S, C)·C_own/C (03a §3a.7.3); при перегрузке сети — 0.
 */
public class KineticPortBlockEntity extends GeneratingKineticBlockEntity {
    private float lastRpm;

    public KineticPortBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.KINETIC_PORT.get(), pos, state);
    }

    @Nullable
    private SteamEngineBlockEntity owner() {
        if (level == null) return null;
        BlockPos c = BoilerGeometry.controllerOf(level, worldPosition);
        return c != null && level.getBlockEntity(c) instanceof SteamEngineBlockEntity e ? e : null;
    }

    @Override
    public float getGeneratedSpeed() {
        SteamEngineBlockEntity o = owner();
        return o == null ? 0 : o.portRpm();
    }

    @Override
    public float calculateAddedStressCapacity() {
        SteamEngineBlockEntity o = owner();
        float c = o == null ? 0 : o.portCapacityPerRpm();
        lastCapacityProvided = c;
        return c;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        SteamEngineBlockEntity o = owner();
        if (o == null) return;
        float rpm = o.portRpm();
        if (rpm != lastRpm) {
            lastRpm = rpm;
            updateGeneratedRotation();
        } else if (o.portCapacityPerRpm() != lastCapacityProvided) {
            float c = calculateAddedStressCapacity();
            if (hasNetwork() && getOrCreateNetwork() != null) notifyStressCapacityChange(c);
        }
        double load = 0;
        float speed = Math.abs(getSpeed());
        if (speed > 0 && !isOverStressed() && capacity > 0) {
            double own = lastCapacityProvided * speed;
            load = ServerConfig.W_PER_SU.get() * Math.min(stress, capacity) * own / capacity;
        }
        o.setPortLoad(load);
    }
}
