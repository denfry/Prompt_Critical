package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.DosimeterItem;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Тексты радиационной части (en_us, ru_ru) и подсказка у любого предмета с активностью: активность, остаточное тепло
 * и мощность дозы на 1 м без защиты — по времени мира у клиента.
 */
public final class RadiationBlocksText {
    private static final String P = "message.promptcritical.radiation_blocks.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("rack.clad", "SPENT FUEL RACK WITHOUT WATER: cladding is overheating and failing. Flood the rack",
                "СТЕЛЛАЖ БЕЗ ВОДЫ: оболочки перегреваются и разрушаются. Залейте стеллаж водой");
        put("activity", "Activity %s GBq · decay heat %s W · %s at 1 m", "Активность %s ГБк · остаточное тепло %s Вт · %s на 1 м");
    }

    private RadiationBlocksText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> {
            ActivityData a = e.getItemStack().get(ActivityData.ACTIVITY.get());
            if (a == null || e.getEntity() == null) return;
            var inv = a.inventory(e.getEntity().level().getGameTime());
            e.getToolTip().add(msg("activity", String.format(Locale.ROOT, "%.3g", inv.totalBq() / 1e9),
                    String.format(Locale.ROOT, "%.3g", inv.decayHeatW()), DosimeterItem.rate(inv.doseRateSvH(1))).withStyle(ChatFormatting.GOLD));
        });
    }
}
