package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BE агрегата коксохима и домны без вала Create: тик (сервер — {@link #serverTick}, клиент — эффекты), руки игрока,
 * склады для автоматики. Блок — {@link dev.denfry.promptcritical.mod.foundry.FoundryBlock}.
 */
public abstract class IronworksBlockEntity extends BlockEntity implements FoundryDevice {
    protected IronworksBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tickDevice() {
        if (level instanceof ServerLevel) {
            SimLevelBinding b = Ironworks.binding(level);
            if (b != null) serverTick(b);
        } else if (level != null) {
            clientTick();
        }
    }

    protected abstract void serverTick(SimLevelBinding b);

    protected void clientTick() {
    }

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

    protected MachineFx fx() {
        BlockState s = getBlockState();
        return MachineFx.of(level, worldPosition, s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH);
    }

    protected Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    protected void setStateFlag(BooleanProperty prop, boolean value) {
        BlockState s = getBlockState();
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(worldPosition, s.setValue(prop, value), Block.UPDATE_ALL);
    }

    protected boolean stateFlag(BooleanProperty prop) {
        BlockState s = getBlockState();
        return s.hasProperty(prop) && s.getValue(prop);
    }

    // --- данные для клиента (рендер частей): только то, что пишет writeClient ---

    /** Что видит клиент (наклон, ход фурмы, заготовка на рольганге); по умолчанию — ничего. */
    protected void writeClient(CompoundTag tag, HolderLookup.Provider registries) {
    }

    protected void readClient(CompoundTag tag, HolderLookup.Provider registries) {
    }

    /** Отправить клиенту {@link #writeClient}. */
    protected void syncClient() {
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeClient(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readClient(tag, registries);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        readClient(pkt.getTag(), registries);
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
