package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.CommissioningCheck;
import dev.denfry.promptcritical.sim.ops.CommissioningDefect;
import dev.denfry.promptcritical.sim.ops.CommissioningDefectKind;
import dev.denfry.promptcritical.sim.ops.CommissioningService;
import dev.denfry.promptcritical.sim.ops.CommissioningState;
import dev.denfry.promptcritical.sim.ops.FaultView;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import dev.denfry.promptcritical.sim.ops.UnitPassport;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Щит обслуживания {@code maintenance_panel} (13 §13.6.5) на местном щите: пусконаладка (чек-лист с отметками,
 * найденные дефекты, пробный пуск), строки неисправностей с тяжестью и способом ремонта, сравнение с паспортом.
 */
public final class OpsPanels {
    public static final int ROWS = 6;
    public static final int KIND0 = 0, SEV0 = KIND0 + ROWS, VAL0 = SEV0 + ROWS;
    public static final int UNIT = VAL0 + ROWS, STATE = UNIT + 1, REQUIRED = STATE + 1, PASSED = REQUIRED + 1,
            FOUND = PASSED + 1, TRIAL_S = FOUND + 1, TRIAL_TOTAL = TRIAL_S + 1, FLAGS = TRIAL_TOTAL + 1, BASE_FLUE_C = FLAGS + 1, CUR_FLUE_C = BASE_FLUE_C + 1,
            BASE_EFF = CUR_FLUE_C + 1, CUR_EFF = BASE_EFF + 1, BASE_HEAD10 = CUR_EFF + 1, CUR_HEAD10 = BASE_HEAD10 + 1,
            CHANNELS = CUR_HEAD10 + 1;
    /** Флаги: паспорт есть, машина стоит, сброс нагрузки в пробном пуске был. */
    public static final int F_PASSPORT = 0, F_STOPPED = 1, F_REJECTED = 2;

    private static final Tr NO_UNIT = PanelText.tr("maint.no_unit", "Mount the panel on a boiler, engine, fan, pump or hoist",
                    "Щит ставят на котёл, машину, вентилятор, насос или подъём"),
            COMMISSIONING = PanelText.tr("maint.commissioning", "Commissioning: %s", "Ввод: %s"),
            NO_COMMISSIONING = PanelText.tr("maint.no_commissioning", "Commissioning off", "Пусконаладка выключена"),
            FOUND_DEFECTS = PanelText.tr("maint.found", "Found: %s", "Найдено: %s"),
            NO_DEFECTS = PanelText.tr("maint.no_defects", "No defects found yet", "Дефектов не найдено"),
            TRIAL = PanelText.tr("maint.trial", "Trial run %s of %s s · load rejection %s", "Пробный пуск %s из %s с · сброс нагрузки %s"),
            YES = PanelText.tr("maint.yes", "yes", "был"), NO = PanelText.tr("maint.no", "no", "нет"),
            NORMAL = PanelText.tr("maint.sev0", "NORMAL", "НОРМА"),
            ATTENTION = PanelText.tr("maint.sev1", "ATTENTION", "ВНИМАНИЕ"),
            REPAIR = PanelText.tr("maint.sev2", "NEEDS REPAIR", "ТРЕБУЕТ РЕМОНТА"),
            CRITICAL = PanelText.tr("maint.sev3", "CRITICAL", "КРИТИЧНО"),
            UNKNOWN = PanelText.tr("maint.unknown", "? %s — not measured: %s", "? %s — не измерено: %s"),
            ROW = PanelText.tr("maint.row", "● %s %s %s · %s", "● %s %s %s · %s"),
            PASSPORT = PanelText.tr("maint.passport", "Flue gas %s °C, at acceptance %s · efficiency %s %%, at acceptance %s",
                    "Газы %s °C, при приёмке %s · КПД %s %%, при приёмке %s"),
            PASSPORT_HEAD = PanelText.tr("maint.passport_head", "Head %s m, at acceptance %s m", "Напор %s м, при приёмке %s м"),
            NO_PASSPORT = PanelText.tr("maint.no_passport", "No passport: trends against acceptance appear after the trial run",
                    "Паспорта нет: сравнение с приёмкой — после пробного пуска"),
            STOPPED = PanelText.tr("maint.stopped", "Stopped — repairs allowed", "Стоит — ремонт разрешён"),
            RUNNING = PanelText.tr("maint.running", "Running — stop it for repairs", "В работе — для ремонта остановите");
    private static final Tr[] UNITS = {
            PanelText.tr("maint.unit.fire_tube_boiler", "Fire-tube boiler", "Жаротрубный котёл"),
            PanelText.tr("maint.unit.steam_engine", "Steam engine", "Паровая машина"),
            PanelText.tr("maint.unit.main_mine_fan", "Main mine fan", "Вентилятор главного проветривания"),
            PanelText.tr("maint.unit.mine_drainage_pump", "Mine drainage pump", "Насос водоотлива"),
            PanelText.tr("maint.unit.mine_hoist", "Mine hoist", "Подъёмная машина")};
    private static final Tr[] FIX = {
            PanelText.tr("maint.fix.bearing_wear", "bronze bearing shell, stopped, 60 s", "бронзовый вкладыш, останов, 60 с"),
            PanelText.tr("maint.fix.lubrication", "oil can + tallow, on the go", "маслёнка + жир, на ходу"),
            PanelText.tr("maint.fix.hx_fouling", "boiler scraper: stopped, cooled, drained", "скребок: останов, остывание, дренаж"),
            PanelText.tr("maint.fix.packing_leak", "wrench on the go or gland packing, stopped", "ключ на ходу или набивка, останов"),
            PanelText.tr("maint.fix.impeller_erosion", "pump impeller, stopped, 120 s", "рабочее колесо, останов, 120 с"),
            PanelText.tr("maint.fix.rope_wear", "wire rope", "канат")};
    private static final Tr[] PROBES = {
            PanelText.tr("maint.probe.bearing_wear", "stethoscope or hand", "стетоскоп или рука"),
            PanelText.tr("maint.probe.hx_fouling", "passport", "паспорт"),
            PanelText.tr("maint.probe.packing_leak", "stethoscope", "стетоскоп"),
            PanelText.tr("maint.probe.impeller_erosion", "passport", "паспорт"),
            PanelText.tr("maint.probe.rope_wear", "inspection", "осмотр")};
    private static final List<MaintenanceFaultKind> SLICE = List.of(MaintenanceFaultKind.BEARING_WEAR,
            MaintenanceFaultKind.LUBRICATION, MaintenanceFaultKind.HX_FOULING, MaintenanceFaultKind.PACKING_LEAK,
            MaintenanceFaultKind.IMPELLER_EROSION, MaintenanceFaultKind.ROPE_WEAR);

    public static final PanelLayout MAINTENANCE;

    static {
        PanelLayout.Builder b = PanelLayout.builder("maintenance_panel", 248, 168, CHANNELS)
                .readout(6, 22, 236, OpsPanels::header, null)
                .readout(6, 36, 236, OpsPanels::found, null)
                .readout(6, 50, 236, OpsPanels::trial, null);
        for (int i = 0; i < ROWS; i++) {
            int row = i;
            b.readout(6, 68 + 13 * i, 236, v -> row(v, row), null);
        }
        MAINTENANCE = b.readout(6, 150, 236, OpsPanels::passport, null).noInventory().build();
    }

    private OpsPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(MAINTENANCE);
    }

    // --- клиент: строки ---

    private static Component header(PanelValues v) {
        int unit = v.raw(UNIT);
        if (unit <= 0) return NO_UNIT.get().withStyle(ChatFormatting.GRAY);
        MutableComponent out = UNITS[unit - 1].get().append(" · ");
        int st = v.raw(STATE);
        if (st <= 0) return out.append(NO_COMMISSIONING.get());
        CommissioningState s = CommissioningState.values()[st - 1];
        MutableComponent state = OpsText.of(s).withStyle(s == CommissioningState.ACCEPTED ? ChatFormatting.GREEN : ChatFormatting.RED);
        MutableComponent checks = Component.empty();
        for (CommissioningCheck c : CommissioningCheck.values()) {
            if ((v.raw(REQUIRED) & 1 << c.ordinal()) == 0) continue;
            boolean ok = (v.raw(PASSED) & 1 << c.ordinal()) != 0;
            checks.append(" ").append(OpsText.of(c).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(ok ? "✔" : "✘");
        }
        return out.append(COMMISSIONING.get(state)).append(checks);
    }

    private static Component found(PanelValues v) {
        if (v.raw(STATE) <= 0) return v.bit(FLAGS, F_STOPPED) ? STOPPED.get() : RUNNING.get();
        int mask = v.raw(FOUND);
        if (mask == 0) return NO_DEFECTS.get();
        MutableComponent list = Component.empty();
        boolean first = true;
        for (CommissioningDefectKind d : CommissioningDefectKind.values()) {
            if ((mask & 1 << d.ordinal()) == 0) continue;
            if (!first) list.append(", ");
            list.append(OpsText.of(d));
            first = false;
        }
        return FOUND_DEFECTS.get(list).withStyle(ChatFormatting.GOLD);
    }

    private static Component trial(PanelValues v) {
        if (v.raw(STATE) <= 0 || v.raw(TRIAL_S) <= 0) return v.bit(FLAGS, F_STOPPED) ? STOPPED.get() : RUNNING.get();
        return TRIAL.get(v.raw(TRIAL_S), v.raw(TRIAL_TOTAL), v.bit(FLAGS, F_REJECTED) ? YES.get() : NO.get());
    }

    private static Component row(PanelValues v, int i) {
        int k = v.raw(KIND0 + i);
        if (k <= 0) return Component.empty();
        MaintenanceFaultKind kind = MaintenanceFaultKind.values()[k - 1];
        int sevKnown = v.raw(SEV0 + i);
        int sev = sevKnown & 3;
        boolean known = (sevKnown & 4) != 0;
        int slice = SLICE.indexOf(kind);
        if (!known) {
            Tr probe = switch (kind) {
                case BEARING_WEAR -> PROBES[0];
                case HX_FOULING -> PROBES[1];
                case PACKING_LEAK -> PROBES[2];
                case IMPELLER_EROSION -> PROBES[3];
                default -> PROBES[4];
            };
            return UNKNOWN.get(OpsText.of(kind), probe.get()).withStyle(ChatFormatting.GRAY);
        }
        Tr sevText = switch (sev) {
            case 0 -> NORMAL;
            case 1 -> ATTENTION;
            case 2 -> REPAIR;
            default -> CRITICAL;
        };
        ChatFormatting color = switch (sev) {
            case 0 -> ChatFormatting.GREEN;
            case 1 -> ChatFormatting.YELLOW;
            case 2 -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
        String bar = "▮".repeat(sev + 1) + "▯".repeat(3 - sev);
        return ROW.get(OpsText.of(kind), bar, sevText.get(), slice >= 0 ? FIX[slice].get() : Component.empty()).withStyle(color);
    }

    private static Component passport(PanelValues v) {
        if (v.raw(UNIT) <= 0) return Component.empty();
        if (!v.bit(FLAGS, F_PASSPORT)) return NO_PASSPORT.get().withStyle(ChatFormatting.GRAY);
        UnitKind kind = UnitKind.values()[v.raw(UNIT) - 1];
        if (kind == UnitKind.FIRE_TUBE_BOILER)
            return PASSPORT.get(v.raw(CUR_FLUE_C), v.raw(BASE_FLUE_C), v.raw(CUR_EFF), v.raw(BASE_EFF));
        if (kind == UnitKind.MINE_DRAINAGE_PUMP)
            return PASSPORT_HEAD.get(v.num(CUR_HEAD10, 10, 1), v.num(BASE_HEAD10, 10, 1));
        return Component.empty();
    }

    // --- сервер: каналы ---

    /**
     * Каналы щита по установке; {@code current} — текущие показатели машины для сравнения с паспортом.
     *
     * @param stopped условие ремонта {@code STOP}
     */
    public static void read(int[] out, OpsSubsystem ops, OpsSubsystem.Unit u, boolean stopped, Baseline current) {
        Arrays.fill(out, 0);
        out[UNIT] = u.kind().ordinal() + 1;
        List<FaultView> faults = u.panel().faults();
        for (int i = 0; i < Math.min(ROWS, faults.size()); i++) {
            FaultView f = faults.get(i);
            out[KIND0 + i] = f.kind().ordinal() + 1;
            out[SEV0 + i] = f.severity() | (f.known() ? 4 : 0);
            out[VAL0 + i] = f.known() ? (int) Math.round(u.panel().source(i).value() * 1000) : 0;
        }
        CommissioningService c = ops.commissioning();
        int flags = stopped ? 1 << F_STOPPED : 0;
        if (u.trialRejected()) flags |= 1 << F_REJECTED;
        c.state(u.id()).ifPresent(s -> {
            out[STATE] = s.ordinal() + 1;
            for (CommissioningCheck k : u.kind().checklist()) out[REQUIRED] |= 1 << k.ordinal();
            for (CommissioningCheck k : c.passedChecks(u.id())) out[PASSED] |= 1 << k.ordinal();
            for (CommissioningDefect d : c.defects(u.id())) if (d.found()) out[FOUND] |= 1 << d.kind().ordinal();
            out[TRIAL_S] = (int) Math.round(u.trialRunS());
            out[TRIAL_TOTAL] = (int) Math.round(ops.config().trialRunS());
        });
        UnitPassport p = c.passport(u.id()).orElse(null);
        if (p != null) {
            flags |= 1 << F_PASSPORT;
            Baseline b = p.baseline();
            out[BASE_FLUE_C] = celsius(b.flueGasK());
            out[CUR_FLUE_C] = celsius(current.flueGasK());
            out[BASE_EFF] = percent(b.efficiency());
            out[CUR_EFF] = percent(current.efficiency());
            out[BASE_HEAD10] = finite(b.headM() * 10);
            out[CUR_HEAD10] = finite(current.headM() * 10);
        }
        out[FLAGS] = flags;
    }

    private static int celsius(double k) {
        return finite(k - 273.15);
    }

    private static int percent(double eta) {
        return finite(eta * 100);
    }

    private static int finite(double v) {
        return Double.isFinite(v) ? (int) Math.round(v) : 0;
    }
}
