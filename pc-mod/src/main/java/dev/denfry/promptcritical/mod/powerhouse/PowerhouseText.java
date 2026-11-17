package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.combustion.BmsResponse;
import dev.denfry.promptcritical.sim.combustion.BmsState;
import dev.denfry.promptcritical.sim.plant.WaterTubeUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты котла I1 и деаэратора: ключи перевода и их en/ru значения для datagen. */
public final class PowerhouseText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".powerhouse.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        msg("problem.no_wall", "the panel must touch a furnace wall from outside", "щит должен касаться стены топки снаружи");
        msg("problem.unloaded", "part of the boiler is in an unloaded chunk", "часть котла в незагруженном чанке");
        msg("problem.too_big", "the furnace is larger than 9×16×9", "топка больше 9×16×9");
        msg("problem.too_small", "the furnace must be at least 5×8×5 (width × height × depth)", "топка — не меньше 5×8×5 (ширина × высота × глубина)");
        msg("problem.furnace_blocked", "the inside of the furnace must be empty", "внутри топки должно быть пусто");
        msg("problem.wall_gap", "the furnace walls, floor and roof must be solid water walls", "стены, под и потолок топки — сплошные экраны");
        msg("problem.burner_place", "burners go into the side walls, not on edges", "горелки — в боковых стенах, не на рёбрах");
        msg("problem.burner_count", "a boiler needs 1–8 burners", "у котла 1–8 горелок");
        msg("problem.no_drum", "a steam drum must sit on the furnace roof", "на потолке топки нужен барабан");
        for (BmsState s : BmsState.values()) {
            String[] n = switch (s) {
                case OFF -> new String[] {"off", "отключён"};
                case PURGE -> new String[] {"purging", "продувка"};
                case PURGE_COMPLETE -> new String[] {"purged, light the igniter", "продуто, зажгите запальник"};
                case IGNITER_TRIAL -> new String[] {"igniter trial", "проба запальника"};
                case IGNITER_ON -> new String[] {"igniter on, open main fuel", "запальник горит, подайте топливо"};
                case MAIN_TRIAL -> new String[] {"main flame trial", "проба основного факела"};
                case RUNNING -> new String[] {"running", "в работе"};
                case MFT -> new String[] {"master fuel trip", "отсечка топлива"};
                case POST_PURGE -> new String[] {"post-purge", "пост-продувка"};
            };
            msg("bms." + s.name().toLowerCase(Locale.ROOT), n[0], n[1]);
        }
        for (WaterTubeUnit.Protection p : WaterTubeUnit.Protection.values()) {
            String[] n = switch (p) {
                case DRUM_LEVEL_LOW_LOW -> new String[] {"drum level low-low", "уровень в барабане аварийно низкий"};
                case DRUM_LEVEL_HIGH_HIGH -> new String[] {"drum level high-high", "уровень в барабане аварийно высокий"};
                case STEAM_PRESSURE_HIGH -> new String[] {"steam pressure high", "давление пара выше допустимого"};
                case FURNACE_PRESSURE_HIGH -> new String[] {"furnace pressure high", "давление в топке высокое"};
                case FURNACE_PRESSURE_LOW -> new String[] {"furnace vacuum too deep", "разрежение в топке слишком глубокое"};
                case FUEL_PRESSURE_LOW -> new String[] {"fuel pressure low (no fuel)", "давление топлива низкое (нет топлива)"};
                case FLUE_CO_HIGH -> new String[] {"CO in the flue gas", "CO в дымовых газах"};
                case TUBE_LEAK -> new String[] {"screen tube rupture", "разрыв трубы экрана"};
            };
            msg("trip." + p.name().toLowerCase(Locale.ROOT), n[0], n[1]);
        }
        for (BmsResponse r : BmsResponse.values()) {
            String[] n = switch (r) {
                case ACCEPTED -> new String[] {"accepted", "принято"};
                case INTERLOCK_FANS -> new String[] {"FD and ID fans are not running", "дутьевой вентилятор и дымосос не в работе"};
                case INTERLOCK_FUEL_VALVES -> new String[] {"fuel valves are not shut", "клапаны топлива не закрыты"};
                case INTERLOCK_PURGE -> new String[] {"no completed purge", "нет завершённой продувки"};
                case INTERLOCK_STATE -> new String[] {"not allowed now", "сейчас не допускается"};
                case INTERLOCK_BURNER -> new String[] {"no such burner", "нет такой горелки"};
            };
            msg("response." + r.name().toLowerCase(Locale.ROOT), n[0], n[1]);
        }
        msg("interlock", "E_INTERLOCK: %s", "E_INTERLOCK: %s");
        msg("bypass_on", "Flame detectors bypassed with the key", "Датчики пламени зашунтированы ключом");
        msg("bypass_off", "Flame detector bypass removed", "Байпас датчиков пламени снят");
        msg("wrong_fuel", "This fuel does not suit the burners now: stop the boiler to change fuel",
                "Это топливо не подходит горелкам сейчас: остановите котёл, чтобы сменить топливо");
        msg("status", "Boiler: %s · drum %s MPa · fuel %s · feed water %s L",
                "Котёл: %s · барабан %s МПа · топливо %s · питательной воды %s л");
        msg("unformed", "Water-tube boiler not formed: %s", "Водотрубный котёл не собран: %s");
        msg("deaerator", "Deaerator: %s · steam %s kg/h · in %s L, out %s L", "Деаэратор: %s · пар %s кг/ч · вход %s л, выход %s л");
        msg("deaerator.no_boiler", "no water-tube boiler within 12 blocks", "нет водотрубного котла в 12 блоках");
        msg("deaerator.cold", "boiler drum below 0.3 MPa — no heating steam", "в барабане меньше 0,3 МПа — нет греющего пара");
        msg("deaerator.working", "working", "работает");
        msg("deaerator.idle", "idle", "простаивает");
        msg("fan", "%s: %s kg/s at %s rpm · %s", "%s: %s кг/с при %s об/мин · %s");
        msg("fan.linked", "serving a boiler", "работает на котёл");
        msg("fan.unlinked", "no water-tube boiler within 12 blocks", "нет водотрубного котла в 12 блоках");
        msg("turbine.gear", "Turbine reduction gear: %s rpm", "Редуктор турбины: %s об/мин");
        msg("dam_gate.open", "Intake gate raised: water goes to the penstock", "Затвор поднят: вода идёт в водовод");
        msg("dam_gate.closed", "Intake gate lowered: the turbine closes its wicket gates", "Затвор опущен: турбина закрывает направляющий аппарат");
        msg("hydro.no_gate", "No intake: put a dam gate in water at least 3 blocks above the turbine, within 48 blocks",
                "Нет водозабора: поставьте затвор в воду не ниже 3 блоков над турбиной, в пределах 48 блоков");
        msg("hydro.burst", "The penstock burst from water hammer: Shift-right-click to replace it",
                "Водовод порвало гидроударом: Shift и правый клик — заменить");
        msg("hydro.repaired", "Penstock replaced", "Водовод заменён");
        msg("hydro.state", "Head %s m · flow %s of %s m³/s · %s of %s kW · %s rpm · Tw %s s · %s",
                "Напор %s м · расход %s из %s м³/с · %s из %s кВт · %s об/мин · Tw %s с · %s");
        msg("hydro.kind.kaplan", "Kaplan runner (low head)", "поворотно-лопастная (низкий напор)");
        msg("hydro.kind.francis", "Francis runner", "радиально-осевая (Френсис)");
        msg("hydro.kind.pelton", "Pelton wheel (high head)", "ковшовая (Пельтон, высокий напор)");
        msg("gt.no_fuel", "No fuel gas in the turbine: pipe in mine methane or coke-oven gas",
                "В турбине нет газа: подведите трубой шахтный метан или коксовый газ");
        msg("gt.start", "Start: starter on · %s %% speed, %s of %s kW, air %s °C, exhaust %s °C",
                "Пуск: пусковой двигатель · %s %% оборотов, %s из %s кВт, воздух %s °C, выхлоп %s °C");
        msg("gt.stop", "Stop: fuel shut · %s %% speed, %s of %s kW, air %s °C, exhaust %s °C",
                "Останов: топливо отсечено · %s %% оборотов, %s из %s кВт, воздух %s °C, выхлоп %s °C");
        msg("gt.reset", "Gas turbine overspeed trip reset", "Автомат безопасности ГТУ взведён");
        msg("gearbox.no_turbine", "The gearbox must touch the gas turbine with its back face",
                "Редуктор должен касаться газовой турбины задней гранью");
        msg("gearbox.linked", "Gearbox on the gas turbine: %s rpm", "Редуктор на газовой турбине: %s об/мин");
        msg("turbine.reset", "Overspeed trip reset", "Автомат безопасности взведён");
        msg("turbine.not_tripped", "The overspeed trip is set (reset only below rated speed)",
                "Автомат безопасности не сработал (взводится ниже номинальных оборотов)");
        msg("turbine.no_boiler", "The turbine touches no boiler: put it against a drum, water wall or boiler panel",
                "Турбина не касается котла: поставьте её к барабану, экрану или щиту котла");
        msg("turbine.valve_open", "Stop valve opened · %s rpm, %s kW, steam %s kg/s, exhaust %s kPa",
                "Стопорный клапан открыт · %s об/мин, %s кВт, пар %s кг/с, выхлоп %s кПа");
        msg("turbine.valve_closed", "Stop valve closed · %s rpm, %s kW, steam %s kg/s, exhaust %s kPa",
                "Стопорный клапан закрыт · %s об/мин, %s кВт, пар %s кг/с, выхлоп %s кПа");
    }

    private PowerhouseText() {
    }

    private static void msg(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static MutableComponent problem(String key) {
        return msg("problem." + key);
    }

    public static MutableComponent bmsState(BmsState s) {
        return msg("bms." + s.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent protection(WaterTubeUnit.Protection p) {
        return msg("trip." + p.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent response(BmsResponse r) {
        return msg("response." + r.name().toLowerCase(Locale.ROOT));
    }

    public static String num(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
