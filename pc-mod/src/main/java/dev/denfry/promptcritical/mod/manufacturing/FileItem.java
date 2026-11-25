package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.sim.manufacturing.HandFiling;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Слесарный напильник (18 §18.7, §18.15 п. 4): ПКМ с кованой деталью во второй руке доводит одну деталь до IT13 —
 * так получают первые валы до токарного станка.
 */
public final class FileItem extends Item {
    public FileItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(self);
        if (level.isClientSide) return InteractionResultHolder.success(self);
        Component refusal = PartInspector.onOne(player, part -> {
            PartSpec spec = part.get(ManufacturingComponents.PART_SPEC.get());
            if (spec == null) return ManufacturingText.msg("file.no_part");
            if (spec.hardnessHrc() > HandFiling.MAX_WORK_HRC) return ManufacturingText.msg("file.too_hard");
            if (!HandFiling.canFile(spec)) return ManufacturingText.msg("file.already_fine");
            part.set(ManufacturingComponents.PART_SPEC.get(), HandFiling.file(spec));
            part.remove(ManufacturingComponents.INSPECTION_CHECKS.get());
            return null;
        });
        if (refusal != null) {
            player.displayClientMessage(refusal, true);
            return InteractionResultHolder.fail(self);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
        player.displayClientMessage(ManufacturingText.msg("file.done", HandFiling.FILED_IT), true);
        self.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        return InteractionResultHolder.success(self);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(ManufacturingText.msg("file.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
