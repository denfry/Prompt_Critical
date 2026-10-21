package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.sim.grid.RelaySetting;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Модуль РЗА A1 (07 §7.5.2): вид задаёт предмет, уставки — компонент {@code relay_settings} (переносятся вместе с
 * модулем). Ставится в шкаф {@code relay_cabinet} у ячейки, а АЧР, 27, 49 — прямо в двигатель или потребителя.
 */
public class RelayModuleItem extends Item {
    private final RelaySetting.Kind kind;

    public RelayModuleItem(Properties props, RelaySetting.Kind kind) {
        super(props);
        this.kind = kind;
    }

    public RelaySetting.Kind kind() {
        return kind;
    }

    public static GridComponents.Settings settings(ItemStack stack) {
        GridComponents.Settings s = stack.get(GridComponents.RELAY_SETTINGS.get());
        return s != null ? s : stack.getItem() instanceof RelayModuleItem m ? GridComponents.Settings.of(m.kind) : new GridComponents.Settings(0, 0);
    }

    /** Модуль как уставка домена; {@code null} — не модуль. */
    public static RelaySetting setting(ItemStack stack) {
        return stack.getItem() instanceof RelayModuleItem m ? settings(stack).toSetting(m.kind) : null;
    }

    /** Шаг ручек уставок: a, b (0 — уставки нет). */
    public static double[] steps(RelaySetting.Kind kind) {
        return switch (kind) {
            case OVERCURRENT -> new double[] {10, 0.05};
            case SYNC_CHECK -> new double[] {0, 0};
            case REVERSE_POWER -> new double[] {0.01, 0.5};
            case UNDERFREQUENCY -> new double[] {0.1, 0.1};
            case UNDERVOLTAGE -> new double[] {0.05, 0.5};
            case THERMAL -> new double[] {5, 0};
            case UFLS -> new double[] {1, 0};
            case AUTORECLOSE -> new double[] {0.5, 1};
            case BREAKER_FAILURE -> new double[] {0.05, 0};
        };
    }

    /** Повернуть ручку уставки {@code which} (0 — a, 1 — b) на {@code dir} шагов; не ниже шага. */
    public static void adjust(ItemStack stack, int which, int dir) {
        if (!(stack.getItem() instanceof RelayModuleItem m)) return;
        double step = steps(m.kind)[which];
        if (step == 0) return;
        GridComponents.Settings s = settings(stack);
        double a = s.a(), b = s.b();
        if (which == 0) a = Math.max(step, Math.round((a + dir * step) / step) * step);
        else b = Math.max(step, Math.round((b + dir * step) / step) * step);
        if (m.kind == RelaySetting.Kind.UFLS) a = Math.min(7, a);
        stack.set(GridComponents.RELAY_SETTINGS.get(), new GridComponents.Settings(a, b));
    }

    /** Уставки строкой для щита и подсказки. */
    public static Component describe(ItemStack stack) {
        if (!(stack.getItem() instanceof RelayModuleItem m)) return Component.empty();
        GridComponents.Settings s = settings(stack);
        return GridPanels.relayText(m.kind, s.a(), s.b());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(describe(stack).copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(GridPanels.RELAY_GENERATION.get().withStyle(ChatFormatting.DARK_GRAY));
    }
}
