package dev.denfry.promptcritical.mod.paperwork;

import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Рукописный документ: оперативный журнал {@code shift_log} или наряд-допуск {@code work_permit}. ПКМ в воздух —
 * прочитать; Shift + ПКМ журналом — сдать смену с подписью. Записи и графы наряда заполняются кликом по машине
 * ({@link Paperwork}).
 */
public final class DocumentItem extends Item {
    public DocumentItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            if (player.isSecondaryUseActive() && stack.is(PcItems.SHIFT_LOG.get())) Paperwork.handOver(sp, stack);
            else Paperwork.show(sp, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        if (stack.is(PcItems.SHIFT_LOG.get())) {
            ShiftLog j = stack.getOrDefault(Paperwork.SHIFT_LOG.get(), ShiftLog.EMPTY);
            out.add(Component.translatable(PaperworkText.LOG_TOOLTIP, j.entries().size(), j.currentShiftEntries())
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        PermitRecord r = stack.get(Paperwork.WORK_PERMIT.get());
        if (r == null) {
            out.add(Component.translatable(PaperworkText.PERMIT_BLANK).withStyle(ChatFormatting.GRAY));
            return;
        }
        out.add(Component.translatable(PaperworkText.PERMIT_TITLE, r.number()).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable(PaperworkText.PERMIT_APPARATUS, r.apparatus(),
                r.pos().getX() + " " + r.pos().getY() + " " + r.pos().getZ()).withStyle(ChatFormatting.GRAY));
        out.add(Paperwork.stage(r.stage()).copy().withStyle(r.stage() == dev.denfry.promptcritical.sim.grid.WorkPermit.Stage.CLOSED
                ? ChatFormatting.DARK_GRAY : ChatFormatting.RED));
    }

    @Override
    public Component getName(ItemStack stack) {
        PermitRecord r = stack.get(Paperwork.WORK_PERMIT.get());
        return r == null ? super.getName(stack) : Component.translatable(PaperworkText.PERMIT_TITLE, r.number());
    }
}
