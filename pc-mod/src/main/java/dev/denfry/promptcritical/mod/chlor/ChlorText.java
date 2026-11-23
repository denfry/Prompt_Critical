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
        put("hcl.off", "HCl furnace: burner out", "Печь HCl: горелка погашена");
        put("hcl.acid", "HCl furnace: burning, hydrogen chloride absorbed into hydrochloric acid of %s %%",
                "Печь HCl: горит, хлороводород поглощается в соляную кислоту %s %%");
        put("hcl.dry", "HCl furnace: burning, hydrogen chloride cooled and dried, out as dry gas",
                "Печь HCl: горит, хлороводород охлаждается и сушится, выходит сухим газом");
        put("hcl.excess", "HCl furnace: hydrogen excess %s %%", "Печь HCl: избыток водорода %s %%");
        put("hcl.slip", "HCl furnace: hydrogen excess %s %% is below 5 %%: unburnt chlorine goes out with the tail gas",
                "Печь HCl: избыток водорода %s %% меньше 5 %%: несгоревший хлор уходит с хвостовым газом");
        put("pvc.auto_on", "PVC autoclave: automatic batch cycle on", "Автоклав ПВХ: автоматический цикл включён");
        put("pvc.auto_off", "PVC autoclave: automatic cycle off", "Автоклав ПВХ: автоматический цикл отключён");
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
