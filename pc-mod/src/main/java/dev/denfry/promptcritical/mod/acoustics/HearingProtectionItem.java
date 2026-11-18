package dev.denfry.promptcritical.mod.acoustics;

import dev.denfry.promptcritical.sim.acoustics.NoiseExposure;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Противошумные наушники (13 §13.7, {@code hearing_protection}): слот головы, снижают уровень у уха на
 * {@value NoiseExposure#PROTECTOR_DB} дБ для дозы и оглушения ({@link NoiseService}); громкость клиента не трогают.
 */
public final class HearingProtectionItem extends Item implements Equipable {
    public HearingProtectionItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical.hearing_protection.hint", (int) NoiseExposure.PROTECTOR_DB)
                .withStyle(ChatFormatting.GRAY));
    }
}
