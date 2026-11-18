package dev.denfry.promptcritical.mod.safety;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Страховочный фал {@code lifeline} (13 §13.7): ПКМ по опоре или треноге {@code rescue_tripod} — закрепить; с
 * надетой привязью {@code safety_harness} падение в пределах {@value dev.denfry.promptcritical.sim.ops.Ppe#LIFELINE_M} м
 * от опоры безвредно, а наблюдающий вытягивает пострадавшего из замкнутого пространства треногой.
 */
public final class LifelineItem extends Item {
    public LifelineItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        stack.set(SafetyComponents.LIFELINE_ANCHOR.get(), GlobalPos.of(level.dimension(), ctx.getClickedPos().immutable()));
        level.playSound(null, ctx.getClickedPos(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
        if (!level.isClientSide && ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(SafetyText.msg("lifeline_clipped", level.getBlockState(ctx.getClickedPos()).getBlock().getName()), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !stack.has(SafetyComponents.LIFELINE_ANCHOR.get())) return InteractionResultHolder.pass(stack);
        stack.remove(SafetyComponents.LIFELINE_ANCHOR.get());
        if (!level.isClientSide) player.displayClientMessage(SafetyText.msg("lifeline_unclipped"), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical.lifeline.hint").withStyle(ChatFormatting.GRAY));
        GlobalPos a = stack.get(SafetyComponents.LIFELINE_ANCHOR.get());
        if (a != null) tooltip.add(Component.translatable("item.promptcritical.lifeline.anchor", a.pos().toShortString()).withStyle(ChatFormatting.AQUA));
    }
}
