package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Map;

/** Тексты стволового сигнального аппарата {@link ShaftSignalBlock}: ключ → {английский, русский} для datagen. */
public final class ShaftSignalText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".shaft_signal.";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        msg("unlinked", "No hoist serves this landing: put the signal box next to the cage at a landing of a working shaft",
                "Посадку не обслуживает ни один подъём: повесьте аппарат у клети на посадке собранного ствола");
        msg("started", "Signal given: the cage is called to landing %s", "Сигнал подан: клеть вызвана на посадку %s");
        msg("already_here", "The cage is already at landing %s", "Клеть уже на посадке %s");
        msg("door_open", "The cage door is open: the hoist will not move", "Дверь клети открыта: подъём не тронется");
        msg("no_cage", "No cage in the shaft", "В стволе нет клети");
        msg("blocked", "The hoist cannot start: see its panel", "Подъём не пускается: смотрите щит машины");
        msg("forbidden", "Refused: rope safety factor or work permit", "Отказ: запас каната или допуск к работе");
        msg("bad_landing", "Landing %s does not exist", "Посадки %s нет");
    }

    private ShaftSignalText() {
    }

    private static void msg(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }
}
