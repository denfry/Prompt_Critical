package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Спасательная тренога {@code rescue_tripod} над люком (13 §13.2.6): пристегните к ней фал входящего (ПКМ фалом);
 * наблюдающий снаружи ПКМ по треноге вытягивает лебёдкой за {@value #WINCH_TICKS} тиков (5 с) того, кто внутри
 * пространства соседнего люка в привязи с фалом на этой треноге, — даже без сознания и не входя самому.
 */
public final class RescueTripodBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int WINCH_TICKS = 100, MANWAY_RANGE = 3;

    @Nullable
    private ServerPlayer winching;
    private int winchLeft;
    @Nullable
    private ManwayBlockEntity winchManway;

    public RescueTripodBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.RESCUE_TRIPOD.get(), pos, state);
    }

    /** Ближайший люк с замкнутым пространством. */
    @Nullable
    ManwayBlockEntity manway(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-MANWAY_RANGE, -MANWAY_RANGE, -MANWAY_RANGE),
                worldPosition.offset(MANWAY_RANGE, MANWAY_RANGE, MANWAY_RANGE)))
            if (level.getBlockEntity(p) instanceof ManwayBlockEntity m && m.confined()) return m;
        return null;
    }

    /** Фал игрока закреплён на этой треноге и надета привязь. */
    boolean tethered(Player p) {
        if (!PpeItem.is(p.getItemBySlot(EquipmentSlot.LEGS), PpeItem.Kind.SAFETY_HARNESS)) return false;
        for (ItemStack s : p.getInventory().items) {
            GlobalPos a = s.getItem() instanceof LifelineItem ? s.get(SafetyComponents.LIFELINE_ANCHOR.get()) : null;
            if (a != null && a.dimension() == p.level().dimension() && a.pos().equals(worldPosition)) return true;
        }
        return false;
    }

    public boolean winching() {
        return winching != null;
    }

    /** Начать подъём: {@code true} — нашёлся пристёгнутый внутри. */
    public boolean startWinch(ServerPlayer rescuer) {
        return startWinch(rescuer, rescuer.serverLevel().players());
    }

    /** То же среди {@code candidates} (испытания передают поддельных игроков, которых нет в списке мира). */
    public boolean startWinch(ServerPlayer rescuer, java.util.Collection<ServerPlayer> candidates) {
        ServerLevel level = rescuer.serverLevel();
        ManwayBlockEntity m = manway(level);
        if (m == null) {
            rescuer.displayClientMessage(SafetyText.msg("tripod_no_manway").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        if (m.inside(rescuer)) {
            rescuer.displayClientMessage(SafetyText.msg("tripod_from_outside").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        for (ServerPlayer p : candidates) {
            if (p == rescuer || !m.inside(p) || !tethered(p)) continue;
            winching = p;
            winchLeft = WINCH_TICKS;
            winchManway = m;
            level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
            rescuer.displayClientMessage(SafetyText.msg("tripod_winching", p.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        rescuer.displayClientMessage(SafetyText.msg("tripod_nobody").withStyle(ChatFormatting.GOLD), true);
        return false;
    }

    @Override
    public InteractionResult use(Player player) {
        if (player instanceof ServerPlayer sp && winching == null) startWinch(sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || winching == null) return;
        if (--winchLeft % 20 == 0) sl.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 1.0f, 0.7f);
        if (winchLeft > 0) return;
        ServerPlayer p = winching;
        if (!p.isRemoved() && winchManway != null && !winchManway.isRemoved() && winchManway.inside(p)) {
            winchManway.leave(p);
            p.displayClientMessage(SafetyText.msg("tripod_rescued").withStyle(ChatFormatting.GREEN), true);
        }
        winching = null;
        winchManway = null;
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof LifelineItem;
    }

    /** Фал — к самому фалу: он запомнит треногу как опору. */
    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }
}
