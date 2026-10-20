package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.sim.mining.VeinCatalog;
import dev.denfry.promptcritical.sim.mining.VeinType;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ключи и тексты раздела 11 (en, ru) для генератора локализации. */
public final class ContentText {
    public static final String MAP_BLANK = "promptcritical.geological_map.blank";
    public static final String MAP_UNREAD = "promptcritical.geological_map.unread";
    public static final String MAP_TARGET = "promptcritical.geological_map.target";
    public static final String MAP_READING = "promptcritical.geological_map.reading";
    public static final String BEARING = "promptcritical.bearing.";
    public static final String LECTERN_EMPTY = "promptcritical.document_lectern.empty";
    public static final String LECTERN_ONLY_DOCUMENTS = "promptcritical.document_lectern.only_documents";
    public static final String HANDBOOK_LOCKED = "promptcritical.handbook.locked";
    public static final String HANDBOOK_NEXT = "promptcritical.handbook.next";
    public static final String HANDBOOK_NO_RECIPE = "promptcritical.handbook.no_recipe";
    public static final String HANDBOOK_LAYER = "promptcritical.handbook.layer";
    public static final String HANDBOOK_ALL_LAYERS = "promptcritical.handbook.all_layers";
    public static final String HANDBOOK_DOCUMENT = "promptcritical.handbook.document";
    public static final String HANDBOOK_WHERE = "promptcritical.handbook.where";
    public static final String HANDBOOK_PONDER_SHOW = "promptcritical.handbook.ponder_show";
    public static final String HANDBOOK_PONDER_NONE = "promptcritical.handbook.ponder_none";
    public static final String HANDBOOK_TAB_HANDBOOK = "promptcritical.handbook.tab_handbook";
    public static final String HANDBOOK_TAB_ROUTE = "promptcritical.handbook.tab_route";
    public static final String ROUTE_DONE = "promptcritical.route.done";
    public static final String ROUTE_STATUS_DONE = "promptcritical.route.status_done";
    public static final String ROUTE_STATUS_OPEN = "promptcritical.route.status_open";
    public static final String ROUTE_STATUS_LOCKED = "promptcritical.route.status_locked";
    public static final String ROUTE_REQUIRES = "promptcritical.route.requires";
    public static final String ROUTE_UNLOCKS = "promptcritical.route.unlocks";
    public static final String ROUTE_OPTIONS = "promptcritical.route.options";
    public static final String ROUTE_HOW = "promptcritical.route.how";
    public static final String ROUTE_GOAL_COUNT = "promptcritical.route.goal_count";
    public static final String ROUTE_GOAL_HOLD = "promptcritical.route.goal_hold";
    public static final String ROUTE_REWARD = "promptcritical.route.reward";
    public static final String ROUTE_CHAPTER = "promptcritical.route.chapter";
    public static final String ROUTE_EMPTY = "promptcritical.route.empty";
    public static final String COAL_ASSAY = "promptcritical.assay.coal";
    public static final String ORE_GRADE = "promptcritical.assay.ore_grade";
    public static final String ORE_BYPRODUCT = "promptcritical.assay.ore_byproduct";

    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        LANG.put(MAP_BLANK, new String[] {"No ore bodies within the survey area", "В районе съёмки рудных тел нет"});
        LANG.put(MAP_UNREAD, new String[] {"Unfold it: marks the nearest ore body", "Разверните: отметит ближайшее рудное тело"});
        LANG.put(MAP_TARGET, new String[] {"%s near X %s, Z %s", "%s у X %s, Z %s"});
        LANG.put(MAP_READING, new String[] {"%s: %s m to the %s, centre at Y %s", "%s: %s м на %s, центр на отметке Y %s"});
        String[][] bearings = {{"n", "north", "север"}, {"ne", "north-east", "северо-восток"}, {"e", "east", "восток"},
                {"se", "south-east", "юго-восток"}, {"s", "south", "юг"}, {"sw", "south-west", "юго-запад"},
                {"w", "west", "запад"}, {"nw", "north-west", "северо-запад"}};
        for (String[] b : bearings) LANG.put(BEARING + b[0], new String[] {b[1], b[2]});
        String[][] veins = {{"banded_iron", "Banded iron formation", "Железистые кварциты"},
                {"coal_seam", "Coal seams", "Угольные пласты"},
                {"cu_ni_sulfide", "Copper-nickel sulphide ore", "Медно-никелевые руды"},
                {"porphyry_copper", "Porphyry copper", "Медно-порфировые руды"},
                {"polymetallic", "Polymetallic ore", "Полиметаллические руды"},
                {"bauxite", "Bauxite", "Бокситы"},
                {"quartz_greisen", "Tin-bearing greisen", "Оловоносные грейзены"},
                {"chromite_ultramafic", "Chromite in ultramafic rock", "Хромиты ультраосновных пород"}};
        for (String[] v : veins) LANG.put(vein(v[0]), new String[] {v[1], v[2]});
        for (VeinType t : VeinCatalog.M2) LANG.putIfAbsent(vein(t.id()), new String[] {t.id(), t.id()});
        LANG.put(LECTERN_EMPTY, new String[] {"Put an operating procedure on the lectern", "Положите на пюпитр инструкцию по эксплуатации"});
        LANG.put(LECTERN_ONLY_DOCUMENTS, new String[] {"Only documents go on the lectern", "На пюпитр кладут только документы"});
        LANG.put(HANDBOOK_LOCKED, new String[] {"Not yet: %s", "Пока закрыто: %s"});
        LANG.put(HANDBOOK_NEXT, new String[] {"What next", "Что делать дальше"});
        LANG.put(HANDBOOK_NO_RECIPE, new String[] {"Recipe %s is missing", "Рецепта %s нет"});
        LANG.put(HANDBOOK_LAYER, new String[] {"Layer %s of %s", "Слой %s из %s"});
        LANG.put(HANDBOOK_ALL_LAYERS, new String[] {"All layers", "Все слои"});
        LANG.put(COAL_ASSAY, new String[] {"Assay: volatiles %s %% daf, ash %s %%, moisture %s %%, sulphur %s %%",
                "Анализ: летучие %s %% (daf), зола %s %%, влага %s %%, сера %s %%"});
        LANG.put(ORE_GRADE, new String[] {"Grade class G%s, %s", "Класс содержания G%s, %s"});
        LANG.put(ORE_BYPRODUCT, new String[] {"By-product %s: about %s ppm", "Попутно %s: около %s ppm"});
        LANG.put(HANDBOOK_DOCUMENT, new String[] {"Operating procedure", "Инструкция по эксплуатации"});
        LANG.put(HANDBOOK_WHERE, new String[] {"where:", "где:"});
        LANG.put(HANDBOOK_PONDER_SHOW, new String[] {"Show (Ponder)", "Показать (Ponder)"});
        LANG.put(HANDBOOK_PONDER_NONE, new String[] {"No Ponder scenes for this item yet", "Сцен Ponder для этого предмета пока нет"});
        LANG.put(HANDBOOK_TAB_HANDBOOK, new String[] {"Handbook", "Справочник"});
        LANG.put(HANDBOOK_TAB_ROUTE, new String[] {"Route", "Маршрут"});
        LANG.put(ROUTE_DONE, new String[] {"Route: \"%s\" done (+%s XP)", "Маршрут: «%s» выполнено (+%s опыта)"});
        LANG.put(ROUTE_STATUS_DONE, new String[] {"Done", "Выполнено"});
        LANG.put(ROUTE_STATUS_OPEN, new String[] {"Open", "Открыто"});
        LANG.put(ROUTE_STATUS_LOCKED, new String[] {"Earlier steps first", "Сначала предыдущие шаги"});
        LANG.put(ROUTE_REQUIRES, new String[] {"After:", "После:"});
        LANG.put(ROUTE_UNLOCKS, new String[] {"Leads to:", "Ведёт к:"});
        LANG.put(ROUTE_OPTIONS, new String[] {"Any of:", "Любое из:"});
        LANG.put(ROUTE_HOW, new String[] {"How:", "Как:"});
        LANG.put(ROUTE_GOAL_COUNT, new String[] {"%s × %s", "%s × %s"});
        LANG.put(ROUTE_GOAL_HOLD, new String[] {"Held %s of %s min", "Держится %s из %s мин"});
        LANG.put(ROUTE_REWARD, new String[] {"Reward: %s XP", "Награда: %s опыта"});
        LANG.put(ROUTE_CHAPTER, new String[] {"Chapter", "Глава"});
        LANG.put(ROUTE_EMPTY, new String[] {"No route tasks", "Заданий маршрута нет"});
    }

    private ContentText() {
    }

    public static String vein(String id) {
        return "promptcritical.vein." + id;
    }
}
