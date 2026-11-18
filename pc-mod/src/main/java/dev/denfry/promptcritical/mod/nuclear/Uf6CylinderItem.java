package dev.denfry.promptcritical.mod.nuclear;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Баллон UF₆ по ANSI N14.1 (у нас — ГОСТ-аналоги): 48Y — сырьё и отвал, до 12 501 кг UF₆ не выше 4,5 % U-235; 30B —
 * продукт НОУ, до 2277 кг не выше 5 %; 5A — малый, до 24,95 кг любого обогащения. Пределы обогащения — по ядерной
 * безопасности: в баллоне большего объёма больше урана, и критическое обогащение для него ниже. Содержимое —
 * компонент {@code uf6}; баллон не складывается в стопку.
 */
public final class Uf6CylinderItem extends Item {
    public enum Type {
        Y48(12_501, 0.045), B30(2_277, 0.05), A5(24.95, 1.0);

        public final double maxKg, maxU235;

        Type(double maxKg, double maxU235) {
            this.maxKg = maxKg;
            this.maxU235 = maxU235;
        }
    }

    private final Type type;

    public Uf6CylinderItem(Properties props, Type type) {
        super(props.stacksTo(1));
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public static Uf6Charge charge(ItemStack stack) {
        Uf6Charge c = stack.get(Uf6Components.UF6.get());
        return c == null ? Uf6Charge.EMPTY : c;
    }

    /** Сколько ещё UF₆ влезет в баллон, кг (0 — не баллон). */
    public static double roomKg(ItemStack stack) {
        if (!(stack.getItem() instanceof Uf6CylinderItem c)) return 0;
        return Math.max(0, c.type.maxKg - charge(stack).uf6Kg());
    }

    /** Можно ли долить этот состав, не нарушив предел обогащения баллона. */
    public static boolean accepts(ItemStack stack, double u235) {
        return stack.getItem() instanceof Uf6CylinderItem c && u235 <= c.type.maxU235 + 1e-12;
    }

    /**
     * Долить {@code kg} UF₆ с долями {@code u235}, {@code u234}: массы складываются, доли усредняются по молям урана.
     * Возвращает тот же стек. Не баллон, перелив или обогащение выше предела типа — {@link IllegalArgumentException}.
     */
    public static ItemStack fill(ItemStack stack, double kg, double u235, double u234) {
        return fill(stack, kg, u235, u234, 0);
    }

    /** То же для регенерированного урана с U-236. */
    public static ItemStack fill(ItemStack stack, double kg, double u235, double u234, double u236) {
        if (!(stack.getItem() instanceof Uf6CylinderItem c)) throw new IllegalArgumentException("not a UF6 cylinder: " + stack);
        if (!(kg >= 0)) throw new IllegalArgumentException("negative UF6 mass: " + kg);
        if (kg == 0) return stack;
        Uf6Charge sum = charge(stack).plus(new Uf6Charge(kg, u235, u234, u236));
        if (sum.uf6Kg() > c.type.maxKg + 1e-6)
            throw new IllegalArgumentException("UF6 cylinder " + c.type + " overfilled: " + sum.uf6Kg() + " kg > " + c.type.maxKg);
        if (sum.u235() > c.type.maxU235 + 1e-12)
            throw new IllegalArgumentException("UF6 cylinder " + c.type + " enrichment limit: " + sum.u235() + " > " + c.type.maxU235);
        stack.set(Uf6Components.UF6.get(), sum);
        return stack;
    }

    /** Отобрать до {@code kg} UF₆ из баллона; возвращает отобранное. */
    public static Uf6Charge take(ItemStack stack, double kg) {
        Uf6Charge c = charge(stack);
        Uf6Charge part = c.part(kg);
        Uf6Charge left = c.minus(part.uf6Kg());
        if (left.empty()) stack.remove(Uf6Components.UF6.get());
        else stack.set(Uf6Components.UF6.get(), left);
        return part;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Uf6Charge c = charge(stack);
        if (c.empty()) {
            tooltip.add(NuclearText.msg("cylinder.empty", NuclearText.num(type.maxKg, 0)).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(NuclearText.msg("cylinder.charge", NuclearText.num(c.uf6Kg(), 1), NuclearText.num(c.u235() * 100, 3))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(NuclearText.msg("cylinder.u234", NuclearText.num(c.u234() * 100, 4), NuclearText.num(c.uraniumKg(), 1))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(NuclearText.msg("cylinder.limit", NuclearText.num(type.maxKg, 0), NuclearText.num(type.maxU235 * 100, 1))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
