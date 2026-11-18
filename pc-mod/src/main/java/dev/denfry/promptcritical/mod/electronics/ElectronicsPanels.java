package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.electronics.ChipDesign;
import dev.denfry.promptcritical.sim.electronics.WaferFab;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * Щиты кремниевой цепочки (11 §11.5): печь, реактор трихлорсилана, реактор Сименса, установка Чохральского, резка
 * пластин и чистая комната со шлюзом — пульт планарного процесса.
 */
public final class ElectronicsPanels {
    // общие для электропечей: мощность, кВт, прогресс, ‰, флаги
    public static final int E_POWER_KW = 0, E_PROGRESS = 1, E_FLAGS = 2, E_A = 3, E_B = 4, E_C = 5, E_CHANNELS = 6;
    public static final int EF_ENERGIZED = 0, EF_SWITCH = 1, EF_RUNNING = 2, EF_SHORT = 3;
    public static final int BTN_SWITCH = 0;
    // Чохральский и резка
    public static final int K_RPM = 0, K_PULL = 1, K_PROGRESS = 2, K_FLAGS = 3, K_ARGON = 4, K_LEFT = 5, K_CHANNELS = 6;
    public static final int KF_RUNNING = 0, KF_MONO = 1, KF_WINDOW = 2, KF_ARGON = 3;
    public static final int BTN_PULL_DOWN = 0, BTN_PULL_UP = 1;
    // фабрика
    public static final int F_POWER_KW = 0, F_ISO10 = 1, F_PROGRESS = 2, F_FLAGS = 3, F_DESIGN = 4, F_LITHO = 5, F_GAP = 6, F_AMBIENT = 7,
            F_OX_T = 8, F_OX_MIN = 9, F_DIFF_T = 10, F_DIFF_MIN = 11, F_CD100 = 12, F_OXIDE_NM = 13, F_XJ100 = 14, F_YIELD10 = 15,
            F_GOOD = 16, F_REJECT = 17, F_HEPA = 18, F_LEAK = 19, F_VOLUME = 20, F_CHANNELS = 21;
    public static final int FF_ENERGIZED = 0, FF_ENCLOSED = 1, FF_DOOR = 2, FF_RUNNING = 3, FF_LENS = 4, FF_SWITCH = 5;
    public static final int B_DESIGN = 0, B_LITHO = 1, B_GAP_DOWN = 2, B_GAP_UP = 3, B_AMBIENT = 4, B_OX_T_DOWN = 5, B_OX_T_UP = 6,
            B_OX_MIN_DOWN = 7, B_OX_MIN_UP = 8, B_DIFF_T_DOWN = 9, B_DIFF_T_UP = 10, B_DIFF_MIN_DOWN = 11, B_DIFF_MIN_UP = 12, B_SWITCH = 13;
    public static final int SLOT_WAFER = 0, SLOT_ALUMINIUM = 1, SLOT_LENS = 2, SLOT_OUT = 3, OUT_SLOTS = 4;

    private static final Tr KW = PanelText.tr("si.kw", "kW", "кВт"), RPM = PanelText.RPM,
            POWER = PanelText.tr("si.power", "Power", "Мощность"),
            ENERGIZED = PanelText.tr("si.energized", "Energized", "Напряжение"),
            SWITCH = PanelText.tr("si.switch", "Switch", "Рубильник"),
            RUNNING = PanelText.tr("si.running", "Running", "Работа"),
            SHORT = PanelText.tr("si.short", "No feed", "Нет сырья"),
            PROGRESS = PanelText.tr("si.progress", "Batch", "Партия"),
            SAF_NOTE = PanelText.tr("si.saf_note", "SiO₂ + 2C → Si + 2CO at 2000 °C · %s kWh per kg Si · electrode %s %%",
                    "SiO₂ + 2C → Si + 2CO при 2000 °C · %s кВт·ч на кг Si · электрод %s %%"),
            QUARTZ = PanelText.tr("si.quartz", "Quartz (4 per batch)", "Кварц (4 на партию)"),
            COKE = PanelText.tr("si.coke", "Coke (2 per batch)", "Кокс (2 на партию)"),
            ELECTRODE = PanelText.tr("si.electrode", "Graphite electrode", "Графитовый электрод"),
            OUT = PanelText.tr("si.out", "Product", "Продукт"),
            TCS_NOTE = PanelText.tr("si.tcs_note", "Si + 3HCl → SiHCl₃ + H₂ at 300 °C · HCl %s Nm³ · SiHCl₃ %s L · H₂ %s Nm³",
                    "Si + 3HCl → SiHCl₃ + H₂ при 300 °C · HCl %s нм³ · SiHCl₃ %s л · H₂ %s нм³"),
            MG_SI = PanelText.tr("si.mg_si", "Metallurgical silicon", "Технический кремний"),
            SIEMENS_NOTE = PanelText.tr("si.siemens_note", "SiHCl₃ + H₂ → Si + 3HCl on rods at 1100 °C · SiHCl₃ %s L · H₂ %s Nm³ · HCl out %s Nm³",
                    "SiHCl₃ + H₂ → Si + 3HCl на стержнях при 1100 °C · SiHCl₃ %s л · H₂ %s нм³ · HCl на выходе %s нм³"),
            ROTATION = PanelText.tr("si.rotation", "Seed rotation", "Вращение затравки"),
            PULL = PanelText.tr("si.pull", "Pull", "Вытягивание"),
            MM_MIN = PanelText.tr("si.mm_min", "mm/min", "мм/мин"),
            MONO = PanelText.tr("si.mono", "Single crystal", "Монокристалл"),
            WINDOW = PanelText.tr("si.window", "In window", "В окне"),
            ARGON = PanelText.tr("si.argon", "Argon", "Аргон"),
            PULL_DOWN = PanelText.tr("si.pull_down", "Pull −", "Вытягивание −"),
            PULL_UP = PanelText.tr("si.pull_up", "Pull +", "Вытягивание +"),
            CZ_NOTE = PanelText.tr("si.cz_note", "Window 0.5–2 mm/min and 5–30 rpm of the seed (shaft ÷ 4) · argon %s Nm³ · crystal %s %%",
                    "Окно: 0,5–2 мм/мин и 5–30 об/мин затравки (вал ÷ 4) · аргон %s нм³ · кристалл %s %%"),
            POLY = PanelText.tr("si.poly", "Polysilicon (1 kg)", "Поликремний (1 кг)"),
            INGOT = PanelText.tr("si.ingot", "Single-crystal ingot", "Монокристаллический слиток"),
            SAW_NOTE = PanelText.tr("si.saw_note", "Wafers left in the ingot %s · polishing %s min per 10 wafers (Preston)",
                    "Пластин в слитке осталось %s · полировка %s мин на 10 пластин (Престон)"),
            FAB_ISO = PanelText.tr("si.iso", "ISO class", "Класс ISO"),
            ENCLOSED = PanelText.tr("si.enclosed", "Enclosed", "Замкнута"),
            DOOR = PanelText.tr("si.door", "Door open", "Дверь"),
            LENS = PanelText.tr("si.lens", "Stepper", "Степпер"),
            WAFER = PanelText.tr("si.wafer", "Silicon wafer", "Кремниевая пластина"),
            ALUMINIUM = PanelText.tr("si.aluminium", "Aluminium for metallisation (1 per 4 wafers)", "Алюминий для металлизации (1 на 4 пластины)"),
            LENS_SLOT = PanelText.tr("si.lens_slot", "Stepper lens: g-line projection, 1 µm", "Объектив степпера: проекция g-line, 1 мкм"),
            CHIPS = PanelText.tr("si.chips", "Chips", "Микросхемы"),
            ROOM = PanelText.tr("si.room", "Room %s m³ · HEPA %s m³/h · leaks %s m³/h · ISO %s",
                    "Комната %s м³ · HEPA %s м³/ч · подсос %s м³/ч · ISO %s"),
            RECIPE = PanelText.tr("si.recipe", "%s · %s → CD %s µm · oxide %s %s °C %s min → %s nm · P %s °C %s min → xⱼ %s µm",
                    "%s · %s → CD %s мкм · окисел %s %s °C %s мин → %s нм · P %s °C %s мин → xⱼ %s мкм"),
            LAST = PanelText.tr("si.last", "Last wafer: %s good, yield %s %% · %s", "Последняя пластина: годных %s, выход %s %% · %s"),
            OK = PanelText.tr("si.ok", "in window", "в окне"),
            DESIGN_BTN = PanelText.tr("si.design_btn", "Product", "Изделие"),
            LITHO_BTN = PanelText.tr("si.litho_btn", "Litho", "Литогр."),
            AMBIENT_BTN = PanelText.tr("si.ambient_btn", "Dry/wet", "Сух./влажн."),
            GAP_TIP = PanelText.tr("si.gap_tip", "Proximity gap −/+ 10 µm", "Зазор бесконтактной печати −/+ 10 мкм"),
            OX_T_TIP = PanelText.tr("si.ox_t_tip", "Oxidation temperature −/+ 25 °C", "Температура окисления −/+ 25 °C"),
            OX_MIN_TIP = PanelText.tr("si.ox_min_tip", "Oxidation time −/+ 5 min", "Время окисления −/+ 5 мин"),
            DIFF_T_TIP = PanelText.tr("si.diff_t_tip", "Phosphorus diffusion temperature −/+ 25 °C", "Температура загонки фосфора −/+ 25 °C"),
            DIFF_MIN_TIP = PanelText.tr("si.diff_min_tip", "Diffusion time −/+ 5 min", "Время загонки −/+ 5 мин");
    static final Tr[] DESIGNS = {
            PanelText.tr("si.d.transistor", "transistor", "транзистор"),
            PanelText.tr("si.d.logic", "logic IC", "логическая микросхема"),
            PanelText.tr("si.d.memory", "4 Kbit RAM", "ОЗУ 4 Кбит"),
            PanelText.tr("si.d.cpu", "microprocessor", "микропроцессор")};
    static final Tr[] LITHOS = {
            PanelText.tr("si.l.contact", "contact print", "контактная печать"),
            PanelText.tr("si.l.proximity", "proximity %s µm", "бесконтактная %s мкм"),
            PanelText.tr("si.l.gline", "g-line stepper", "степпер g-line")};
    static final Tr[] AMBIENTS = {PanelText.tr("si.a.dry", "dry O₂", "сухой O₂"), PanelText.tr("si.a.wet", "wet H₂O", "влажный H₂O")};
    static final Tr[] REJECTS = {
            PanelText.tr("si.r.ox_thin", "gate oxide too thin: breakdown", "подзатворный окисел тонок: пробой"),
            PanelText.tr("si.r.ox_thick", "gate oxide too thick: threshold too high", "подзатворный окисел толст: высокий порог"),
            PanelText.tr("si.r.xj_shallow", "junctions too shallow: high resistance", "переходы мелкие: большое сопротивление"),
            PanelText.tr("si.r.xj_deep", "junctions too deep: punch-through", "переходы глубокие: смыкание"),
            PanelText.tr("si.r.large", "die too large for this feature size", "кристалл слишком велик для этого размера элемента"),
            PanelText.tr("si.r.none", "no wafer run yet", "пластин ещё не было")};

    /** Электропечь (рудно-термическая и Сименс): мощность, прогресс, два входа, электрод/выход. */
    private static PanelLayout.Builder furnace(String id, int maxKw) {
        return PanelLayout.builder(id, 220, 140, E_CHANNELS)
                .dial(8, 24, true, E_POWER_KW, 1, maxKw, Double.NaN, 0, KW.get(), POWER.get(), null)
                .bar(72, 26, 100, E_PROGRESS, 10, 100, 0xFF60A060, PROGRESS.get(), null)
                .lamp(72, 42, E_FLAGS, EF_ENERGIZED, LampColor.GREEN, ENERGIZED.get())
                .lamp(140, 42, E_FLAGS, EF_SWITCH, LampColor.WHITE, SWITCH.get())
                .lamp(72, 56, E_FLAGS, EF_RUNNING, LampColor.AMBER, RUNNING.get())
                .lamp(140, 56, E_FLAGS, EF_SHORT, LampColor.RED, SHORT.get())
                .button(72, 70, 60, BTN_SWITCH, SWITCH.get(), null);
    }

    public static final PanelLayout SAF = furnace("submerged_arc_furnace", 4000)
            .readout(8, 92, 204, v -> SAF_NOTE.get(v.num(E_A, 10, 1), v.raw(E_B)), null)
            .slot(16, 112, 0, s -> s.is(Items.QUARTZ), QUARTZ.get())
            .slot(38, 112, 1, s -> s.is(PcItems.COKE.get()), COKE.get())
            .slot(60, 112, 2, s -> s.is(PcItems.GRAPHITE_ELECTRODE.get()), ELECTRODE.get())
            .output(100, 112, 3, OUT.get())
            .build();

    public static final PanelLayout TCS = PanelLayout.builder("trichlorosilane_reactor", 220, 120, E_CHANNELS)
            .bar(8, 26, 120, E_PROGRESS, 10, 100, 0xFF60A060, PROGRESS.get(), null)
            .lamp(8, 42, E_FLAGS, EF_RUNNING, LampColor.AMBER, RUNNING.get())
            .lamp(72, 42, E_FLAGS, EF_SHORT, LampColor.RED, SHORT.get())
            .readout(8, 62, 204, v -> TCS_NOTE.get(v.num(E_A, 10, 1), v.num(E_B, 10, 1), v.num(E_C, 10, 1)), null)
            .slot(16, 92, 0, s -> s.is(PcItems.METALLURGICAL_SILICON.get()), MG_SI.get())
            .build();

    public static final PanelLayout SIEMENS = furnace("siemens_reactor", 2500)
            .readout(8, 92, 204, v -> SIEMENS_NOTE.get(v.num(E_A, 10, 1), v.num(E_B, 10, 1), v.num(E_C, 10, 1)), null)
            .output(100, 112, 0, OUT.get())
            .build();

    public static final PanelLayout CZ = PanelLayout.builder("czochralski_puller", 220, 140, K_CHANNELS)
            .dial(8, 24, false, K_RPM, 1, 40, 30, 0, RPM.get(), ROTATION.get(), null)
            .dial(64, 24, false, K_PULL, 100, 4, 2, 2, MM_MIN.get(), PULL.get(), null)
            .bar(120, 26, 92, K_PROGRESS, 10, 100, 0xFF8080C0, PROGRESS.get(), null)
            .lamp(120, 42, K_FLAGS, KF_RUNNING, LampColor.AMBER, RUNNING.get())
            .lamp(170, 42, K_FLAGS, KF_WINDOW, LampColor.GREEN, WINDOW.get())
            .lamp(120, 56, K_FLAGS, KF_MONO, LampColor.GREEN, MONO.get())
            .lamp(170, 56, K_FLAGS, KF_ARGON, LampColor.WHITE, ARGON.get())
            .button(8, 74, 60, BTN_PULL_DOWN, PULL_DOWN.get(), null)
            .button(72, 74, 60, BTN_PULL_UP, PULL_UP.get(), null)
            .readout(8, 94, 204, v -> CZ_NOTE.get(v.num(K_ARGON, 10, 1), v.num(K_PROGRESS, 10, 0)), null)
            .slot(16, 114, 0, s -> s.is(PcItems.POLYSILICON.get()) || s.is(PcItems.SILICON_INGOT.get()), POLY.get())
            .output(60, 114, 1, OUT.get())
            .build();

    public static final PanelLayout SAW = PanelLayout.builder("wafer_saw", 220, 120, K_CHANNELS)
            .dial(8, 24, false, K_RPM, 1, 256, Double.NaN, 0, RPM.get(), ROTATION.get(), null)
            .bar(64, 26, 120, K_PROGRESS, 10, 100, 0xFF8080C0, PROGRESS.get(), null)
            .lamp(64, 42, K_FLAGS, KF_RUNNING, LampColor.AMBER, RUNNING.get())
            .readout(8, 70, 204, v -> SAW_NOTE.get(v.raw(K_LEFT), v.num(K_PULL, 10, 1)), null)
            .slot(16, 94, 0, ElectronicsBlocks::isMonoIngot, INGOT.get())
            .output(60, 94, 1, OUT.get())
            .build();

    public static final PanelLayout FAB = PanelLayout.builder("cleanroom_airlock", 256, 200, F_CHANNELS)
            .dial(8, 24, false, F_ISO10, 10, 9, 7, 1, Component.literal("ISO"), FAB_ISO.get(), null)
            .dial(64, 24, false, F_POWER_KW, 1, 300, Double.NaN, 0, KW.get(), POWER.get(), null)
            .bar(120, 26, 128, F_PROGRESS, 10, 100, 0xFF60A0A0, PROGRESS.get(), null)
            .lamp(120, 42, F_FLAGS, FF_ENERGIZED, LampColor.GREEN, ENERGIZED.get())
            .lamp(176, 42, F_FLAGS, FF_ENCLOSED, LampColor.GREEN, ENCLOSED.get())
            .lamp(120, 56, F_FLAGS, FF_DOOR, LampColor.RED, DOOR.get())
            .lamp(176, 56, F_FLAGS, FF_LENS, LampColor.WHITE, LENS.get())
            .button(8, 74, 48, B_DESIGN, DESIGN_BTN.get(), null)
            .button(58, 74, 48, B_LITHO, LITHO_BTN.get(), null)
            .button(108, 74, 18, B_GAP_DOWN, Component.literal("−"), GAP_TIP.get())
            .button(128, 74, 18, B_GAP_UP, Component.literal("+"), GAP_TIP.get())
            .button(150, 74, 50, B_AMBIENT, AMBIENT_BTN.get(), null)
            .button(204, 74, 44, B_SWITCH, SWITCH.get(), null)
            .button(8, 94, 18, B_OX_T_DOWN, Component.literal("T−"), OX_T_TIP.get())
            .button(28, 94, 18, B_OX_T_UP, Component.literal("T+"), OX_T_TIP.get())
            .button(48, 94, 18, B_OX_MIN_DOWN, Component.literal("t−"), OX_MIN_TIP.get())
            .button(68, 94, 18, B_OX_MIN_UP, Component.literal("t+"), OX_MIN_TIP.get())
            .button(128, 94, 18, B_DIFF_T_DOWN, Component.literal("T−"), DIFF_T_TIP.get())
            .button(148, 94, 18, B_DIFF_T_UP, Component.literal("T+"), DIFF_T_TIP.get())
            .button(168, 94, 18, B_DIFF_MIN_DOWN, Component.literal("t−"), DIFF_MIN_TIP.get())
            .button(188, 94, 18, B_DIFF_MIN_UP, Component.literal("t+"), DIFF_MIN_TIP.get())
            .readout(8, 116, 240, ElectronicsPanels::recipe, null)
            .readout(8, 128, 240, ElectronicsPanels::room, null)
            .readout(8, 140, 240, ElectronicsPanels::last, null)
            .slot(16, 172, SLOT_WAFER, s -> s.is(PcItems.SILICON_WAFER.get()), WAFER.get())
            .slot(38, 172, SLOT_ALUMINIUM, CleanroomAirlockBlockEntity::isAluminium, ALUMINIUM.get())
            .slot(60, 172, SLOT_LENS, s -> s.is(PcItems.STEPPER_LENS.get()), LENS_SLOT.get())
            .output(110, 172, SLOT_OUT, CHIPS.get())
            .output(132, 172, SLOT_OUT + 1, CHIPS.get())
            .output(154, 172, SLOT_OUT + 2, CHIPS.get())
            .output(176, 172, SLOT_OUT + 3, CHIPS.get())
            .build();

    private ElectronicsPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(SAF, TCS, SIEMENS, CZ, SAW, FAB);
    }

    private static Component recipe(PanelValues v) {
        Tr litho = LITHOS[Math.floorMod(v.raw(F_LITHO), LITHOS.length)];
        return RECIPE.get(DESIGNS[Math.floorMod(v.raw(F_DESIGN), DESIGNS.length)].get(), litho.get(v.raw(F_GAP)), v.num(F_CD100, 100, 2),
                AMBIENTS[Math.floorMod(v.raw(F_AMBIENT), 2)].get(), v.raw(F_OX_T), v.raw(F_OX_MIN), v.raw(F_OXIDE_NM), v.raw(F_DIFF_T),
                v.raw(F_DIFF_MIN), v.num(F_XJ100, 100, 2));
    }

    private static Component room(PanelValues v) {
        Component c = ROOM.get(v.raw(F_VOLUME), v.raw(F_HEPA), v.num(F_LEAK, 10, 1), v.num(F_ISO10, 10, 1));
        return v.bit(F_FLAGS, FF_ENCLOSED) ? c : c.copy().withStyle(ChatFormatting.RED);
    }

    private static Component last(PanelValues v) {
        int r = v.raw(F_REJECT);
        Component reason = r < 0 ? OK.get() : REJECTS[Math.min(r, REJECTS.length - 1)].get();
        Component c = LAST.get(v.raw(F_GOOD), v.num(F_YIELD10, 10, 1), reason);
        return r >= 0 && r < ChipDesign.Reject.values().length ? c.copy().withStyle(ChatFormatting.RED) : c;
    }

    /** Номер причины брака в канал щита: −1 — в окне, длина таблицы — пластин не было. */
    static int rejectCode(WaferFab.Result r) {
        if (r == null) return ChipDesign.Reject.values().length;
        return r.reject() == null ? -1 : r.reject().ordinal();
    }
}
