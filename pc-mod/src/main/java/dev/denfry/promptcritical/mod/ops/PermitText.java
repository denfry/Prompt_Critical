package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.ops.GasLimits;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import dev.denfry.promptcritical.sim.ops.PermitState;
import dev.denfry.promptcritical.sim.ops.PermitType;
import dev.denfry.promptcritical.sim.ops.Turnaround;
import dev.denfry.promptcritical.sim.ops.TurnaroundPhase;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты нарядов-допусков, огневых работ и остановочного ремонта (13 §13.2, §13.6.2): ключи и en/ru для datagen. */
public final class PermitText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".permit.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        m("issued", "Work permit #%s (%s) issued for %s: isolate it, then click the object with the permit",
                "Наряд-допуск № %s (%s) выдан на %s: изолируйте объект и щёлкните по нему нарядом");
        m("title", "Work permit #%s · %s", "Наряд-допуск № %s · %s");
        m("status", "Permit #%s %s · %s · %s", "Наряд № %s %s · %s · %s");
        m("status.gas", "Gas test: O2 %s %%, flammables %s %% LEL, CO %s ppm (%s s ago)%s",
                "Газоанализ: O2 %s %%, горючие %s %% НКПР, CO %s ppm (%s с назад)%s");
        m("status.gas_none", "No gas test recorded", "Газоанализа нет");
        m("status.unbumped", " — instrument not bump-tested", " — прибор не проверен газом");
        m("status.crew", "Crew: %s · collective dose %s mSv · work %s of %s s", "Бригада: %s · коллективная доза %s мЗв · работа %s из %s с");
        m("status.valid", "Valid for %s min more", "Действует ещё %s мин");
        m("other", "This permit is for %s, not for this object", "Этот наряд — на %s, а не на этот объект");
        m("isolated", "Isolation done and verified: measure the gas, then admit the crew",
                "Изоляция выполнена и проверена: сделайте газоанализ и допустите бригаду");
        m("isolated_no_gas", "Isolation done and verified: click again to admit the crew",
                "Изоляция выполнена и проверена: щёлкните ещё раз, чтобы допустить бригаду");
        m("admitted", "Crew admitted: permit #%s is ACTIVE", "Бригада допущена: наряд № %s ДЕЙСТВУЕТ");
        m("joined", "You signed permit #%s: you may work in its area", "Вы расписались в наряде № %s: можно работать в его объёме");
        m("completed", "Work complete: take the locks off, then Shift-click to close the permit",
                "Работы окончены: снимите замки и закройте наряд щелчком с Shift");
        m("closed", "Permit #%s closed: the equipment is handed back", "Наряд № %s закрыт: оборудование сдано");
        m("complete_hint", "Shift-click to declare the work complete", "Щёлкните с Shift, чтобы объявить окончание работ");
        m("suspended", "Permit #%s SUSPENDED (%s): leave the area, test the gas again", "Наряд № %s ПРИОСТАНОВЛЕН (%s): выйдите из объёма, повторите газоанализ");
        m("expired", "Permit #%s has expired: write a new one", "Срок наряда № %s истёк: выпишите новый");
        m("cancelled", "Permit #%s cancelled", "Наряд № %s отменён");
        m("gas.recorded", "Gas test recorded in permit #%s", "Замер занесён в наряд № %s");
        m("gas.rejected", "Gas test in permit #%s not accepted: %s", "Замер в наряд № %s не принят: %s");
        m("gas.reading", "O2 %s %% · flammables %s %% LEL · H2S %s ppm · CO %s ppm%s",
                "O2 %s %% · горючие %s %% НКПР · H2S %s ppm · CO %s ppm%s");
        m("gas.hold", "Hold the detector for %s s in the work area (T90)", "Держите прибор %s с в объёме работ (T90)");
        m("gas.bumped", "Bump test passed: the detector answers the calibration gas", "Проверка газом пройдена: прибор отвечает на поверочную смесь");
        m("gas.bump_needed", "Detector not bump-tested in the last 24 h: Shift-use it with calibration gas in the other hand",
                "Прибор не проверен газом за 24 ч: Shift + ПКМ с поверочной смесью в другой руке");
        m("hot.refused", "HOT WORK REFUSED: a flammable medium is within 11 m and there is no active hot work permit",
                "ОГНЕВЫЕ РАБОТЫ ЗАПРЕЩЕНЫ: в 11 м горючая среда, действующего наряда на огневые работы нет");
        m("hot.warned", "Hot work without a permit within 11 m of a flammable medium: journaled",
                "Огневые работы без наряда в 11 м от горючей среды: записано в журнал");
        m("hot.ignition", "IGNITION: the flammable cloud caught fire from the hot work", "ВОСПЛАМЕНЕНИЕ: горючее облако вспыхнуло от огневых работ");
        m("alarm", "GAS ALARM in the area of permit #%s: work suspended", "ГАЗОВАЯ ТРЕВОГА в объёме наряда № %s: работы приостановлены");

        type(PermitType.COLD, "cold work", "обычные работы");
        type(PermitType.HOT, "hot work", "огневые работы");
        type(PermitType.CONFINED_SPACE, "confined space", "замкнутое пространство");
        type(PermitType.ELECTRICAL, "electrical", "электроустановка");
        type(PermitType.RADIATION, "radiation work", "радиационно опасные работы");
        type(PermitType.MOC, "change", "изменение");
        state(PermitState.DRAFT, "draft", "черновик");
        state(PermitState.ISSUED, "issued", "выдан");
        state(PermitState.ISOLATED, "isolated", "изоляция выполнена");
        state(PermitState.GAS_TESTED, "gas tested", "газоанализ в норме");
        state(PermitState.ACTIVE, "ACTIVE", "ДЕЙСТВУЕТ");
        state(PermitState.SUSPENDED, "SUSPENDED", "ПРИОСТАНОВЛЕН");
        state(PermitState.WORK_COMPLETE, "work complete", "работы окончены");
        state(PermitState.CLOSED, "closed", "закрыт");
        state(PermitState.EXPIRED, "expired", "просрочен");
        state(PermitState.CANCELLED, "cancelled", "отменён");
        result(PermitRegistry.Result.UNKNOWN, "the permit is not in the register", "наряда нет в журнале");
        result(PermitRegistry.Result.WRONG_STATE, "not at this step", "не на этом шаге");
        result(PermitRegistry.Result.NOT_ISOLATED, "isolation plan not complete: lock and verify every point (padlock, voltage tester)",
                "план изоляции не выполнен: заприте и проверьте каждую точку (замок, указатель напряжения)");
        result(PermitRegistry.Result.NO_GAS_TEST, "no gas test: hold a portable gas detector in the area",
                "нет газоанализа: подержите переносной газоанализатор в объёме работ");
        result(PermitRegistry.Result.GAS_STALE, "the gas test is older than 2 h: measure again", "замеру больше 2 ч: повторите");
        result(PermitRegistry.Result.GAS_FAILED, "the gas test failed", "газоанализ не в норме");
        result(PermitRegistry.Result.NO_FIRE_WATCH, "no fire watch: someone with a fire extinguisher must stand within 11 m",
                "нет пожарного поста: в 11 м должен стоять человек с огнетушителем");
        result(PermitRegistry.Result.WORK_NOT_DONE, "the crew has not finished the work yet", "бригада ещё не закончила работу");
        result(PermitRegistry.Result.LOCKS_REMAIN, "locks are still on the isolation points", "на точках изоляции ещё висят замки");
        result(PermitRegistry.Result.EXPIRED, "the permit has expired", "срок наряда истёк");
        result(PermitRegistry.Result.OK, "done", "готово");
        for (PermitRegistry.Reason r : PermitRegistry.Reason.values())
            m("reason." + low(r), reasonEn(r), reasonRu(r));
        fault(GasLimits.Fault.NOT_BUMP_TESTED, "instrument not bump-tested", "прибор не проверен газом");
        fault(GasLimits.Fault.O2_LOW, "O2 below 19.5 %", "O2 ниже 19,5 %");
        fault(GasLimits.Fault.O2_HIGH, "O2 above 23.5 %", "O2 выше 23,5 %");
        fault(GasLimits.Fault.FLAMMABLE, "flammables present", "есть горючие");
        fault(GasLimits.Fault.H2S, "H2S 10 ppm or more", "H2S от 10 ppm");
        fault(GasLimits.Fault.CO, "CO 25 ppm or more", "CO от 25 ppm");

        for (TurnaroundPhase ph : TurnaroundPhase.values()) m("phase." + low(ph), phaseEn(ph), phaseRu(ph));
        missing(Turnaround.Missing.MACHINES_RUNNING, "machines still running", "машины ещё работают");
        missing(Turnaround.Missing.GAS_TEST, "no gas test of the freed plant", "нет газоанализа освобождённой установки");
        missing(Turnaround.Missing.NO_REPAIRS, "no work completed under a permit", "нет работ, оконченных по наряду");
        missing(Turnaround.Missing.PERMITS_OPEN, "permits still open", "открыты наряды");
        missing(Turnaround.Missing.LOCKS_HUNG, "locks still on", "висят замки");
        missing(Turnaround.Missing.SIS_BYPASSED, "a SIS function is bypassed", "функция ПАЗ в байпасе");
        missing(Turnaround.Missing.SIS_OVERDUE, "a SIS proof test is overdue", "просрочена проверка ПАЗ");
        missing(Turnaround.Missing.NOT_RUNNING, "nothing has started yet", "ничего ещё не пущено");
        missing(Turnaround.Missing.DONE, "the turnaround is complete", "ремонт завершён");
        m("turnaround.phase", "Turnaround: %s", "Остановочный ремонт: %s");
        m("turnaround.missing", "Cannot leave %s: %s", "Нельзя закончить фазу «%s»: %s");
        m("turnaround.complete", "Turnaround complete in %s min, %s jobs done under permits",
                "Остановочный ремонт завершён за %s мин, работ по нарядам: %s");
        m("turnaround.reset", "A new turnaround is being planned", "Планируется новый остановочный ремонт");
        LANG.put("item.promptcritical.portable_gas_detector.unbumped", new String[] {"Not bump-tested", "Не проверен газом"});
        LANG.put("item.promptcritical.portable_gas_detector.bumped", new String[] {"Bump-tested", "Проверен газом"});
        LANG.put("item.promptcritical.portable_gas_detector.hint", new String[] {
                "Hold use %s s to measure; Shift-use with calibration gas to bump-test (valid %s h)",
                "Удерживайте ПКМ %s с для замера; Shift + ПКМ с поверочной смесью — проверка газом (на %s ч)"});
    }

    private static String reasonEn(PermitRegistry.Reason r) {
        return switch (r) {
            case ISSUED -> "issued";
            case ISOLATED -> "isolated";
            case GAS_OK -> "gas test passed";
            case GAS_FAILED -> "gas test failed";
            case ADMITTED -> "crew admitted";
            case GAS_ALARM -> "gas alarm";
            case GAS_RETEST_DUE -> "gas retest due";
            case TIME_EXPIRED -> "time expired";
            case WORK_DONE -> "work done";
            case CLOSED -> "closed";
            case CANCELLED -> "cancelled";
        };
    }

    private static String reasonRu(PermitRegistry.Reason r) {
        return switch (r) {
            case ISSUED -> "выдан";
            case ISOLATED -> "изоляция";
            case GAS_OK -> "газоанализ в норме";
            case GAS_FAILED -> "газоанализ не в норме";
            case ADMITTED -> "бригада допущена";
            case GAS_ALARM -> "газовая тревога";
            case GAS_RETEST_DUE -> "пора повторить газоанализ";
            case TIME_EXPIRED -> "истёк срок";
            case WORK_DONE -> "работы окончены";
            case CLOSED -> "закрыт";
            case CANCELLED -> "отменён";
        };
    }

    private static String phaseEn(TurnaroundPhase p) {
        return switch (p) {
            case PLANNING -> "planning";
            case SHUTDOWN -> "shutdown";
            case FREEING -> "freeing: drain, steam out, purge";
            case OPEN -> "open: inspection and repairs";
            case BOX_UP -> "box-up";
            case PSSR -> "pre-startup safety review";
            case STARTUP -> "startup";
            case COMPLETE -> "complete";
        };
    }

    private static String phaseRu(TurnaroundPhase p) {
        return switch (p) {
            case PLANNING -> "планирование";
            case SHUTDOWN -> "останов";
            case FREEING -> "освобождение: дренаж, пропарка, продувка";
            case OPEN -> "вскрытие: осмотр и ремонт";
            case BOX_UP -> "сборка";
            case PSSR -> "предпусковая проверка";
            case STARTUP -> "пуск";
            case COMPLETE -> "завершён";
        };
    }

    private static String low(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static void type(PermitType t, String en, String ru) {
        m("type." + low(t), en, ru);
    }

    private static void state(PermitState s, String en, String ru) {
        m("state." + low(s), en, ru);
    }

    private static void result(PermitRegistry.Result r, String en, String ru) {
        m("result." + low(r), en, ru);
    }

    private static void fault(GasLimits.Fault f, String en, String ru) {
        m("fault." + low(f), en, ru);
    }

    private static void missing(Turnaround.Missing x, String en, String ru) {
        m("missing." + low(x), en, ru);
    }

    private PermitText() {
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static MutableComponent type(PermitType t) {
        return msg("type." + low(t));
    }

    public static MutableComponent state(PermitState s) {
        return msg("state." + low(s));
    }

    public static MutableComponent result(PermitRegistry.Result r) {
        return msg("result." + low(r));
    }

    public static MutableComponent reason(PermitRegistry.Reason r) {
        return msg("reason." + low(r));
    }

    public static MutableComponent phase(TurnaroundPhase p) {
        return msg("phase." + low(p));
    }

    public static MutableComponent faults(Collection<GasLimits.Fault> fs) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (GasLimits.Fault f : fs) {
            if (!first) out.append("; ");
            first = false;
            out.append(msg("fault." + low(f)));
        }
        return out;
    }

    public static MutableComponent missing(Collection<Turnaround.Missing> ms) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Turnaround.Missing x : ms) {
            if (!first) out.append("; ");
            first = false;
            out.append(msg("missing." + low(x)));
        }
        return out;
    }
}
