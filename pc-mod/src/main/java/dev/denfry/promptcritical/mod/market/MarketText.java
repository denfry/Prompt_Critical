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
        put("period_report", "Week closed: %s t CO2e emitted, %s t allowances held · fine %s CR, pollution fees %s CR, contracts %s CR",
                "Неделя закрыта: выброс %s т CO2-экв., квот на счёте %s т · штраф %s CR, платы за загрязнение %s CR, договоры %s CR");
        put("contract_signed", "Supply contract: %s units a week of %s at %s CR, deposit %s CR",
                "Договор поставки: %s ед. в неделю (%s) по %s CR, депозит %s CR");
        put("contract_refused", "No contract: put goods in the slot, keep the 20 %% deposit on the account, one contract per product",
                "Договор не подписан: положите товар в слот, держите на счёте депозит 20 %%, один договор на товар");
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
