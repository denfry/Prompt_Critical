package dev.denfry.promptcritical.mod.market;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения биржи (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class MarketText {
    private static final String P = "message.promptcritical.market.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("balance", "Account %s CR · sold today %s CR", "Счёт %s CR · продано за сутки %s CR");
    }

    private MarketText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    /** Центы → «1234.56». */
    public static String cr(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }
}
