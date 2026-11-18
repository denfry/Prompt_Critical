package dev.denfry.promptcritical.mod.radiation;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Баллон снимаемого дезактивирующего покрытия (латекс на поливиниловом спирте): ПКМ по блоку — покрыть верхнюю грань.
 * Плёнка сохнет {@link ContaminationService#CURE_TICKS} тиков, затем Shift + ПКМ пустой рукой — содрать её вместе с
 * рыхлым загрязнением ({@link dev.denfry.promptcritical.sim.radiation.DeconMethod#STRIPPABLE_COATING}). Жидких отходов
 * нет — только плёнка, твёрдые отходы.
 */
public final class StrippableCoatingItem extends Item {
    public StrippableCoatingItem(Properties props) {
        super(props.stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) return InteractionResult.SUCCESS;
        ContaminationMap m = ContaminationMap.of(level);
        if (m.coating(ctx.getClickedPos()) >= 0) {
            ctx.getPlayer().displayClientMessage(ContaminationText.msg("coat.already"), true);
            return InteractionResult.FAIL;
        }
        m.coat(ctx.getClickedPos(), level.getGameTime() + ContaminationService.CURE_TICKS);
        if (!ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        level.playSound(null, ctx.getClickedPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f);
        ctx.getPlayer().displayClientMessage(ContaminationText.msg("coat.applied",
                String.format(Locale.ROOT, "%.0f", ContaminationService.CURE_TICKS * 3.6 / 3600)), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical.strippable_coating.tip").withStyle(ChatFormatting.GRAY));
    }
}
