package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.sim.radiation.ContaminationLimits;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Радиометр загрязнённости поверхностей с плоским α/β-зондом ({@link ContaminationService#PROBE}): ПКМ по блоку — его
 * верхняя грань против нормы для помещений постоянного пребывания; ПКМ в воздух — свои руки (норма для кожи) и обувь
 * (норма для спецобуви), а если во второй руке предмет — он (норма для оборудования). Показания — скорость счёта и
 * пересчёт в Бк/см² по ISO 7503-1, ниже предела обнаружения — «&lt;МДА».
 */
public final class ContaminationMeterItem extends Item {
    public ContaminationMeterItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        p.displayClientMessage(ContaminationText.reading("block", ContaminationService.surface(level, ctx.getClickedPos()),
                ContaminationLimits.Surface.PERMANENT_ROOM, ContaminationService.PROBE), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.success(self);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!other.isEmpty()) {
            p.displayClientMessage(ContaminationText.reading("item", ContaminationData.get(other), ContaminationLimits.Surface.PERMANENT_ROOM,
                    ContaminationService.PROBE), false);
        } else {
            p.displayClientMessage(ContaminationText.reading("hands", ContaminationService.hands(p), ContaminationLimits.Surface.SKIN,
                    ContaminationService.PROBE), false);
            p.displayClientMessage(ContaminationText.reading("shoes", ContaminationService.shoes(p), ContaminationLimits.Surface.OVERALLS,
                    ContaminationService.PROBE), false);
        }
        return InteractionResultHolder.success(self);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical.contamination_meter.tip").withStyle(ChatFormatting.GRAY));
    }
}
