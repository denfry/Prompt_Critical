package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты аварий нефтегаза, разливов и трубопроводов (09 §9.8, §9.11, 14 §14.5): ключи перевода с en/ru для datagen. */
public final class HazardText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".hazard.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        m("vce", "Vapour cloud explosion: %s kg of gas in the congested area, class %s",
                "Взрыв газовоздушного облака: %s кг газа в загромождённой зоне, класс %s");
        m("flash_fire", "Flash fire: the gas cloud burnt back to the leak", "Пожар-вспышка: облако газа выгорело до места утечки");
        m("jet_fire", "Jet fire at the leak: keep away and cool the vessels around it",
                "Струйный пожар на месте утечки: не подходите, охлаждайте соседние аппараты");
        m("bleve", "BLEVE: the vessel failed, fireball %s m across", "BLEVE: сосуд разрушился, огненный шар диаметром %s м");
        m("bleve_safe", "The vessel would have failed now (BLEVE); this server is creative-safe, the contents were lost instead",
                "Сейчас сосуд разрушился бы (BLEVE); на этом сервере режим creative_safe — содержимое просто потеряно");
        m("heat", "Radiant heat %s kW/m²: get out of sight of the fire", "Тепловое излучение %s кВт/м²: уйдите из прямой видимости огня");
        m("bullet", "LPG bullet: %s t propane, wall %s °C, %s bar%s", "Ёмкость СУГ: пропана %s т, стенка %s °C, %s бар%s");
        m("bullet.relief", ", relief valve lifting", ", сбрасывает предохранительный клапан");
        m("spill.land", "Oil spill: %s m³ on land", "Разлив нефти: %s м³ на земле");
        m("spill.water", "Oil spill on water: %s m³. It spreads: boom it off and skim it", "Разлив нефти на воде: %s м³. Пятно растекается: огородите бонами и соберите");
        m("sorbent", "Sorbent took %s kg of oil", "Сорбент собрал %s кг нефти");
        m("shore_cleaned", "Shore cleaned with a sorbent pad", "Берег очищен сорбентом");
        m("skimmer", "Skimmer: %s, film %s mm, efficiency %s %%, recovered %s L",
                "Нефтесборщик: %s, плёнка %s мм, эффективность %s %%, собрано %s л");
        m("skimmer.on", "running", "в работе");
        m("skimmer.off", "no shaft rotation", "нет вращения вала");
        m("skimmer.dry", "no slick within reach", "рядом нет плёнки");
    }

    private HazardText() {
    }

    static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }
}
