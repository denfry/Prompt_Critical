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
        // резервуарный парк и трубопроводы 09 §9.8
        m("gauge", "Tank level %s %%, evaporation %s kg/h, %s", "Уровень %s %%, испарение %s кг/ч, %s");
        m("gauge.floating", "floating roof", "плавающая крыша");
        m("gauge.fixed", "fixed roof", "стационарная крыша");
        m("roof_plates", "A floating roof takes %s steel plates", "На плавающую крышу нужно %s стальных листов");
        m("roof_fitted", "Floating roof fitted: evaporation losses down by 94 %", "Плавающая крыша смонтирована: потери от испарения меньше на 94 %");
        m("pig.stuck", "The pig is stuck %s m down the line: vent the section and cut it out",
                "Скребок застрял в %s м от камеры: стравите давление и вырежьте участок");
        m("pig.cleaned", "Cleaning pig arrived: %s kg of wax pushed into the receiver", "Очистной скребок пришёл: в камеру приёма вытеснено %s кг парафина");
        m("pig.report", "Smart pig arrived: %s defects over 10 %% of the wall, the deepest %s %%",
                "Дефектоскоп пришёл: дефектов глубже 10 %% стенки — %s, самый глубокий — %s %%");
        m("pig.door_open", "Close the trap door first", "Сначала закройте дверцу камеры");
        m("pig.vented", "The trap is vented: pressurise it before launching", "Камера стравлена: поднимите давление перед пуском");
        m("pig.empty", "No pig in the trap", "В камере нет скребка");
        m("pig.no_receiver", "No receiver at the end of the Create pipes from this trap", "На конце труб Create от этой камеры нет камеры приёма");
        m("pig.busy", "A pig is already in the trap or the line", "Скребок уже в камере или в трубе");
        m("pig.door_closed", "Open the trap door to load a pig", "Чтобы заложить скребок, откройте дверцу");
        m("pig.door_pressure", "PIPE_TRAP_DOOR_PRESSURE: the trap is under pressure, the door is locked",
                "PIPE_TRAP_DOOR_PRESSURE: камера под давлением, дверца заблокирована");
        m("pig.vented_first", "Vent the section before cutting it out", "Перед вырезкой стравите давление");
        m("pig.receiver_empty", "The receiver is empty", "Камера приёма пуста");
        m("anode", "Sacrificial anodes on the section: %s kg of magnesium", "Протекторы на участке: %s кг магния");
        m("rectifier", "CP rectifier: %s A shared between %s sections", "Станция катодной защиты: %s А на %s участков");
        m("test_post", "Pipe-to-soil potential: on %s V, off %s V (CSE): %s", "Потенциал труба — земля: вкл %s В, выкл %s В (МСЭ): %s");
        m("test_post.ok", "protected", "защищена");
        m("test_post.low", "not protected, it corrodes", "не защищена, корродирует");
        m("test_post.over", "overprotected: the coating disbonds", "перезащита: отслаивается изоляция");
        m("test_post.none", "No pipeline section nearby", "Рядом нет участка трубопровода");
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
