package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.mining.HoistDuty;
import dev.denfry.promptcritical.sim.mining.HoistState;
import dev.denfry.promptcritical.sim.mining.RopeConstruction;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты подъёма и водоотлива: ключи перевода и их ru/en значения для datagen. */
public final class HoistText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    /** Причины, по которым подъём не собран (код панели). */
    public enum Problem { NONE, NO_HEADFRAME, NOT_FORMED, NO_CAGE, NO_SHAFT, NO_ROPE }

    static {
        msg("hoist.title", "Mine hoist", "Шахтный подъём");
        msg("hoist.landing.top", "Top landing", "Верхняя посадка");
        msg("hoist.landing.level", "Level −%s m", "Горизонт −%s м");
        msg("hoist.landing.bottom", "Shaft bottom −%s m", "Зумпф −%s м");
        msg("hoist.stop", "Stop", "Стоп");
        msg("hoist.reset", "Reset trip", "Сброс защиты");
        msg("hoist.speed", "Speed limit %s m/s", "Предел скорости %s м/с");
        msg("hoist.position", "Cage −%s m, %s m/s", "Клеть −%s м, %s м/с");
        msg("hoist.load", "Load %s kg (%s), rope SF %s (norm %s)", "Груз %s кг (%s), запас каната %s (норма %s)");
        msg("hoist.power", "Shaft %s kW of %s kW available", "Вал %s кВт из %s кВт доступных");
        msg("hoist.rope", "Rope ⌀%s mm", "Канат ⌀%s мм");
        msg("hoist.rope_wear", "ROPE_WEAR: replace the rope", "ROPE_WEAR: канат под замену");
        msg("hoist.going", "Going to: %s", "Идёт на: %s");
        problem(Problem.NO_HEADFRAME, "No headframe in front of or behind the hoist (within 32 blocks)",
                "Нет копра перед машиной или за ней (до 32 блоков)");
        problem(Problem.NOT_FORMED, "Headframe is not complete: 2–4 segments", "Копёр не собран: 2–4 сегмента");
        problem(Problem.NO_CAGE, "No cage in the shaft under the headframe", "В стволе под копром нет клети");
        problem(Problem.NO_SHAFT, "The cage does not fit under the headframe: the shaft must be free",
                "Клеть не помещается под копром: ствол должен быть свободен");
        problem(Problem.NO_ROPE, "No rope: use wire rope on the hoist", "Нет каната: повесьте канат на машину");
        for (HoistState s : HoistState.values()) stateText(s);
        duty(HoistDuty.PEOPLE, "people", "людской");
        duty(HoistDuty.MIXED, "mixed", "грузо-людской");
        duty(HoistDuty.CARGO, "cargo", "грузовой");
        msg("hoist.door_open", "Close the cage door first", "Сначала закройте дверь клети");
        msg("hoist.forbidden", "Rope safety factor %s below the norm %s for %s duty — refused",
                "Запас прочности каната %s ниже нормы %s для режима «%s» — отказ");
        msg("hoist.unsafe", "Rope safety factor below the norm: UNSAFE_OPERATION logged",
                "Запас прочности каната ниже нормы: записано UNSAFE_OPERATION");
        msg("hoist.blocked", "The hoist cannot start: %s", "Подъём не пускается: %s");
        msg("hoist.here", "The cage is already at this landing", "Клеть уже на этой посадке");
        msg("hoist.rope_need", "The rope needs %s m: %s coils", "Канату нужно %s м: %s бухт");
        msg("hoist.rope_busy", "Stop the cage before changing the rope", "Остановите клеть перед заменой каната");
        msg("hoist.rope_installed", "Rope hung: ⌀%s mm, %s m", "Канат навешен: ⌀%s мм, %s м");
        msg("wire_rope.spec", "⌀%s mm, %s MPa, %s", "⌀%s мм, %s МПа, %s");
        msg("wire_rope.breaking", "Breaking force %s kN", "Разрывное усилие %s кН");
        construction(RopeConstruction.R6X19_FC, "6×19 fibre core", "6×19 ОС");
        construction(RopeConstruction.R6X19_IWRC, "6×19 steel core", "6×19 МС");
        construction(RopeConstruction.R6X36_IWRC, "6×36 steel core", "6×36 МС");
        construction(RopeConstruction.R18X7_NONROTATING, "18×7 non-rotating", "18×7 малокрутящийся");
        msg("drainage", "Drainage: sump %s of %s m³ (%s%%), inflow %s m³/h, pumped %s m³/h%s",
                "Водоотлив: в водосборнике %s из %s м³ (%s%%), приток %s м³/ч, откачка %s м³/ч%s");
        msg("drainage.flooded", ", flooded %s m³", ", затоплено %s м³");
        msg("drainage.acid", "; mine water is acidic", "; вода кислая");
        msg("drainage.small_sump", "Sump holds less than 4 h of inflow", "Водосборник меньше 4 ч притока");
        msg("pump", "Drainage pump: %s m³/h at %s m head, %s kW", "Насос водоотлива: %s м³/ч на %s м, %s кВт");
    }

    private HoistText() {
    }

    private static void msg(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static void problem(Problem p, String en, String ru) {
        msg("hoist.problem." + p.name().toLowerCase(Locale.ROOT), en, ru);
    }

    private static void stateText(HoistState s) {
        String[] t = switch (s) {
            case HELD -> new String[] {"on brake", "на тормозе"};
            case RUNNING -> new String[] {"running", "в ходу"};
            case TRIP_OVERSPEED -> new String[] {"TRIP: overspeed", "ЗАЩИТА: превышение скорости"};
            case TRIP_OVERWIND -> new String[] {"TRIP: overwind", "ЗАЩИТА: переподъём"};
            case TRIP_POWER_LOSS -> new String[] {"TRIP: power loss", "ЗАЩИТА: потеря питания"};
            case ROPE_BROKEN_CAUGHT -> new String[] {"ROPE BROKEN: cage on safety catches", "ОБРЫВ КАНАТА: клеть на парашютах"};
            case ROPE_BROKEN_FELL -> new String[] {"ROPE BROKEN: cage fell", "ОБРЫВ КАНАТА: клеть упала"};
        };
        msg("hoist.state." + s.name().toLowerCase(Locale.ROOT), t[0], t[1]);
    }

    private static void duty(HoistDuty d, String en, String ru) {
        msg("hoist.duty." + d.name().toLowerCase(Locale.ROOT), en, ru);
    }

    private static void construction(RopeConstruction c, String en, String ru) {
        msg("rope." + c.name().toLowerCase(Locale.ROOT), en, ru);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static MutableComponent problem(Problem p) {
        return msg("hoist.problem." + p.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent state(HoistState s) {
        return msg("hoist.state." + s.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent duty(HoistDuty d) {
        return msg("hoist.duty." + d.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent construction(RopeConstruction c) {
        return msg("rope." + c.name().toLowerCase(Locale.ROOT));
    }

    /** Название посадки по глубине. */
    public static MutableComponent landing(int[] depths, int i) {
        if (i == 0) return msg("hoist.landing.top");
        return msg(i == depths.length - 1 ? "hoist.landing.bottom" : "hoist.landing.level", depths[i]);
    }

    /** Число для сообщения: целое или с одним знаком. */
    public static String num(double v) {
        return Math.abs(v) >= 100 || v == Math.rint(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
