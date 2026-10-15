package dev.denfry.promptcritical.mod.foundry;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * BE машины на валу Create с доменным двойником (03a §3a.12.2, 05 §5.3.1). Каждый серверный тик пишет скорость вала
 * в {@link ShaftInput} двойника (шаг 1 §3a.1), после шага домена объявляет нагрузку: мощность двойника → импульс
 * стресса Create {@code P / (W_PER_SU · n)} через фильтр R4 (не чаще раза в секунду и при изменении &gt; 5 %). Пока вал
 * стоит, объявляется номинальная мощность при номинальной скорости — в очках инженера видна будущая нагрузка.
 *
 * @param <U> доменный двойник
 */
public abstract class KineticMachineBlockEntity<U> extends KineticBlockEntity implements DeviceHost, FoundryDevice {
    private static final long STRESS_PERIOD_TICKS = 20;
    private static final double STRESS_DEADBAND = 0.05;

    private SimId simId = SimId.NONE;
    private boolean chunkUnloading;
    private float impact;
    private double declaredW = -1;
    private long lastStressTick = Long.MIN_VALUE / 2;

    protected KineticMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Двойник в домене; {@code null} — биндинга или устройства нет. */
    @Nullable
    protected abstract U unit(SimLevelBinding binding);

    protected abstract ShaftInput shaft(U unit);

    /** Мощность на валу сейчас, Вт. */
    protected abstract double powerW(U unit);

    /** Номинальная мощность и скорость — для импульса, пока вал стоит. */
    protected abstract double ratedW();

    protected abstract double nominalRpm();

    /** Серверный тик после записи вала: материалы, состояние блока. */
    protected abstract void serverTick(SimLevelBinding binding, U unit);

    @Nullable
    protected U unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? null : unit(b);
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        U u = b == null || simId.isNone() ? null : unit(b);
        if (u == null) return;
        shaft(u).sample(getSpeed(), !isOverStressed(), b.world().clock().tick());
        serverTick(b, u);
        declareStress(powerW(u));
    }

    /** Фильтр R4 (03a §3a.2.4). */
    private void declareStress(double powerW) {
        long now = level.getGameTime();
        double p = Math.max(0, powerW);
        boolean zeroCross = (p == 0) != (declaredW == 0);
        if (declaredW >= 0 && now - lastStressTick < STRESS_PERIOD_TICKS) return;
        if (declaredW >= 0 && !zeroCross && Math.abs(p - declaredW) <= STRESS_DEADBAND * declaredW) return;
        double wPerSu = ServerConfig.W_PER_SU.get();
        double rpm = Math.abs(getSpeed());
        impact = (float) (rpm == 0 ? ratedW() / (wPerSu * nominalRpm()) : p / (wPerSu * Math.max(rpm, 0.1 * nominalRpm())));
        declaredW = p;
        lastStressTick = now;
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, impact);
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = impact;
        return impact;
    }

    /** Поставить булево свойство контроллера, если изменилось. */
    protected void setStateFlag(BooleanProperty prop, boolean value) {
        BlockState s = getBlockState();
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(worldPosition, s.setValue(prop, value), Block.UPDATE_ALL);
    }

    // --- DeviceHost ---

    @Override
    public SimId simId() {
        return simId;
    }

    @Override
    public void bindSimId(SimId id) {
        if (!id.equals(simId)) {
            simId = id;
            setChanged();
        }
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) level;
    }

    @Override
    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public String deviceType() {
        return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString();
    }

    @Override
    public DeviceMode mode() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return DeviceMode.LOCAL;
        return b.world().topology().deviceMode(simId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.attach(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.detach(this, !chunkUnloading);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.putLong("sim_id", simId.raw());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) simId = new SimId(tag.getLong("sim_id"));
    }
}
