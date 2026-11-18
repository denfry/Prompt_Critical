package dev.denfry.promptcritical.mod.ops;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Предмет LOTO (13 §13.1.2): личный замок, бирка «Не включать! Работают люди», многозамковая накладка, указатель
 * напряжения. Применяется ПКМ по точке изоляции ({@link Lockouts}). Замок личный по тому, кто его повесил: снять его
 * может только он сам, поэтому ключ у замка не отдельный предмет.
 */
public final class LockoutItem extends Item {
    public enum Kind { PADLOCK, TAG, HASP, VOLTAGE_TESTER }

    private final Kind kind;

    public LockoutItem(Properties p, Kind kind) {
        super(p);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(LockoutText.msg("tip." + kind.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
    }
}
