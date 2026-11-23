package dev.denfry.promptcritical.mod.manufacturing;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Кузнечная машина на валу Create (18 §18.7, механизированная ступень): молот, пресс, волочильный стан. Берёт с вала
 * постоянный импульс стресса {@code RATED_W / (W_PER_SU · n_ном)}, поэтому мощность растёт с оборотами:
 * P = W_PER_SU · impact · |n|. Пока вал перегружен или стоит, машина не работает. Процесс идёт раз в секунду игры.
 */
public abstract class PoweredSmithyDevice extends KineticBlockEntity implements FoundryDevice, PanelHost,
        dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    // --- цельная модель (реальный масштаб) ---

    /** Id цельной модели у машины реального масштаба (блок {@code *_real}); у старой — null. */
    @Override
    @Nullable
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    /** Свет — с фасада, перед контроллером. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing());
    }

    protected final MachineInventory inv;

    protected PoweredSmithyDevice(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineInventory.Role[] roles,
            BiPredicate<Integer, ItemStack> filter) {
        super(type, pos, state);
        inv = new MachineInventory(roles, filter, this::setChanged);
    }

    /** Мощность при номинальных оборотах, Вт. */
    protected abstract double ratedW();

    /** Номинальные обороты вала, об/мин. */
    protected abstract double nominalRpm();

    /** Секунда процесса на сервере с мощностью вала {@code shaftW}. */
    protected abstract void second(double shaftW);

    /** Импульс стресса, SU на об/мин. */
    public float impact() {
        return (float) (ratedW() / (ServerConfig.W_PER_SU.get() * nominalRpm()));
    }

    /** Мощность на валу сейчас, Вт: 0, если вал стоит или сеть Create перегружена. */
    public double shaftW() {
        if (getSpeed() == 0 || isOverStressed()) return 0;
        return ServerConfig.W_PER_SU.get() * impact() * Math.abs(getSpeed());
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = impact();
        return lastStressApplied;
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel)) return;
        if (level.getGameTime() % 20 == 0) {
            double w = shaftW();
            second(w);
            setRunning(w > 0 && working());
        }
    }

    /** Прогнать {@code n} секунд процесса без хода времени мира: GameTest и проверка машины на щите. */
    public void runSeconds(int n) {
        for (int i = 0; i < n; i++) second(shaftW());
    }

    /** Машина сейчас что-то делает: для свойства {@code running} и анимации. */
    protected abstract boolean working();

    private void setRunning(boolean on) {
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != on)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, on), Block.UPDATE_ALL);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.getCount() > 1 && (Smithy.forgeable(stack) || Smithy.drawable(stack))) {
            ItemStack rest = inv.insertFromPlayer(stack.split(1));
            stack.grow(rest.getCount());
            player.setItemInHand(hand, stack);
        } else player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public Component panelTitle() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    /** Обороты вала ×10 для щита. */
    protected int rpm10() {
        return Math.round(Math.abs(getSpeed()) * 10);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
