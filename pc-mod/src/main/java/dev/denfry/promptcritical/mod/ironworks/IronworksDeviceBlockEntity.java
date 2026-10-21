package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Агрегат с доменным двойником в {@code IronmakingSubsystem} (как {@code DeviceBlockEntity}, 05 §5.3.1). */
public abstract class IronworksDeviceBlockEntity<U extends IronmakingUnit> extends IronworksBlockEntity implements DeviceHost {
    private final Class<U> unitClass;
    private SimId simId = SimId.NONE;
    private boolean chunkUnloading;

    protected IronworksDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Class<U> unitClass) {
        super(type, pos, state);
        this.unitClass = unitClass;
    }

    @Nullable
    public U unit() {
        return Ironworks.unit(Ironworks.binding(level), simId, unitClass);
    }

    @Override
    protected final void serverTick(SimLevelBinding b) {
        U u = Ironworks.unit(b, simId, unitClass);
        if (u != null) serverTick(b, u);
    }

    protected abstract void serverTick(SimLevelBinding b, U unit);

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
        SimLevelBinding b = Ironworks.binding(level);
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return DeviceMode.LOCAL;
        return b.world().topology().deviceMode(simId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null) b.attach(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null) b.detach(this, !chunkUnloading);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("sim_id", simId.raw());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        simId = new SimId(tag.getLong("sim_id"));
    }
}
