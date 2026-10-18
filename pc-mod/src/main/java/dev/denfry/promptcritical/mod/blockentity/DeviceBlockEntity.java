package dev.denfry.promptcritical.mod.blockentity;

import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.registry.PcBlockEntities;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BE устройства (05 §5.3.1, §5.2.8): хранит только {@link SimId}; физика живёт в домене. При загрузке на сервере
 * привязывается к доменному объекту или создаёт его, при разрушении блока — снимает его с топологии, при выгрузке
 * чанка — только отвязывается.
 */
public class DeviceBlockEntity extends BlockEntity implements DeviceHost {
    private SimId simId = SimId.NONE;
    private boolean chunkUnloading;

    public DeviceBlockEntity(BlockPos pos, BlockState state) {
        super(PcBlockEntities.DEVICE.get(), pos, state);
    }

    /** BE устройства со своим поведением (этапы 6–8). */
    protected DeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

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
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.detach(this, !chunkUnloading);
        }
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

    // --- местный щит (для наследников, реализующих PanelHost) ---

    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    public BlockPos panelPos() {
        return worldPosition;
    }

    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }
}
