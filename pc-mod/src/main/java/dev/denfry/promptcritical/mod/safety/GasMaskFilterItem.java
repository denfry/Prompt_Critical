package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.ops.FilterType;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Сменный фильтр противогаза {@code gas_mask_filter} (13 §13.7): тип — компонент {@code filter_type}. */
public final class GasMaskFilterItem extends Item {
    public GasMaskFilterItem(Properties props) {
        super(props.stacksTo(16));
    }

    /** Фильтр стека; без компонента — новый фильтр B (так он лежит во вкладке творческого режима). */
    public static SafetyComponents.Filter filter(ItemStack stack) {
        SafetyComponents.Filter f = stack.get(SafetyComponents.FILTER_TYPE.get());
        return f == null ? new SafetyComponents.Filter(FilterType.B, 0) : f;
    }

    public static ItemStack of(FilterType type) {
        return of(new SafetyComponents.Filter(type, 0));
    }

    public static ItemStack of(SafetyComponents.Filter f) {
        ItemStack s = new ItemStack(PcItems.GAS_MASK_FILTER.get());
        s.set(SafetyComponents.FILTER_TYPE.get(), f);
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.promptcritical.gas_mask_filter.typed", filter(stack).type().name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SafetyComponents.Filter f = filter(stack);
        tooltip.add(Component.translatable("item.promptcritical.gas_mask_filter.stops",
                Component.translatable("contaminant.promptcritical." + f.type().stops.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
        if (f.loaded() > 0)
            tooltip.add(Component.translatable("item.promptcritical.gas_mask.filter", f.type().name(),
                    Math.round(Math.min(1, f.loaded() / f.type().capacity()) * 100)).withStyle(ChatFormatting.AQUA));
    }
}
