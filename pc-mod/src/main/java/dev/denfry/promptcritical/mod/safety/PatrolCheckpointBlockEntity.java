package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PatrolRound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Метка маршрута обхода {@code patrol_checkpoint} (13 §13.3.1) на оборудовании. Помнит последнее посещение; если с
 * него прошло больше {@value PatrolRound#PERIOD_S} с, горит сигнальная лампа (свойство {@code alarm}) и в журнал
 * станции пишется «ОБХОД ПРОСРОЧЕН» (P4, журнальная). Метка, на которой ещё никто не отмечался, не тревожит.
 */
public final class PatrolCheckpointBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    /** Метка ещё не посещалась. */
    public static final long NEVER = Long.MIN_VALUE;
    private long lastVisit = NEVER;
    private boolean overdue;

    public PatrolCheckpointBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.PATROL_CHECKPOINT.get(), pos, state);
    }

    public long lastVisit() {
        return lastVisit;
    }

    /** Отметка обходчика. */
    public void visited(long tick) {
        lastVisit = tick;
        setChanged();
        update();
    }

    /** Для испытаний: сдвинуть последнее посещение. */
    public void setLastVisit(long tick) {
        lastVisit = tick;
        update();
    }

    public boolean overdue() {
        return overdue;
    }

    private void update() {
        if (level == null || level.isClientSide) return;
        boolean now = lastVisit != NEVER && level.getGameTime() - lastVisit > PatrolRound.PERIOD_S * PatrolRound.TICKS_PER_S;
        if (now && !overdue) {
            OpsSubsystem ops = OpsWorld.ops(level);
            if (ops != null) ops.journal(dev.denfry.promptcritical.sim.core.SimId.NONE, OpsLogEntry.Kind.PATROL_OVERDUE, "@" + worldPosition.toShortString());
        }
        overdue = now;
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.ALARM) && s.getValue(PcProperties.ALARM) != now)
            level.setBlock(worldPosition, s.setValue(PcProperties.ALARM, now), Block.UPDATE_ALL);
    }

    @Override
    public void tickDevice() {
        if (level != null && !level.isClientSide && (level.getGameTime() + worldPosition.hashCode()) % 20 == 0) update();
    }

    @Override
    public InteractionResult use(Player player) {
        if (!level.isClientSide) player.displayClientMessage(SafetyText.msg("checkpoint_hint"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    /** Считыватель — к самому считывателю: он отмечает посещение. */
    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof PatrolReaderItem;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("last_visit", lastVisit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastVisit = tag.contains("last_visit") ? tag.getLong("last_visit") : NEVER;
    }
}
