package dev.denfry.promptcritical.mod.electronics;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Подсказки предметов электроники (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class ElectronicsText {
    private static final String P = "message.promptcritical.electronics.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("chip", "%s µm process · %s transistors · %s MHz · die %s mm²", "Техпроцесс %s мкм · транзисторов %s · %s МГц · кристалл %s мм²");
        put("ingot_mono", "Single crystal, p-type (boron): %s → %s Ω·cm head to tail · %s %% in tolerance",
                "Монокристалл p-типа (бор): %s → %s Ом·см от головы к хвосту · в допуске %s %%");
        put("ingot_poly", "Polycrystal: the growth left the window. Remelt it in the puller",
                "Поликристалл: рост вышел из окна. Переплавьте в установке Чохральского");
    }

    private ElectronicsText() {
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

    private static String f(double v, int d) {
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    public static void registerTooltips() {
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> {
            var s = e.getItemStack();
            var chip = s.get(ElectronicsComponents.CHIP.get());
            if (chip != null)
                e.getToolTip().add(msg("chip", f(chip.cdUm(), 2), chip.transistors(), f(chip.clockMhz(), 1), f(chip.dieMm2(), 1))
                        .withStyle(ChatFormatting.GRAY));
            var ingot = s.get(ElectronicsComponents.INGOT.get());
            if (ingot != null)
                e.getToolTip().add(ingot.monocrystal()
                        ? msg("ingot_mono", f(ingot.rhoHead(), 1), f(ingot.rhoTail(), 1), Math.round(ingot.usable() * 100)).withStyle(ChatFormatting.GRAY)
                        : msg("ingot_poly").withStyle(ChatFormatting.RED));
        });
    }
}
