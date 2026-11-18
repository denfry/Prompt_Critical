package dev.denfry.promptcritical.mod.nonferrous;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Тексты цветной металлургии (08 §8.8): имена блоков, предметов и жидкостей и сообщения (en_us, ru_ru); ключи
 * собирает {@code PcLanguageProvider} одной строкой.
 */
public final class NonferrousText {
    private static final String P = "message.promptcritical.nonferrous.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        // Байер
        block("bayer_digester", "Bayer Digester (bauxite leaching)", "Автоклав выщелачивания боксита");
        block("alumina_precipitator", "Alumina Precipitator", "Декомпозёр алюминатного раствора");
        block("alumina_calciner", "Rotary Calciner (hydrate to alumina)", "Прокалочная печь (гидроксид в глинозём)");
        item("alumina", "Alumina (smelter grade)", "Глинозём металлургический");
        item("aluminium_hydroxide", "Aluminium Hydroxide", "Гидроксид алюминия");
        fluid("sodium_aluminate_liquor", "Sodium Aluminate Liquor", "Алюминатный раствор");
        fluid("red_mud", "Red Mud (bauxite residue)", "Красный шлам");
        say("on", "%s: started", "%s: пущено");
        say("off", "%s: stopped", "%s: остановлено");
    }

    private NonferrousText() {
    }

    private static void block(String id, String en, String ru) {
        LANG.put("block.promptcritical." + id, new String[] {en, ru});
    }

    private static void item(String id, String en, String ru) {
        LANG.put("item.promptcritical." + id, new String[] {en, ru});
    }

    private static void fluid(String id, String en, String ru) {
        LANG.put("fluid_type.promptcritical." + id, new String[] {en, ru});
    }

    private static void say(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
