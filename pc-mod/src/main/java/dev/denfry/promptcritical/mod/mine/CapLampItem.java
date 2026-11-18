package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.item.CapLampData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.sim.battery.CapLamp;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Головной шахтный светильник ({@link CapLamp}): носится в слоте головы и горит, пока надет; батарея садится по
 * игровому времени ({@link CapLampService#SECONDS_PER_TICK}), заряжается на стеллаже ламповой. Полоска под иконкой —
 * степень заряда.
 */
public class CapLampItem extends Item implements Equipable {
    public CapLampItem(Properties props) {
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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel sl) || (level.getGameTime() + entity.getId()) % CapLampService.UPDATE_TICKS != 0) return;
        boolean worn = entity instanceof LivingEntity le && le.getItemBySlot(EquipmentSlot.HEAD) == stack;
        CapLampService.update(stack, sl, worn);
    }

    static CapLampData data(ItemStack stack) {
        return stack.getOrDefault(PcDataComponents.CAP_LAMP.get(), CapLampData.FRESH);
    }

    /** Степень заряда по компоненту (на клиенте — по последней синхронизации). */
    public static double soc(ItemStack stack) {
        return data(stack).toLamp().battery().soc();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * (float) soc(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb((float) soc(stack) / 3f, 1, 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        CapLampData d = data(stack);
        CapLamp l = d.toLamp();
        tip.add(Component.translatable("item.promptcritical.cap_lamp.charge", pct(l.battery().soc()),
                String.format(Locale.ROOT, "%.1f", l.battery().capacityAh()), String.format(Locale.ROOT, "%.0f", l.battery().cycles()))
                .withStyle(ChatFormatting.GRAY));
        if (d.glassBroken()) tip.add(Component.translatable("item.promptcritical.cap_lamp.broken").withStyle(ChatFormatting.RED));
        else tip.add(Component.translatable("item.promptcritical.cap_lamp.proof").withStyle(ChatFormatting.DARK_GREEN));
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f", 100 * v);
    }
}
