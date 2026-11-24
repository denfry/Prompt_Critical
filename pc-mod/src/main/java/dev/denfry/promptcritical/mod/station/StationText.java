package dev.denfry.promptcritical.mod.station;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты станций и журнала (04d §4d.8–4d.9): ключи перевода и их en/ru значения для datagen. */
public final class StationText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".station.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        m("created", "Station %s founded: you are its owner. Roles: /pc station grant <player> <role>",
                "Основана станция %s, вы её владелец. Роли: /pc station grant <игрок> <роль>");
        m("denied", "E_ACCESS: station %s, your roles lack %s", "E_ACCESS: станция %s, у ваших ролей нет права %s");
        m("none_here", "No station in this chunk", "В этом чанке нет станции");
        m("info", "Station %s (%s), owner %s, %s chunks, founded at tick %s", "Станция %s (%s), владелец %s, чанков: %s, основана на тике %s");
        m("info.member", "  %s: %s", "  %s: %s");
        m("info.you", "Your roles: %s", "Ваши роли: %s");
        m("granted", "%s now holds %s at %s", "%s получил роль %s на станции %s");
        m("revoked", "%s lost %s at %s", "%s лишён роли %s на станции %s");
        m("revoked_all", "%s lost all roles at %s", "%s лишён всех ролей на станции %s");
        m("cannot_assign", "E_ACCESS: you cannot assign %s at %s", "E_ACCESS: вы не можете выдавать роль %s на станции %s");
        m("journal.head", "Journal of %s, last %s entries:", "Журнал станции %s, последние записи (%s):");
        m("journal.own", "Only your own actions are shown: you are not a member", "Показаны только ваши действия: вы не член станции");
        m("journal.empty", "No entries", "Записей нет");
        m("unknown", "No station named %s", "Нет станции %s");
        m("exported", "Exported %s entries to %s", "Выгружено записей: %s, файл %s");
        m("export_failed", "Export failed: %s", "Выгрузка не удалась: %s");
        m("claimed", "Chunk %s joined station %s", "Чанк %s присоединён к станции %s");
        m("claim_none", "No station of yours borders this chunk", "Рядом с этим чанком нет вашей станции");
        m("claim_taken", "This chunk already belongs to station %s", "Этот чанк уже принадлежит станции %s");
        m("computer.look", "Look at a computer, turtle or MES server within 5 blocks",
                "Смотрите на компьютер, черепаху или сервер MES в пределах 5 блоков");
        m("computer.owned", "E_ACCESS: this computer is recorded as %s's", "E_ACCESS: этот компьютер записан за игроком %s");
        m("computer.bound", "Computer %s now acts with your station rights", "Компьютер %s теперь действует с вашими правами на станции");
        m("announce", "[Station %s] level %s event: %s", "[Станция %s] событие уровня %s: %s");
    }

    private StationText() {
    }

    private static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }
}
