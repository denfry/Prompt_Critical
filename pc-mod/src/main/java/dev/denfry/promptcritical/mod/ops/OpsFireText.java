package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты пожарной сигнализации и тушения (13 §13.5): ключи перевода и их en/ru значения для datagen. */
public final class OpsFireText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".fire.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        m("fire", "FIRE ALARM: the fire panel at %s confirmed a fire", "ПОЖАР: ППК у %s подтвердил пожар");
        m("asphyxiant", "Extinguishing gas released: leave the room, the air will not keep you alive",
                "Выпущен огнетушащий газ: покиньте помещение, этим воздухом дышать нельзя");
        m("still_burning", "It is still burning nearby: put the fire out first", "Рядом ещё горит: сначала потушите");
        m("bulb_replaced", "Sprinkler bulb replaced", "Колба спринклера заменена");
        m("detector_reset", "Detector reset", "Извещатель сброшен");
        m("deluge_open", "Deluge valve open by the fire panel", "Дренчер открыт по сигналу ППК");
        m("deluge_closed", "Deluge closed: it opens on a fire signal from the fire panel", "Дренчер закрыт: откроется по сигналу пожара от ППК");
        m("monitor_on", "Fire monitor on: a water jet along its face", "Лафетный ствол открыт: струя воды по фасаду");
        m("monitor_off", "Fire monitor off", "Лафетный ствол закрыт");
        m("recharged", "Gas module recharged", "Модуль газового тушения перезаряжен");
        m("gas_blocked", "Gas release blocked by hand (lock it for the works)", "Выпуск газа заблокирован вручную (заприте на время работ)");
        m("gas_armed", "Gas release armed: on a fire it goes off 30 s after the siren",
                "Выпуск газа взведён: при пожаре сработает через 30 с после сирены");
        m("panel_reset", "Fire panel reset", "ППК сброшен");
        m("water_on_metal", "Water on burning metal: hydrogen flashes, the fire is not out — use metal-fire powder",
                "Вода на горящий металл: вспышка водорода, огонь не погас — нужен порошок для металлов");
        m("water_on_live", "Water on live equipment: electric shock — de-energize first or use powder or CO₂",
                "Вода на электроустановку под напряжением: удар током — сначала снимите напряжение или тушите порошком, CO₂");
        m("ineffective", "This extinguisher does not put out this fire", "Этим огнетушителем такой пожар не потушить");
        m("tip.water", "Class A: wood, coal, cables. Not on metals or live equipment", "Класс A: дерево, уголь, кабели. Не на металлы и не на электроустановки");
        m("tip.foam", "Classes A and B: solids and burning liquids. Not on live equipment", "Классы A и B: твёрдые и горящие жидкости. Не на электроустановки");
        m("tip.powder_abce", "Classes A, B, C, E: the universal one, also on live equipment", "Классы A, B, C, E: универсальный, и на электроустановки");
        m("tip.co2", "Classes B and E: liquids and live equipment; useless on smouldering solids",
                "Классы B и E: жидкости и электроустановки; тлеющие твёрдые не тушит");
        m("tip.powder_d", "Class D: burning metals — sodium, magnesium, zirconium", "Класс D: горящие металлы — натрий, магний, цирконий");
    }

    private OpsFireText() {
    }

    private static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }
}
