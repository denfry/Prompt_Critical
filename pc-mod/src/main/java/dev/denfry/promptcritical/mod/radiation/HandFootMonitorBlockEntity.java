package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.radiation.ContaminationLimits;
import dev.denfry.promptcritical.sim.radiation.ContaminationProbe;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Установка контроля рук и ног на выходе из зоны через санпропускник: ПКМ — ладони на датчики, ноги на
 * подставку. Руки сверяются с нормой для кожи, обувь — для спецобуви (НРБ-99/2009, табл. 8.9) по показанию зонда:
 * чего прибор не видит (ниже МДА), того для него нет.
 */
public final class HandFootMonitorBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    /** Блоки детектирования и табло установки, Вт. Малая нагрузка 0,4 кВ: розетка, щит или ИБП рядом. */
    public static final double POWER_W = 100;
    public HandFootMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.HAND_FOOT_MONITOR.get(), pos, state);
    }

    /** Кратность превышения нормы по показаниям зонда: руки (кожа) и обувь (спецобувь), худшая. */
    public static double worstRatio(ServerPlayer p) {
        return Math.max(measured(ContaminationService.hands(p), ContaminationLimits.Surface.SKIN),
                measured(ContaminationService.shoes(p), ContaminationLimits.Surface.OVERALLS));
    }

    private static double measured(SurfaceContamination c, ContaminationLimits.Surface limit) {
        ContaminationProbe pr = ContaminationService.PROBE;
        double a = pr.alphaBqCm2(pr.alphaCps(c)), b = pr.betaBqCm2(pr.betaCps(c));
        if (a < pr.alphaMda()) a = 0;
        if (b < pr.betaMda()) b = 0;
        return Math.max(a / limit.alphaBqCm2(), b / limit.betaBqCm2());
    }

    @Override
    public InteractionResult use(Player player) {
        if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        if (!dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(dev.denfry.promptcritical.mod.grid.GridText.NO_POWER), true);
            return InteractionResult.SUCCESS;
        }
        double r = worstRatio(p);
        if (r > 1) {
            p.displayClientMessage(ContaminationText.msg("monitor.dirty", ContaminationText.num(r)).withStyle(ChatFormatting.RED), false);
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 0.5f);
        } else {
            p.displayClientMessage(ContaminationText.msg("monitor.clean").withStyle(ChatFormatting.GREEN), false);
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.5f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tickDevice() {
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
}
