package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты водородной станции: ключи перевода и их en/ru значения для datagen. */
public final class HydrogenText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".hydrogen.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        msg("electrolyser.on", "Electrolyser started", "Электролизёр пущен");
        msg("electrolyser.off", "Electrolyser stopped", "Электролизёр остановлен");
        msg("electrolyser.purge_first", "Tripped: purge the stack with nitrogen first", "Отключён защитой: сначала продуйте батарею азотом");
        msg("electrolyser.purge", "Nitrogen purge started, two minutes", "Продувка азотом пошла, две минуты");
        msg("compressor.on", "Compressor started", "Компрессор пущен");
        msg("compressor.off", "Compressor stopped", "Компрессор остановлен");
        msg("fuel_cell.on", "Fuel cell module started", "Модуль топливных элементов пущен");
        msg("fuel_cell.off", "Fuel cell module stopped", "Модуль топливных элементов остановлен");
        msg("disc.pressurised", "Vent the vessel before you change the burst disc", "Сбросьте давление, прежде чем менять мембрану");
        msg("disc.replaced", "New burst disc fitted", "Новая мембрана поставлена");
    }

    private HydrogenText() {
    }

    private static void msg(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }
}
