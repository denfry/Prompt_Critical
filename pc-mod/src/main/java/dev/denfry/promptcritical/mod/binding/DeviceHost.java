package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Реализуют все BE мода, имеющие доменный двойник (05 §5.3.1). */
public interface DeviceHost {
    SimId simId();

    /** Привязать к доменному объекту (биндинг при {@code attach}). */
    void bindSimId(SimId id);

    ServerLevel level();

    BlockPos pos();

    /** Ключ типа устройства в домене — id блока. */
    String deviceType();

    DeviceMode mode();

    /** {@code IPeripheral}, если CC загружен; создаётся лениво (03b §3b.7.7). */
    @Nullable
    default Object peripheral() {
        return null;
    }
}
