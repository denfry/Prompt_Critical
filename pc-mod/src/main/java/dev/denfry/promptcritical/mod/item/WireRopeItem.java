package dev.denfry.promptcritical.mod.item;

import dev.denfry.promptcritical.mod.mine.HoistText;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Стальной канат {@code wire_rope} (08 §8.2.8): бухта 10 м, конструкция — компонент {@code rope_state}. */
public final class WireRopeItem extends Item {
    public WireRopeItem(Properties props) {
        super(props);
    }

    public static RopeStateData rope(ItemStack stack) {
        RopeStateData d = stack.get(PcDataComponents.ROPE_STATE.get());
        return d == null ? RopeStateData.DEFAULT : d;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RopeStateData d = rope(stack);
        tooltip.add(HoistText.msg("wire_rope.spec", d.diameterMm(), d.gradeMpa(), HoistText.construction(d.constructionOrDefault()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(HoistText.msg("wire_rope.breaking", HoistText.num(d.fresh().breakingForceN() / 1000)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
