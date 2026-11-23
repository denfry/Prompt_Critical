package dev.denfry.promptcritical.mod.foundry;

import static dev.denfry.promptcritical.mod.panel.PanelText.CELSIUS;
import static dev.denfry.promptcritical.mod.panel.PanelText.RPM;
import static dev.denfry.promptcritical.mod.panel.PanelText.RUNNING;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT_TIP;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.CastAlloy;
import dev.denfry.promptcritical.sim.metallurgy.CastingKind;
import dev.denfry.promptcritical.sim.metallurgy.CrucibleUnit;
import dev.denfry.promptcritical.sim.metallurgy.CupolaUnit;
import dev.denfry.promptcritical.sim.metallurgy.MachinedPart;
import dev.denfry.promptcritical.sim.mining.CoalJig;
import dev.denfry.promptcritical.sim.mining.DenseMediumSeparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Местные щиты литейки, обогатительной и механического цеха. У машин на валу Create — тахометр вала (0–256 об/мин,
 * предел Create) и лампа работы; каналы {@link #SHAFT_RPM10} и {@link #FLAGS} общие, остальные — свои с {@link #OWN}.
 */
public final class FoundryPanels {
    public static final int SHAFT_RPM10 = 0, FLAGS = 1, OWN = 2;
    public static final int F_RUNNING = 0, F_HELD = 1;

    // вагранка
    public static final int C_WELL_C = OWN, C_WELL_KG10 = OWN + 1, C_IRON_KG = OWN + 2, C_COKE_KG = OWN + 3, C_AIR_NM3H = OWN + 4,
            C_MELT_KGH = OWN + 5, C_CHANNELS = OWN + 6;
    // тигель
    public static final int T_TEMP_C = OWN, T_MASS_KG10 = OWN + 1, T_LIQUID_PERMIL = OWN + 2, T_FUEL_KG10 = OWN + 3,
            T_ALLOY = OWN + 4, T_CHANNELS = OWN + 5;
    // отсадка
    public static final int J_FEED_KG = OWN, J_WATER_L = OWN + 1, J_RHO_CLEAN = OWN + 2, J_RHO_REFUSE = OWN + 3,
            J_CHANNELS = OWN + 4;
    public static final int BUTTON_JIG_CUT = 0;
    /** Тяжелосредный сепаратор. */
    public static final int D_FEED_KG = OWN, D_MEDIUM_L = OWN + 1, D_RHO100 = OWN + 2, D_MAGNETITE_KG = OWN + 3,
            D_CHANNELS = OWN + 4;
    // формовка, выбивка
    public static final int M_SAND = OWN, M_NEED = OWN + 1, M_WATER_MB = OWN + 2, M_PROGRESS_PERMIL = OWN + 3, M_KIND = OWN + 4,
            M_CHANNELS = OWN + 5;
    public static final int K_READY = OWN, K_PROGRESS_PERMIL = OWN + 1, K_CHANNELS = OWN + 2;
    // станки
    public static final int L_SPINDLE_RPM = OWN, L_PROGRESS_PERMIL = OWN + 1, L_WEAR_PERMIL = OWN + 2, L_KIND = OWN + 3,
            L_CHANNELS = OWN + 4;
    // станочный парк: скорость (об/мин, дв. ход/мин), подача ×1000, Ra ×100 текущего прохода, мощность, v_c ×10, деталь,
    // итог последней снятой детали (брак — ProductionToolUnit.Reject, Ra ×100, IT)
    public static final int P_SPEED = OWN, P_PROGRESS_PERMIL = OWN + 1, P_WEAR_PERMIL = OWN + 2, P_PART = OWN + 3,
            P_FEED_UM = OWN + 4, P_RA100 = OWN + 5, P_POWER_W = OWN + 6, P_VC10 = OWN + 7, P_LAST_REJECT = OWN + 8,
            P_LAST_RA100 = OWN + 9, P_LAST_IT = OWN + 10, P_CHANNELS = OWN + 11;
    public static final int BUTTON_FEED = 0;

    private static final Tr WELL = PanelText.tr("cupola.well", "Well", "Горн"),
            BLAST = PanelText.tr("cupola.blast", "Blast", "Дутьё"),
            NM3H = PanelText.tr("unit.nm3h", "Nm³/h", "нм³/ч"),
            STACK_IRON = PanelText.tr("cupola.iron", "Iron in the stack", "Металл в шахте"),
            WELL_METAL = PanelText.tr("cupola.well_metal", "Molten iron in the well", "Чугун в горне"),
            CUPOLA_IRON = PanelText.tr("cupola.iron_slot", "Charge: pig iron, iron ingots, scrap", "Колоша: чугун, железо, лом"),
            CUPOLA_COKE = PanelText.tr("cupola.coke_slot", "Charge: coke", "Колоша: кокс"),
            CUPOLA_STATUS = PanelText.tr("cupola.status", "Melting %s kg/h · taps into a mould below", "Плавит %s кг/ч · выпуск в форму у подножия"),
            TAP = PanelText.tr("tap_held", "Held", "Задержан"),
            TAP_TIP = PanelText.tr("tap_held_tip", "A redstone signal holds the tap", "Сигнал красного камня держит выпуск"),
            KG_OF = PanelText.tr("kg_of", "%s of %s kg", "%s из %s кг"),
            L_OF = PanelText.tr("l_of", "%s of %s L", "%s из %s л"),
            MB = PanelText.tr("mb", "%s mB", "%s мВ"),
            KG = PanelText.tr("kg", "%s kg", "%s кг"),
            STACK_COKE_TIP = PanelText.tr("cupola.coke_tip", "Coke in the stack: %s kg", "Кокса в шахте: %s кг"),
            LIT = PanelText.tr("furnace_lit", "Fire", "Горит");

    private static final Tr MELT = PanelText.tr("crucible.melt", "Melt", "Расплав"),
            CRUCIBLE_FUEL = PanelText.tr("crucible.fuel", "Fuel in the hearth", "Топливо в горне"),
            MOLTEN = PanelText.tr("crucible.molten", "Molten", "Расплавлено"),
            METAL_SLOT = PanelText.tr("crucible.metal_slot", "Metal: copper, bronze or aluminium ingot",
                    "Металл: слиток меди, бронзы или алюминия"),
            COAL_SLOT = PanelText.tr("crucible.coal_slot", "Fuel: coal, coke, charcoal", "Топливо: уголь, кокс, древесный уголь"),
            CRUCIBLE_STATUS = PanelText.tr("crucible.status", "%s %s kg · pours into a mould in front", "%s %s кг · разлив в форму перед печью"),
            CRUCIBLE_EMPTY = PanelText.tr("crucible.empty", "Crucible empty", "Тигель пуст");

    private static final Tr JIG_WATER = PanelText.tr("jig.water", "Make-up water", "Подпиточная вода"),
            JIG_FEED = PanelText.tr("jig.feed", "Raw coal in the bed", "Рядовой уголь в постели"),
            JIG_CUT = PanelText.tr("jig.cut", "Cut density", "Плотность"),
            JIG_CUT_TIP = PanelText.tr("jig.cut_tip", "Next clean-coal cut density (also: Create wrench)",
                    "Следующая плотность разделения концентрата (или ключ Create)"),
            JIG_STATUS = PanelText.tr("jig.status", "Cut %s / %s g/cm³", "Разделение %s / %s г/см³"),
            JIG_IN = PanelText.tr("jig.in", "Raw coal", "Рядовой уголь"),
            JIG_CLEAN = PanelText.tr("jig.clean", "Clean coal", "Концентрат"),
            JIG_MID = PanelText.tr("jig.middlings", "Middlings", "Промпродукт"),
            JIG_REFUSE = PanelText.tr("jig.refuse", "Refuse", "Отходы");
    private static final Tr DMS_MEDIUM = PanelText.tr("dms.medium", "Magnetite medium in the circuit", "Магнетитовая суспензия в контуре"),
            DMS_STATUS = PanelText.tr("dms.status", "Cut %s g/cm³ · magnetite %s kg", "Разделение %s г/см³ · магнетит %s кг"),
            DMS_MAGNETITE = PanelText.tr("dms.magnetite", "Magnetite: iron ore concentrate", "Магнетит: железорудный концентрат");

    private static final Tr SAND = PanelText.tr("molding.sand", "Foundry sand", "Формовочная смесь"),
            PATTERN = PanelText.tr("molding.pattern", "Casting pattern (not used up)", "Модель отливки (не расходуется)"),
            MOLD_OUT = PanelText.tr("molding.out", "Rammed mould", "Готовая форма"),
            WATER = PanelText.tr("molding.water", "Water", "Вода"),
            CYCLE = PanelText.tr("cycle", "Cycle", "Цикл"),
            MOLDING_STATUS = PanelText.tr("molding.status", "%s · sand %s / %s", "%s · смесь %s / %s"),
            NO_PATTERN = PanelText.tr("molding.no_pattern", "Put a pattern in", "Вставьте модель");

    private static final Tr READY = PanelText.tr("shakeout.ready", "Moulds ready on the grate: %s", "Готово форм на решётке: %s"),
            CASTINGS = PanelText.tr("shakeout.out", "Castings and returned sand", "Отливки и возвратная смесь");

    private static final Tr SPINDLE = PanelText.tr("tool.spindle", "Spindle", "Шпиндель"),
            PROGRESS = PanelText.tr("tool.progress", "Cut", "Обработка"),
            WEAR = PanelText.tr("tool.wear", "Tool wear", "Износ резца"),
            WORK_SLOT = PanelText.tr("tool.work_slot", "Casting to machine", "Отливка на обработку"),
            TOOL_SLOT = PanelText.tr("tool.tool_slot", "Cutting tool", "Резец"),
            DONE_SLOT = PanelText.tr("tool.done_slot", "Machined casting", "Обработанная отливка"),
            TOOL_STATUS = PanelText.tr("tool.status", "%s · spindle %s rpm", "%s · шпиндель %s об/мин"),
            TOOL_IDLE = PanelText.tr("tool.idle", "No workpiece", "Нет заготовки");

    /** Фон — art/blockbench/foundry/cupola_furnace.author.js ({@code guiCupola}). */
    public static final PanelLayout CUPOLA = PanelLayout.builder("cupola_furnace", 200, 150, C_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/cupola_furnace.png"))
            .dial(8, 24, true, C_WELL_C, 1, 1600, Double.NaN, 0, CELSIUS.get(), WELL.get(), null)
            .dial(80, 24, false, C_AIR_NM3H, 1, 1200, Double.NaN, 0, NM3H.get(), BLAST.get(), null)
            .dial(140, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(80, 86, FLAGS, F_RUNNING, LampColor.AMBER, LIT.get())
            .lamp(140, 86, FLAGS, F_HELD, LampColor.RED, TAP.get())
            .bar(8, 112, 88, C_IRON_KG, 1, CupolaUnit.STACK_CAPACITY_KG, 0xFF9AA0A6, STACK_IRON.get(),
                    v -> KG_OF.get(v.raw(C_IRON_KG), PanelText.num(CupolaUnit.STACK_CAPACITY_KG, 0)))
            .bar(104, 112, 88, C_WELL_KG10, 10, CupolaUnit.WELL_CAPACITY_KG, 0xFFF08020, WELL_METAL.get(),
                    v -> KG_OF.get(v.num(C_WELL_KG10, 10, 0), PanelText.num(CupolaUnit.WELL_CAPACITY_KG, 0)))
            .readout(8, 124, 128, v -> CUPOLA_STATUS.get(v.raw(C_MELT_KGH)),
                    v -> STACK_COKE_TIP.get(v.raw(C_COKE_KG)))
            .slot(146, 126, 0, s -> s.is(FoundryTags.CUPOLA_IRON), CUPOLA_IRON.get())
            .slot(170, 126, 1, s -> s.is(FoundryTags.CUPOLA_COKE), CUPOLA_COKE.get())
            .build();

    /** Фон — art/blockbench/foundry/crucible_furnace.author.js ({@code guiCrucible}). */
    public static final PanelLayout CRUCIBLE = PanelLayout.builder("crucible_furnace", 200, 150, T_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/crucible_furnace.png"))
            .dial(8, 24, true, T_TEMP_C, 1, 1600, Double.NaN, 0, CELSIUS.get(), MELT.get(), null)
            .lamp(84, 26, FLAGS, F_RUNNING, LampColor.AMBER, LIT.get())
            .lamp(84, 42, FLAGS, F_HELD, LampColor.RED, TAP.get())
            .bar(84, 72, 108, T_LIQUID_PERMIL, 10, 100, 0xFFF08020, MOLTEN.get(), v -> Component.literal(v.num(T_LIQUID_PERMIL, 10, 0) + " %"))
            .bar(84, 96, 108, T_FUEL_KG10, 10, CrucibleUnit.FUEL_CAPACITY_KG, 0xFF5A5A5A, CRUCIBLE_FUEL.get(),
                    v -> KG_OF.get(v.num(T_FUEL_KG10, 10, 0), PanelText.num(CrucibleUnit.FUEL_CAPACITY_KG, 0)))
            .readout(8, 124, 128, FoundryPanels::crucibleStatus, null)
            .slot(146, 126, 0, s -> CrucibleBlockEntity.alloyOf(s) != null, METAL_SLOT.get())
            .slot(170, 126, 1, s -> s.is(FoundryTags.FURNACE_COAL), COAL_SLOT.get())
            .build();

    /** Фон — art/blockbench/coal_prep/coal_jig.author.js ({@code guiJig}). */
    public static final PanelLayout COAL_JIG = PanelLayout.builder("coal_jig", 200, 150, J_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/coal_jig.png"))
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 88, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 34, 126, J_WATER_L, 1, CoalJig.WATER_CAPACITY_L, 0xFF3F78B8, JIG_WATER.get(),
                    v -> L_OF.get(v.raw(J_WATER_L), PanelText.num(CoalJig.WATER_CAPACITY_L, 0)))
            .bar(66, 58, 126, J_FEED_KG, 1, 1000, 0xFF404040, JIG_FEED.get(), v -> KG.get(v.raw(J_FEED_KG)))
            .readout(66, 72, 126, v -> JIG_STATUS.get(v.num(J_RHO_CLEAN, 100, 2), v.num(J_RHO_REFUSE, 100, 2)), null)
            .button(66, 90, 60, BUTTON_JIG_CUT, JIG_CUT.get(), JIG_CUT_TIP.get())
            .slot(16, 120, 0, s -> s.is(FoundryTags.JIG_FEED), JIG_IN.get())
            .output(104, 120, 1, JIG_CLEAN.get())
            .output(128, 120, 2, JIG_MID.get())
            .output(152, 120, 3, JIG_REFUSE.get())
            .caption(40, 125, Component.literal("→"))
            .build();

    /** Тяжелосредный сепаратор: без фона, суспензия — вода и магнетит, плотность — разделение (08 §8.3). */
    public static final PanelLayout DENSE_MEDIUM = PanelLayout.builder("dense_medium_separator", 200, 150, D_CHANNELS)
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 88, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 34, 126, D_MEDIUM_L, 1, DenseMediumSeparator.MEDIUM_CAPACITY_L, 0xFF2E2E34, DMS_MEDIUM.get(),
                    v -> L_OF.get(v.raw(D_MEDIUM_L), PanelText.num(DenseMediumSeparator.MEDIUM_CAPACITY_L, 0)))
            .bar(66, 58, 126, D_FEED_KG, 1, 1000, 0xFF404040, JIG_FEED.get(), v -> KG.get(v.raw(D_FEED_KG)))
            .readout(66, 72, 126, v -> DMS_STATUS.get(v.num(D_RHO100, 100, 2), v.raw(D_MAGNETITE_KG)), null)
            .slot(16, 120, 0, s -> s.is(FoundryTags.JIG_FEED), JIG_IN.get())
            .slot(40, 120, 1, s -> s.is(PcItems.IRON_ORE_CONCENTRATE.get()), DMS_MAGNETITE.get())
            .output(128, 120, 2, JIG_CLEAN.get())
            .output(152, 120, 3, JIG_REFUSE.get())
            .caption(70, 125, Component.literal("→"))
            .build();

    /** Фон — art/blockbench/foundry/molding.author.js ({@code paintGui}). */
    public static final PanelLayout MOLDING = PanelLayout.builder("molding_station", 200, 150, M_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/molding_station.png"))
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 88, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 34, 126, M_WATER_MB, 1, 4000, 0xFF3F78B8, WATER.get(), v -> MB.get(v.raw(M_WATER_MB)))
            .bar(66, 58, 126, M_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, CYCLE.get(), null)
            .readout(66, 72, 126, FoundryPanels::moldingStatus, null)
            .slot(16, 120, 0, s -> s.is(PcItems.FOUNDRY_SAND.get()), SAND.get())
            .slot(40, 120, 1, s -> s.is(PcItems.CASTING_PATTERN.get()), PATTERN.get())
            .caption(70, 125, Component.literal("→"))
            .output(104, 120, 2, MOLD_OUT.get())
            .build();

    /** Фон — art/blockbench/foundry/molding.author.js ({@code paintGui}). */
    public static final PanelLayout SHAKEOUT = PanelLayout.builder("shakeout_grate", 200, 150, K_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/shakeout_grate.png"))
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 88, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 34, 126, K_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, CYCLE.get(), null)
            .readout(66, 48, 126, v -> READY.get(v.raw(K_READY)), null)
            .output(84, 72, 0, CASTINGS.get()).output(102, 72, 1, CASTINGS.get()).output(120, 72, 2, CASTINGS.get())
            .output(84, 90, 3, CASTINGS.get()).output(102, 90, 4, CASTINGS.get()).output(120, 90, 5, CASTINGS.get())
            .output(84, 108, 6, CASTINGS.get()).output(102, 108, 7, CASTINGS.get()).output(120, 108, 8, CASTINGS.get())
            .build();

    private static final Tr FEED = PanelText.tr("shop.feed", "Feed", "Подача"),
            FEED_TIP = PanelText.tr("shop.feed_tip", "Next step of the feed gearbox: finer feed — cleaner surface, longer cut",
                    "Следующая ступень коробки подач: мельче подача — чище поверхность, дольше обработка"),
            CUTTER = PanelText.tr("shop.cutter", "Cutter", "Фреза"),
            STROKES = PanelText.tr("shop.strokes", "Strokes", "Ходы"),
            WHEEL = PanelText.tr("shop.wheel", "Wheel", "Круг"),
            DSPM = PanelText.tr("unit.dspm", "str/min", "дв.х/мин"),
            LOADING = PanelText.tr("shop.loading", "Wheel loading", "Засаливание круга"),
            BURN = PanelText.tr("shop.burn", "Burn", "Прижог"),
            SHOP_STATUS = PanelText.tr("shop.status", "%s · f %s mm · Ra %s µm · %s W", "%s · f %s мм · Ra %s мкм · %s Вт"),
            SHOP_IDLE = PanelText.tr("shop.idle", "No workpiece · f %s mm", "Нет заготовки · f %s мм"),
            LAST_OK = PanelText.tr("shop.last_ok", "Last part: Ra %s µm, IT%s", "Последняя деталь: Ra %s мкм, IT%s"),
            LAST_ROUGH = PanelText.tr("shop.last_rough", "Scrap: too rough, Ra %s µm", "Брак: грубо, Ra %s мкм"),
            LAST_BURN = PanelText.tr("shop.last_burn", "Scrap: grinding burn", "Брак: прижог"),
            LAST_DEFECT = PanelText.tr("shop.last_defect", "Scrap: casting defect opened up", "Брак: вскрылся литейный дефект"),
            BLANK_SLOT = PanelText.tr("shop.blank_slot", "Blank: casting, bar or plate", "Заготовка: отливка, пруток или лист"),
            CUTTER_SLOT = PanelText.tr("shop.cutter_slot", "Disc milling cutter", "Дисковая фреза"),
            WHEEL_SLOT = PanelText.tr("shop.wheel_slot", "Grinding wheel", "Шлифовальный круг"),
            PART_SLOT = PanelText.tr("shop.part_slot", "Finished part or scrap", "Готовая деталь или лом");

    public static final PanelLayout LATHE = tool("machine_lathe", 800);
    public static final PanelLayout DRILL_PRESS = tool("drill_press", 1200);
    public static final PanelLayout MILLING = production("milling_machine", 512, RPM.get(), CUTTER.get(), WEAR.get(),
            CUTTER_SLOT.get(), PcItems.MILLING_CUTTER);
    public static final PanelLayout SHAPER = production("shaping_machine", 128, DSPM.get(), STROKES.get(), WEAR.get(),
            TOOL_SLOT.get(), PcItems.CUTTING_TOOL);
    public static final PanelLayout GRINDER = production("surface_grinder", 6000, RPM.get(), WHEEL.get(), LOADING.get(),
            WHEEL_SLOT.get(), PcItems.GRINDING_WHEEL);

    private FoundryPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CUPOLA, CRUCIBLE, COAL_JIG, DENSE_MEDIUM, MOLDING, SHAKEOUT, LATHE, DRILL_PRESS, MILLING, SHAPER, GRINDER);
    }

    /** Фоны станков — art/blockbench/machine_shop/machine_tools.author.js ({@code guiTool}). */
    private static PanelLayout tool(String id, double spindleMax) {
        return PanelLayout.builder(id, 200, 150, L_CHANNELS)
                .art(PromptCritical.id("textures/gui/panel/" + id + ".png"))
                .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
                .dial(66, 24, false, L_SPINDLE_RPM, 1, spindleMax, Double.NaN, 0, RPM.get(), SPINDLE.get(), null)
                .lamp(124, 26, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
                .bar(124, 56, 68, L_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, PROGRESS.get(),
                        v -> Component.literal(v.num(L_PROGRESS_PERMIL, 10, 0) + " %"))
                .bar(124, 80, 68, L_WEAR_PERMIL, 10, 100, 0xFFC04030, WEAR.get(),
                        v -> Component.literal(v.raw(L_WEAR_PERMIL) < 0 ? "—" : v.num(L_WEAR_PERMIL, 10, 0) + " %"))
                .readout(8, 98, 184, FoundryPanels::toolStatus, null)
                .slot(16, 124, 0, s -> s.has(FoundryComponents.CASTING_SPEC.get()), WORK_SLOT.get())
                .slot(40, 124, 1, s -> s.is(PcItems.CUTTING_TOOL.get()), TOOL_SLOT.get())
                .caption(70, 129, Component.literal("→"))
                .output(104, 124, 2, DONE_SLOT.get())
                .build();
    }

    /** Щит станка парка: тахометр вала и шпинделя, лампы работы и прижога, ход детали, износ, кнопка подачи, слоты. */
    private static PanelLayout production(String id, double speedMax, Component unit, Component speedName, Component wearName,
            Component toolSlot, java.util.function.Supplier<? extends net.minecraft.world.item.Item> tool) {
        return PanelLayout.builder(id, 200, 150, P_CHANNELS)
                .art(PromptCritical.id("textures/gui/panel/" + id + ".png"))
                .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
                .dial(66, 24, false, P_SPEED, 1, speedMax, Double.NaN, 0, unit, speedName, null)
                .lamp(124, 26, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
                .lamp(160, 26, FLAGS, F_HELD, LampColor.RED, BURN.get())
                .bar(124, 56, 68, P_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, PROGRESS.get(),
                        v -> Component.literal(v.num(P_PROGRESS_PERMIL, 10, 0) + " %"))
                .bar(124, 80, 68, P_WEAR_PERMIL, 10, 100, 0xFFC04030, wearName,
                        v -> Component.literal(v.raw(P_WEAR_PERMIL) < 0 ? "—" : v.num(P_WEAR_PERMIL, 10, 0) + " %"))
                .readout(8, 92, 184, FoundryPanels::shopStatus, FoundryPanels::shopLast)
                .button(8, 104, 60, BUTTON_FEED, FEED.get(), FEED_TIP.get())
                .slot(16, 124, 0, s -> ProductionToolBlockEntity.workpiece(s) != null, BLANK_SLOT.get())
                .slot(40, 124, 1, s -> s.is(tool.get()), toolSlot)
                .caption(70, 129, Component.literal("→"))
                .output(104, 124, 2, PART_SLOT.get())
                .build();
    }

    private static Component shopStatus(PanelValues v) {
        String feed = v.num(P_FEED_UM, 1000, 3);
        int p = v.raw(P_PART);
        MachinedPart[] parts = MachinedPart.values();
        if (p < 0 || p >= parts.length) return SHOP_IDLE.get(feed);
        String ra = v.raw(P_RA100) < 0 ? "—" : v.num(P_RA100, 100, 2);
        return SHOP_STATUS.get(FoundryText.part(parts[p]), feed, ra, v.raw(P_POWER_W));
    }

    private static Component shopLast(PanelValues v) {
        int r = v.raw(P_LAST_REJECT);
        String ra = v.num(P_LAST_RA100, 100, 2);
        return switch (r) {
            case 0 -> LAST_OK.get(ra, v.raw(P_LAST_IT));
            case 1 -> LAST_DEFECT.get();
            case 2 -> LAST_ROUGH.get(ra);
            case 3 -> LAST_BURN.get();
            default -> Component.empty();
        };
    }

    private static PanelLayout.Tip shaftTip() {
        return v -> SHAFT_TIP.get(v.num(SHAFT_RPM10, 10, 0));
    }

    private static Component crucibleStatus(PanelValues v) {
        int a = v.raw(T_ALLOY);
        if (a < 0 || a >= CastAlloy.values().length) return CRUCIBLE_EMPTY.get();
        return CRUCIBLE_STATUS.get(FoundryText.alloy(CastAlloy.values()[a].id), v.num(T_MASS_KG10, 10, 0));
    }

    private static Component moldingStatus(PanelValues v) {
        int k = v.raw(M_KIND);
        if (k < 0 || k >= CastingKind.values().length) return NO_PATTERN.get().withStyle(ChatFormatting.GOLD);
        return MOLDING_STATUS.get(FoundryText.kind(CastingKind.values()[k]), v.raw(M_SAND), v.raw(M_NEED));
    }

    private static Component toolStatus(PanelValues v) {
        int k = v.raw(L_KIND);
        if (k < 0 || k >= CastingKind.values().length) return TOOL_IDLE.get();
        return TOOL_STATUS.get(FoundryText.kind(CastingKind.values()[k]), v.raw(L_SPINDLE_RPM));
    }
}
