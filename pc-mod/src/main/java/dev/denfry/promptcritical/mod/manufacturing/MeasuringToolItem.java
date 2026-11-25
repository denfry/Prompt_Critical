package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.sim.manufacturing.PartInspection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Измерительный инструмент цеха (18 §18.6.10): штангенциркуль, микрометр, твердомер. ПКМ с деталью во второй руке —
 * замер; результат идёт в паспорт детали, инструмент изнашивается на единицу.
 */
public final class MeasuringToolItem extends Item {
    private final PartInspection.Tool tool;

    public MeasuringToolItem(PartInspection.Tool tool, Properties props) {
        super(props);
        this.tool = tool;
    }

    public PartInspection.Tool tool() {
        return tool;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(self);
        if (level.isClientSide) return InteractionResultHolder.success(self);
        PartInspector.Result r = PartInspector.measure(player.getOffhandItem(), tool);
        if (r.message() != null) player.displayClientMessage(r.message(), false);
        boolean measured = r.outcome() == PartInspector.Outcome.MEASURED || r.outcome() == PartInspector.Outcome.INSPECTED;
        if (measured) self.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        return measured ? InteractionResultHolder.success(self) : InteractionResultHolder.fail(self);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(ManufacturingText.msg("inspect.tool_hint." + PartInspector.key(tool)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
