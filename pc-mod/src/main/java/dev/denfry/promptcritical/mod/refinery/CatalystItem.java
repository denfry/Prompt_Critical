package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.sim.refining.CatalystState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Мешок катализатора с компонентом {@code catalyst_state} (09 §9.9.11): подсказка — активность, кокс, возраст, стадия. */
public final class CatalystItem extends Item {
    public CatalystItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CatalystState s = stack.get(RefineryComponents.CATALYST_STATE.get());
        if (s != null) tooltip.add(ConversionPanels.catalystTip(s).withStyle(ChatFormatting.GRAY));
    }
}
