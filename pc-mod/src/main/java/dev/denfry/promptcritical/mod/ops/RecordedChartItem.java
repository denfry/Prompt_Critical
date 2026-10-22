package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.sim.ops.ChartDrum;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Снятая лента самописца {@code recorded_chart} (13 §13.6.7): ПКМ — развернуть ленту на экране. */
public final class RecordedChartItem extends Item {
    public RecordedChartItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        OpsComponents.ChartTrace t = stack.get(OpsComponents.CHART_TRACE.get());
        if (t == null) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) dev.denfry.promptcritical.mod.ops.client.ChartScreen.open(t);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        OpsComponents.ChartTrace t = stack.get(OpsComponents.CHART_TRACE.get());
        if (t == null) return;
        long n = t.points().stream().filter(p -> p != ChartDrum.EMPTY).count();
        lines.add(OpsText.msg("chart_view", OpsText.msg("quantity." + t.quantity()), OpsText.num(t.min(), 0),
                OpsText.num(t.max(), 0), n).withStyle(ChatFormatting.GRAY));
    }
}
