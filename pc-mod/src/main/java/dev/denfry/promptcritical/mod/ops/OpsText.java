package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.ops.CheckCondition;
import dev.denfry.promptcritical.sim.ops.CommissioningCheck;
import dev.denfry.promptcritical.sim.ops.CommissioningDefectKind;
import dev.denfry.promptcritical.sim.ops.CommissioningState;
import dev.denfry.promptcritical.sim.ops.MachineWear;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.PlainBearing;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты эксплуатации, пусконаладки и водоподготовки: ключи перевода и их en/ru значения для datagen. */
public final class OpsText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".ops.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        // обслуживание
        def("stopped", "The machine is stopped — nothing to hear", "Машина стоит — слушать нечего");
        sound(MachineWear.StethoscopeSound.HUM, "Stethoscope: an even hum", "Стетоскоп: ровный гул");
        sound(MachineWear.StethoscopeSound.RUSTLE, "Stethoscope: a rustle in the bearing", "Стетоскоп: шорох в подшипнике");
        sound(MachineWear.StethoscopeSound.SCRAPE, "Stethoscope: scraping in the bearing", "Стетоскоп: скрежет в подшипнике");
        sound(MachineWear.StethoscopeSound.KNOCK, "Stethoscope: the bearing knocks", "Стетоскоп: подшипник стучит");
        sound(MachineWear.StethoscopeSound.HISS, "Stethoscope: hissing at the gland", "Стетоскоп: шипение у сальника");
        feel(PlainBearing.HandFeel.WARM, "Bearing housing: warm", "Корпус подшипника: тёплый");
        feel(PlainBearing.HandFeel.HOT, "Bearing housing: hot", "Корпус подшипника: горячий");
        feel(PlainBearing.HandFeel.SCALDING, "Bearing housing: scalding — you burn your hand!", "Корпус подшипника: обжигает — ожог руки!");
        def("seized", "The bearing has seized — the shaft is locked until the shell is replaced",
                "Подшипник заплавился — вал заклинен до замены вкладыша");
        def("need_stop", "Stop the machine first", "Сначала остановите машину");
        def("unsafe", "Working on a running machine — the moving parts catch you!", "Работа на ходу — вас задело движущимися частями!");
        def("need_tallow", "The oil can is empty: carry tallow", "Маслёнка пуста: нужен технический жир (tallow)");
        def("no_fault", "Nothing to do here: %s", "Здесь нечего делать: %s");
        def("repaired", "Done: %s", "Выполнено: %s");
        def("working", "Working: %s…", "Работа: %s…");
        def("packing_tightened", "Gland tightened (%s of 3 times)", "Сальник подтянут (%s из 3 раз)");
        def("packing_spent", "The packing is spent: replace it with gland packing on a stopped machine",
                "Набивка выработана: замените её на остановленной машине");
        // котёл
        def("relief_lift", "Relief valve lifted at %s MPa, reseated at %s MPa — set to %s MPa",
                "ПК поднялся при %s МПа, сел при %s МПа — отрегулирован на %s МПа");
        def("relief_not_lifting", "Raise the steam pressure to the valve set point %s MPa (now %s) to test it",
                "Поднимите давление пара до уставки ПК %s МПа (сейчас %s), чтобы испытать его");
        def("instrument", "Pressure gauge zero offset %s%% of scale — corrected; gauge glass blown down",
                "Смещение нуля манометра %s%% шкалы — исправлено; водоуказательное стекло продуто");
        def("caulked", "Seam caulked", "Шов подчеканен");
        def("caulk_need", "Caulk the seam on an empty cold boiler (drain it first)",
                "Шов чеканят на пустом холодном котле (сначала спустите воду)");
        def("caulk_none", "The seams are tight — nothing to caulk", "Швы плотные — чеканить нечего");
        def("scrape_need", "Scale is removed from a stopped, cooled and drained boiler", "Накипь чистят на остановленном, остывшем и спущенном котле");
        def("psv_replaced", "Relief valve replaced: the unit needs the relief valve test again",
                "ПК заменён: установке снова нужна проверка ПК");
        def("psv_need", "Replace the relief valve only with no pressure in the boiler", "ПК меняют только без давления в котле");
        def("drain_open", "Boiler drain opened", "Дренаж котла открыт");
        def("drain_closed", "Boiler drain closed", "Дренаж котла закрыт");
        def("weeping", "The shell seam weeps under pressure", "Шов обечайки парит под давлением");
        // пусконаладка
        def("start_refused", "Start refused — commissioning checks not passed: %s", "Пуск запрещён — не пройдены проверки: %s");
        def("start_unaccepted", "Unit not accepted — started at your own risk (logged)", "Установка не принята — пуск под вашу ответственность (записано)");
        def("check_passed", "%s: passed", "%s: пройдена");
        def("check_failed", "%s: FAILED — %s", "%s: НЕ ПРОЙДЕНА — %s");
        def("check_refused", "%s: cannot start — %s", "%s: нельзя начать — %s");
        def("accepted", "Unit accepted, passport issued", "Установка принята, выдан паспорт");
        def("primed", "Pump primed: water came out of the air vent", "Насос залит: из воздушника пошла вода");
        def("prime_stop", "Stop the pump to prime it", "Насос заливают остановленным");
        def("rotation", "Bump test: rotation %s", "Толчок: вращение %s");
        def("rotation_forward", "forward", "прямое");
        def("rotation_reverse", "REVERSED — swap the Create drive direction", "ОБРАТНОЕ — смените направление привода Create");
        def("rotation_need", "Turn the shaft to bump-test the rotation", "Для толчка вал должен вращаться");
        def("brake", "Brake test: %s%% of rated braking torque — gap adjusted", "Испытание тормоза: %s%% расчётного момента — зазор отрегулирован");
        def("trial_started", "Trial run: %s s of %s, load rejection %s", "Пробный пуск: %s с из %s, сброс нагрузки %s");
        // гидроиспытание
        def("hydro", "Hydrotest: %s MPa (test %s MPa), held %s s of %s", "Гидроиспытание: %s МПа (пробное %s МПа), выдержка %s с из %s");
        def("hydro_no_unit", "Put the test pump against a boiler or a steam engine", "Опрессовочный насос ставят вплотную к котлу или машине");
        def("hydro_leak", "Water drips from the seam — the pressure falls", "Из шва капает вода — давление падает");
        // самописец
        def("chart", "Recording gauge: %s, %s; pen at %s of %s", "Самописец: %s, %s; перо на %s из %s");
        def("chart_no_paper", "Recording gauge: no chart paper (%s)", "Самописец: нет ленты (%s)");
        def("chart_no_unit", "Mount the recording gauge on a boiler, engine, fan or pump", "Самописец ставят на котёл, машину, вентилятор или насос");
        def("chart_view", "%s, %s… %s; %s points", "%s, %s… %s; точек %s");
        quantity("pressure", "boiler pressure, MPa", "давление в котле, МПа");
        quantity("level", "water level, %", "уровень воды, %");
        quantity("flue", "flue gas, °C", "уходящие газы, °C");
        quantity("speed", "speed, rpm", "обороты, об/мин");
        quantity("flow", "flow, m³/h", "подача, м³/ч");
        // водоподготовка
        def("water", "%s: in %s mB, out %s mB (%s)", "%s: вход %s мБ, выход %s мБ (%s)");
        def("clarifier_lime", "lime %s kg in the bin", "извести в бункере %s кг");
        def("filter_load", "filter loading %s%% — Shift-click to backwash", "загрузка фильтра %s%% — Shift + ПКМ: промывка");
        def("filter_backwash", "Filter backwashed", "Фильтр промыт");
        def("resin", "resin %s%% loaded — regenerate with salt (%s kg)", "смола загружена на %s%% — регенерация солью (%s кг)");
        def("regenerated", "Column regenerated: %s kg of salt", "Колонна отрегенерирована: соли %s кг");
        def("regen_need", "Regeneration needs %s kg of salt", "Для регенерации нужно соли %s кг");
    }

    private static final Map<CommissioningCheck, String[]> CHECKS = Map.of(
            CommissioningCheck.PRESSURE_TEST, new String[] {"Hydrotest", "Гидроиспытание"},
            CommissioningCheck.RELIEF_VALVE_SET, new String[] {"Relief valve", "Проверка ПК"},
            CommissioningCheck.INSTRUMENT_CHECK, new String[] {"Instruments", "Проверка КИП"},
            CommissioningCheck.ROTATION_CHECK, new String[] {"Rotation", "Вращение"},
            CommissioningCheck.PRIMING, new String[] {"Priming", "Заливка"},
            CommissioningCheck.BRAKE_TEST, new String[] {"Brake test", "Испытание тормоза"},
            CommissioningCheck.TRIAL_RUN, new String[] {"Trial run", "Пробный пуск"});
    private static final Map<CommissioningDefectKind, String[]> DEFECTS = Map.of(
            CommissioningDefectKind.SEAM_WEEP, new String[] {"weeping seam", "течь шва"},
            CommissioningDefectKind.CASTING_POROSITY, new String[] {"porous casting", "пористая отливка"},
            CommissioningDefectKind.PSV_OFFSET, new String[] {"relief valve set point off", "смещена уставка ПК"},
            CommissioningDefectKind.PSV_STUCK, new String[] {"relief valve stuck", "ПК прикипел"},
            CommissioningDefectKind.GAUGE_OFFSET, new String[] {"gauge zero offset", "смещён нуль манометра"},
            CommissioningDefectKind.GAUGE_GLASS_BLOCKED, new String[] {"gauge glass blocked", "засорено водоуказательное стекло"},
            CommissioningDefectKind.REVERSED_ROTATION, new String[] {"reversed rotation", "обратное вращение"},
            CommissioningDefectKind.DRY_PUMP, new String[] {"pump not primed", "насос не залит"},
            CommissioningDefectKind.BRAKE_MISADJUSTED, new String[] {"brake misadjusted", "тормоз не отрегулирован"},
            CommissioningDefectKind.DASHPOT_DRY, new String[] {"dashpot dry", "катаракт без масла"});
    private static final Map<CommissioningState, String[]> STATES = Map.of(
            CommissioningState.NEW, new String[] {"NOT ACCEPTED", "НЕ ПРИНЯТА"},
            CommissioningState.TESTING, new String[] {"TESTING", "ИСПЫТАНИЯ"},
            CommissioningState.ACCEPTED, new String[] {"ACCEPTED", "ПРИНЯТА"},
            CommissioningState.MODIFIED, new String[] {"MODIFIED", "ИЗМЕНЕНА"});
    private static final Map<CheckCondition, String[]> CONDITIONS = Map.ofEntries(
            Map.entry(CheckCondition.NOT_IN_CHECKLIST, new String[] {"not in the checklist", "нет в чек-листе"}),
            Map.entry(CheckCondition.FIRE_LIT, new String[] {"put the fire out", "погасите топку"}),
            Map.entry(CheckCondition.METAL_HOT, new String[] {"metal hotter than 60 °C", "металл горячее 60 °C"}),
            Map.entry(CheckCondition.PRESSURE_LOW, new String[] {"test pressure not reached", "пробное давление не набрано"}),
            Map.entry(CheckCondition.HOLD_SHORT, new String[] {"hold too short", "мала выдержка"}),
            Map.entry(CheckCondition.NO_CONTROL_GAUGE, new String[] {"no control gauge: carry a test pressure gauge", "нет контрольного манометра"}),
            Map.entry(CheckCondition.UNIT_RUNNING, new String[] {"the unit is running", "установка работает"}),
            Map.entry(CheckCondition.LOAD_LOW, new String[] {"load too low", "мала нагрузка"}),
            Map.entry(CheckCondition.PREREQUISITES, new String[] {"other checks first", "сначала прочие проверки"}),
            Map.entry(CheckCondition.RUN_SHORT, new String[] {"run too short", "мала наработка"}),
            Map.entry(CheckCondition.NO_LOAD_REJECTION, new String[] {"no load rejection", "не было сброса нагрузки"}));
    private static final Map<MaintenanceFaultKind, String[]> FAULTS = Map.of(
            MaintenanceFaultKind.BEARING_WEAR, new String[] {"BEARING", "ПОДШИПНИК"},
            MaintenanceFaultKind.LUBRICATION, new String[] {"LUBRICATION", "СМАЗКА"},
            MaintenanceFaultKind.HX_FOULING, new String[] {"SCALE", "НАКИПЬ"},
            MaintenanceFaultKind.PACKING_LEAK, new String[] {"GLAND", "САЛЬНИК"},
            MaintenanceFaultKind.IMPELLER_EROSION, new String[] {"IMPELLER", "РАБОЧЕЕ КОЛЕСО"},
            MaintenanceFaultKind.ROPE_WEAR, new String[] {"ROPE", "КАНАТ"});

    static {
        for (CommissioningCheck c : CommissioningCheck.values()) LANG.put(P + "check." + lower(c), CHECKS.getOrDefault(c, new String[] {c.name(), c.name()}));
        for (CommissioningDefectKind d : CommissioningDefectKind.values()) LANG.put(P + "defect." + lower(d), DEFECTS.get(d));
        for (CommissioningState s : CommissioningState.values()) LANG.put(P + "state." + lower(s), STATES.get(s));
        for (CheckCondition c : CheckCondition.values()) LANG.put(P + "condition." + lower(c), CONDITIONS.getOrDefault(c, new String[] {c.name(), c.name()}));
        for (MaintenanceFaultKind k : MaintenanceFaultKind.values()) LANG.put(P + "fault." + lower(k), FAULTS.getOrDefault(k, new String[] {k.name(), k.name()}));
    }

    private OpsText() {
    }

    private static void def(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static void sound(MachineWear.StethoscopeSound s, String en, String ru) {
        def("sound." + lower(s), en, ru);
    }

    private static void feel(PlainBearing.HandFeel f, String en, String ru) {
        def("feel." + lower(f), en, ru);
    }

    private static void quantity(String id, String en, String ru) {
        def("quantity." + id, en, ru);
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static MutableComponent of(Enum<?> e) {
        String kind = switch (e) {
            case CommissioningCheck c -> "check.";
            case CommissioningDefectKind d -> "defect.";
            case CommissioningState s -> "state.";
            case CheckCondition c -> "condition.";
            case MaintenanceFaultKind k -> "fault.";
            case MachineWear.StethoscopeSound s -> "sound.";
            case PlainBearing.HandFeel f -> "feel.";
            default -> "";
        };
        return Component.translatable(P + kind + lower(e));
    }

    /** Перечень через запятую. */
    public static MutableComponent list(Set<? extends Enum<?>> items) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Enum<?> e : items) {
            if (!first) out.append(", ");
            out.append(of(e));
            first = false;
        }
        return out;
    }

    public static String num(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
