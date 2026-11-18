package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.sim.waste.Repository.Verdict;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения обращения с РАО (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class WasteText {
    private static final String P = "message.promptcritical.waste.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("verdict.accepted", "Package accepted: %s", "Упаковка принята: %s");
        put("verdict.non_compliant", "Rejected: the waste form does not meet the standard (weak cement or overloaded bitumen)",
                "Не принято: компаунд не соответствует нормам (слабый цемент или пересоленный битум)");
        put("verdict.wrong_class", "Rejected: this waste class is not allowed here (%s, class %s)",
                "Не принято: этот класс отходов сюда нельзя (%s, класс %s)");
        put("verdict.dose_rate", "Rejected: dose rate at the package surface %s is above 2 mSv/h",
                "Не принято: мощность дозы у поверхности упаковки %s выше 2 мЗв/ч");
        put("verdict.too_hot", "Rejected: the package gives off %s W, more than the bentonite buffer can take (1700 W)",
                "Не принято: упаковка выделяет %s Вт — больше, чем выдержит бентонитовый буфер (1700 Вт)");
        put("verdict.needs_overpack", "Rejected: glass goes into deep disposal only inside a copper canister",
                "Не принято: стекло идёт в глубинное захоронение только в медном контейнере");
        put("verdict.full", "Rejected: the disposal vault is full", "Не принято: отсек захоронения заполнен");
        put("verdict.empty", "Not a waste package", "Это не упаковка РАО");
        put("cat.vllw", "VLLW", "ОНАО");
        put("cat.llw", "LLW", "НАО");
        put("cat.ilw", "ILW", "САО");
        put("cat.hlw", "HLW", "ВАО");
        put("package", "%s · class %s · %s · %s", "%s · класс %s · %s · %s");
        put("compliant", "meets the standard", "соответствует нормам");
        put("non_compliant", "DOES NOT meet the standard", "НЕ соответствует нормам");
    }

    private WasteText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static MutableComponent verdict(Verdict v, Object... args) {
        return msg("verdict." + v.name().toLowerCase(Locale.ROOT), args);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
