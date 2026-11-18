package dev.denfry.promptcritical.mod.chlor;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения хлорного производства (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class ChlorText {
    private static final String P = "message.promptcritical.chlor.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("cells.on", "Cell series: on load", "Серия ванн: под нагрузкой");
        put("cells.off", "Cell series: switched off", "Серия ванн: отключена");
        put("cells.rebuilt", "Diaphragms rebuilt: fresh asbestos on every cathode", "Диафрагмы переосаждены: свежий асбест на всех катодах");
        put("cells.blast", "HYDROGEN-CHLORINE EXPLOSION in the cell gas spaces: anolyte level fell below the diaphragms. Chlorine released",
                "ВЗРЫВ ХЛОРО-ВОДОРОДНОЙ СМЕСИ в газовых пространствах ванн: анолит ушёл ниже диафрагм. Выброс хлора");
        put("rectifier.on", "Rectifier: breaker closed", "Выпрямитель: выключатель включён");
        put("rectifier.off", "Rectifier: breaker open", "Выпрямитель: выключатель отключён");
        put("liquefier.on", "Chlorine liquefaction: compressor and refrigeration on", "Сжижение хлора: компрессор и холод пущены");
        put("liquefier.off", "Chlorine liquefaction stopped", "Сжижение хлора остановлено");
        put("edc.on", "EDC unit on", "Установка ДХЭ пущена");
        put("edc.off", "EDC unit stopped", "Установка ДХЭ остановлена");
        put("cracker.on", "EDC cracking furnace lit", "Печь пиролиза ДХЭ разожжена");
        put("cracker.off", "EDC cracking furnace out", "Печь пиролиза ДХЭ погашена");
        put("cracker.decoked", "EDC furnace coil decoked with steam and air", "Змеевик печи ДХЭ выжжен паровоздушной смесью");
        put("pvc.auto_on", "PVC autoclave: automatic batch cycle on", "Автоклав ПВХ: автоматический цикл включён");
        put("pvc.auto_off", "PVC autoclave: automatic cycle off", "Автоклав ПВХ: автоматический цикл отключён");
        put("cloud.smell", "A sharp, bleach-like smell: chlorine in the air", "Резкий запах хлорки: в воздухе хлор");
        put("cloud.idlh", "Chlorine above 10 ppm: eyes and throat burn. Get out across the wind and uphill",
                "Хлора больше 10 ppm: режет глаза и горло. Уходите поперёк ветра и на возвышенность");
        put("cloud.aegl3", "Chlorine above 50 ppm: choking, lungs are being damaged", "Хлора больше 50 ppm: удушье, поражение лёгких");
    }

    private ChlorText() {
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
}
