package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BE устройства сети без вала (07 §7.4–7.6): двойник в {@link GridSubsystem}, регистрация в {@link GridWorld} для поиска
 * узлов. Руки игрока и тик — через {@link FoundryDevice} (блок {@code FoundryBlock}); местный щит — у наследников с
 * {@code PanelHost}.
 *
 * @param <U> двойник
 */
public abstract class GridDeviceBlockEntity<U extends GridUnit> extends DeviceBlockEntity implements GridDevice, FoundryDevice {
    private boolean registered;
    protected int ticks;
    /** На аппарате висит плакат «Не включать! Работают люди» (открыт наряд-допуск); синхронизируется клиенту. */
    private boolean posterHung;

    protected GridDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Двойник; {@code null} — биндинга нет или устройство ещё не в топологии. */
    @Nullable
    public U unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone() || !b.world().topology().exists(simId())) return null;
        GridSubsystem g = b.world().subsystem(GridSubsystem.class);
        return g == null ? null : unitOf(g);
    }

    protected abstract U unitOf(GridSubsystem grid);

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId().isNone() ? null : unitOf(grid);
    }

    protected Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock pc ? pc.facing(s) : Direction.NORTH;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) {
                b.grids().register(worldPosition);
                registered = true;
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (registered && level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.grids().unregister(worldPosition);
            registered = false;
        }
    }

    @Override
    public void tickDevice() {
        ticks++;
        if (level == null || level.isClientSide) return;
        U u = unit();
        if (u != null) serverTick(u);
    }

    /** Серверный тик с двойником. */
    protected void serverTick(U unit) {
    }

    public boolean posterHung() {
        return posterHung;
    }

    /** Плакат наряда на аппарате: при изменении — клиенту (рендер плаката). */
    protected void setPosterHung(boolean hung) {
        if (posterHung == hung || level == null) return;
        posterHung = hung;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Клиенту: плакат наряда; наследники с собственной синхронизацией дописывают своё в этот тег. */
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("poster", posterHung);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // в сохранении мира флага нет — сервер восстанавливает его по двойнику
        if (tag.contains("poster")) posterHung = tag.getBoolean("poster");
    }

    /** Поставить булево свойство блока (контроллера), если изменилось. */
    protected void setFlag(BooleanProperty prop, boolean value) {
        BlockState s = getBlockState();
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(worldPosition, s.setValue(prop, value), Block.UPDATE_CLIENTS);
    }

    // --- FoundryDevice: склада нет, предметы — по месту ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }
}
