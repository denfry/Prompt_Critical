package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Щиты подготовки и углубления переработки НПЗ (09 §9.7.3, §9.9.2–9.9.5, §9.9.13–9.9.14, §9.10.3): ЭЛОУ, вакуумная
 * колонна, каткрекинг, аминовая очистка, станция смешения бензина, лаборатория и поточный анализатор. Подписи приборов
 * — имена тегов периферии CC.
 */
public final class ConversionPanels {
    /** Общие биты флагов и кнопки. */
    public static final int F_ON = 0, F_RUN = 1, F_WARN = 2, F_AUX = 3, F_ALARM = 4, F_LOW = 5;
    public static final int B_RUN = 0, B_DOWN = 1, B_UP = 2, B_DOWN2 = 3, B_UP2 = 4, B_ACTION = 5, B_ACTION2 = 6;

    private static final Tr RUN = PanelText.tr("conv.run", "Run", "Пуск/стоп"), ON = PanelText.tr("conv.on", "On", "Вкл."),
            WORKING = PanelText.tr("conv.working", "Working", "Работа"), PCT = PanelText.tr("conv.pct", "%", "%");

    // ------------------------------------------------------------------ ЭЛОУ (09 §9.7.3)
    public static final int DS_SALT_X100 = 0, DS_WATER_X100 = 1, DS_DP_KPA = 2, DS_FEED_LH = 3, DS_TANK = 4, DS_STAGE1_X10 = 5,
            DS_FLAGS = 6, DS_CHANNELS = 7;
    private static final Tr MGL = PanelText.tr("conv.mgl", "mg/l", "мг/л"), SALT = PanelText.tr("conv.salt_out", "Salt out", "Соль на выходе"),
            WATER = PanelText.tr("conv.water_out", "Water in crude", "Вода в нефти"), KPA = PanelText.tr("conv.kpa", "kPa", "кПа"),
            DP = PanelText.tr("conv.mix_dp", "Mixing valve", "Смесительный клапан"),
            BRINE = PanelText.tr("conv.brine", "Brine", "Солёная вода"),
            FIELD = PanelText.tr("conv.field", "Electrodes energised", "Электроды под напряжением"),
            SALT_HIGH = PanelText.tr("conv.salt_high", "Salt above 5 mg/l", "Соль выше 5 мг/л"),
            NO_FURNACE = PanelText.tr("conv.no_furnace", "No tube furnace within 6 blocks", "Нет трубчатой печи в 6 блоках"),
            DP_DOWN = PanelText.tr("conv.dp_down", "Valve Δp −10 kPa", "Перепад клапана −10 кПа"),
            DP_UP = PanelText.tr("conv.dp_up", "Valve Δp +10 kPa", "Перепад клапана +10 кПа"),
            DESALTER_STATUS = PanelText.tr("conv.desalter_status", "Crude %s l/h · after stage 1 %s mg/l · water %s %%",
                    "Нефть %s л/ч · после 1-й ступени %s мг/л · вода %s %%");

    public static final PanelLayout DESALTER = PanelLayout.builder("electrostatic_desalter", 232, 140, DS_CHANNELS)
            .dial(8, 24, false, DS_SALT_X100, 100, 20, 5, 1, MGL.get(), SALT.get(), null)
            .dial(64, 24, false, DS_WATER_X100, 100, 2, 0.5, 2, PCT.get(), WATER.get(), null)
            .dial(120, 24, false, DS_DP_KPA, 1, 160, Double.NaN, 0, KPA.get(), DP.get(), null)
            .glass(176, 24, 56, DS_TANK, 0xFF4A6A8A, Double.NaN, Double.NaN, BRINE.get(), null)
            .lamp(196, 22, DS_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(196, 36, DS_FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(196, 50, DS_FLAGS, F_AUX, LampColor.GREEN, FIELD.get()).lamp(196, 64, DS_FLAGS, F_WARN, LampColor.AMBER, SALT_HIGH.get())
            .lamp(196, 78, DS_FLAGS, F_ALARM, LampColor.RED, NO_FURNACE.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_DOWN, Component.literal("−"), DP_DOWN.get()).button(78, 92, 20, B_UP, Component.literal("+"), DP_UP.get())
            .readout(8, 118, 216, v -> DESALTER_STATUS.get(v.raw(DS_FEED_LH), v.num(DS_STAGE1_X10, 10, 1), v.num(DS_WATER_X100, 100, 2)), null)
            .noInventory().build();

    // ------------------------------------------------------------------ вакуумная колонна (09 §9.9.2)
    public static final int VC_T_C = 0, VC_P_X10 = 1, VC_VGO_PERMIL = 2, VC_CCR_PERMIL = 3, VC_TANK = 4, VC_FEED_LH = 5, VC_FLASH_C = 6,
            VC_EJECTORS = 7, VC_FLAGS = 8, VC_CHANNELS = 9;
    private static final Tr HEATER = PanelText.tr("conv.heater", "Heater outlet", "Выход печи"),
            FLASH_P = PanelText.tr("conv.flash_p", "Flash zone", "Зона питания"),
            VGO_YIELD = PanelText.tr("conv.vgo_yield", "Gas oil yield", "Выход газойля"),
            VGO = PanelText.tr("conv.vgo", "Vacuum gas oil", "Вакуумный газойль"),
            VACUUM_OK = PanelText.tr("conv.vacuum_ok", "Vacuum held", "Вакуум есть"),
            NO_VACUUM = PanelText.tr("conv.no_vacuum", "No vacuum: ejectors or feedwater missing", "Нет вакуума: нет эжекторов или воды"),
            T_DOWN = PanelText.tr("conv.t_down", "−5 °C", "−5 °C"), T_UP = PanelText.tr("conv.t_up", "+5 °C", "+5 °C"),
            VACUUM_STATUS = PanelText.tr("conv.vacuum_status", "Mazut %s l/h · cut to %s °C · tar CCR %s %% · ejector stages %s",
                    "Мазут %s л/ч · выкипает до %s °C · коксуемость гудрона %s %% · ступеней эжекторов %s");

    public static final PanelLayout VACUUM = PanelLayout.builder("vacuum_column", 232, 140, VC_CHANNELS)
            .dial(8, 24, false, VC_T_C, 1, 450, 420, 0, PanelText.CELSIUS.get(), HEATER.get(), null)
            .dial(64, 24, false, VC_P_X10, 10, 110, 20, 0, KPA.get(), FLASH_P.get(), null)
            .dial(120, 24, false, VC_VGO_PERMIL, 10, 100, Double.NaN, 0, PCT.get(), VGO_YIELD.get(), null)
            .glass(176, 24, 56, VC_TANK, 0xFF4A3420, Double.NaN, Double.NaN, VGO.get(), null)
            .lamp(196, 22, VC_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(196, 36, VC_FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(196, 50, VC_FLAGS, F_AUX, LampColor.GREEN, VACUUM_OK.get()).lamp(196, 64, VC_FLAGS, F_WARN, LampColor.AMBER, NO_VACUUM.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_DOWN, Component.literal("−"), T_DOWN.get()).button(78, 92, 20, B_UP, Component.literal("+"), T_UP.get())
            .readout(8, 118, 216, v -> VACUUM_STATUS.get(v.raw(VC_FEED_LH), v.raw(VC_FLASH_C), v.num(VC_CCR_PERMIL, 10, 1), v.raw(VC_EJECTORS)), null)
            .noInventory().build();

    // ------------------------------------------------------------------ каткрекинг (09 §9.9.5)
    public static final int FC_RISER_C = 0, FC_REGEN_C = 1, FC_C2O_X10 = 2, FC_CONV_PERMIL = 3, FC_ACT_PERMIL = 4, FC_TANK = 5,
            FC_INV_KG = 6, FC_CRC_X1000 = 7, FC_AIR_X1000 = 8, FC_DAMPER_PCT = 9, FC_DP_KPA = 10, FC_FEED_LH = 11, FC_FLAGS = 12,
            FC_CHANNELS = 13;
    private static final Tr RISER = PanelText.tr("conv.riser", "Riser outlet", "Выход райзера"),
            REGEN = PanelText.tr("conv.regen", "Regenerator", "Регенератор"),
            C2O = PanelText.tr("conv.c2o", "Cat/oil", "Катализатор/сырьё"), RATIO = PanelText.tr("conv.ratio", "kg/kg", "кг/кг"),
            CONVERSION = PanelText.tr("conv.conversion", "Conversion", "Конверсия"),
            FCC_GASOLINE = PanelText.tr("conv.fcc_gasoline", "FCC gasoline", "Бензин каткрекинга"),
            NO_BLOWER = PanelText.tr("conv.no_blower", "No air blower within 8 blocks", "Нет воздуходувки в 8 блоках"),
            SLIDE_TRIP = PanelText.tr("conv.slide_trip", "Slide valves shut: Δp low (Run resets)", "Задвижки закрыты: мал перепад (Пуск — сброс)"),
            AFTERBURN = PanelText.tr("conv.afterburn", "Afterburn above 760 °C", "Догорание выше 760 °C"),
            CAT_LOW = PanelText.tr("conv.cat_low", "Catalyst charge below half", "Катализатора меньше половины"),
            AIR_DOWN = PanelText.tr("conv.air_down", "Air −", "Воздух −"), AIR_UP = PanelText.tr("conv.air_up", "Air +", "Воздух +"),
            AIR_DOWN_TIP = PanelText.tr("conv.air_down_tip", "Air damper −5 %", "Заслонка воздуха −5 %"),
            AIR_UP_TIP = PanelText.tr("conv.air_up_tip", "Air damper +5 %", "Заслонка воздуха +5 %"),
            FCC_CATALYST = PanelText.tr("conv.fcc_catalyst", "Zeolite catalyst, 100 kg bags", "Цеолитный катализатор, мешки по 100 кг"),
            ECAT = PanelText.tr("conv.ecat", "Withdrawn equilibrium catalyst", "Выведенный равновесный катализатор"),
            FCC_STATUS = PanelText.tr("conv.fcc_status", "Gas oil %s l/h · catalyst %s kg, activity %s %% · coke on regenerated %s %%",
                    "Газойль %s л/ч · катализатор %s кг, активность %s %% · кокс на регенерированном %s %%"),
            AIR_STATUS = PanelText.tr("conv.air_status", "Air %s kg/s (damper %s %%) · slide valve Δp %s kPa",
                    "Воздух %s кг/с (заслонка %s %%) · перепад на задвижке %s кПа"),
            CATALYST_TIP = PanelText.tr("conv.catalyst_state", "Activity %s %% · coke %s %% · %s h in service · %s",
                    "Активность %s %% · кокс %s %% · %s ч в работе · %s");
    private static final Tr[] STAGES = {PanelText.tr("conv.stage.fresh", "fresh", "свежий"),
            PanelText.tr("conv.stage.oxide", "oxide form", "оксидная форма"), PanelText.tr("conv.stage.active", "in service", "в работе"),
            PanelText.tr("conv.stage.spent", "spent", "отработанный"), PanelText.tr("conv.stage.regenerating", "regenerating", "на регенерации")};

    /** Подсказка мешка катализатора с {@code catalyst_state}. */
    public static net.minecraft.network.chat.MutableComponent catalystTip(dev.denfry.promptcritical.sim.refining.CatalystState s) {
        return CATALYST_TIP.get(PanelText.num(s.activity() * 100, 0), PanelText.num(s.cokeFrac() * 100, 2), PanelText.num(s.ageH(), 0),
                STAGES[s.state().ordinal()].get());
    }

    public static final PanelLayout FCC = PanelLayout.builder("fcc_unit", 300, 168, FC_CHANNELS)
            .dial(8, 24, false, FC_RISER_C, 1, 600, 545, 0, PanelText.CELSIUS.get(), RISER.get(), null)
            .dial(64, 24, false, FC_REGEN_C, 1, 800, 760, 0, PanelText.CELSIUS.get(), REGEN.get(), null)
            .dial(120, 24, false, FC_C2O_X10, 10, 12, 10, 1, RATIO.get(), C2O.get(), null)
            .dial(176, 24, false, FC_CONV_PERMIL, 10, 100, Double.NaN, 0, PCT.get(), CONVERSION.get(), null)
            .glass(232, 24, 64, FC_TANK, 0xFFE0A040, Double.NaN, Double.NaN, FCC_GASOLINE.get(), null)
            .lamp(256, 22, FC_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(256, 36, FC_FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(256, 50, FC_FLAGS, F_AUX, LampColor.AMBER, NO_BLOWER.get()).lamp(256, 64, FC_FLAGS, F_WARN, LampColor.RED, SLIDE_TRIP.get())
            .lamp(256, 78, FC_FLAGS, F_ALARM, LampColor.RED, AFTERBURN.get()).lamp(256, 92, FC_FLAGS, F_LOW, LampColor.AMBER, CAT_LOW.get())
            .button(8, 108, 44, B_RUN, RUN.get(), null)
            .button(56, 108, 20, B_DOWN, Component.literal("−"), T_DOWN.get()).button(78, 108, 20, B_UP, Component.literal("+"), T_UP.get())
            .button(102, 108, 40, B_DOWN2, AIR_DOWN.get(), AIR_DOWN_TIP.get()).button(146, 108, 40, B_UP2, AIR_UP.get(), AIR_UP_TIP.get())
            .slot(196, 108, 0, s -> s.is(dev.denfry.promptcritical.mod.registry.PcItems.CATALYST_FCC.get()), FCC_CATALYST.get())
            .output(216, 108, 1, ECAT.get()).output(234, 108, 2, ECAT.get())
            .readout(8, 134, 284, v -> FCC_STATUS.get(v.raw(FC_FEED_LH), v.raw(FC_INV_KG), v.num(FC_ACT_PERMIL, 10, 1),
                    v.num(FC_CRC_X1000, 1000, 3)), null)
            .readout(8, 148, 284, v -> AIR_STATUS.get(v.num(FC_AIR_X1000, 1000, 2), v.raw(FC_DAMPER_PCT), v.raw(FC_DP_KPA)), null).build();

    // ------------------------------------------------------------------ аминовая очистка (09 §9.10.3)
    public static final int AM_SWEET_X10 = 0, AM_RICH_PERMIL = 1, AM_Q_X100 = 2, AM_STEAM = 3, AM_GAS_NM3H = 4, AM_H2S_KGH10 = 5,
            AM_PURITY_PERMIL = 6, AM_LEAN_X1000 = 7, AM_HOLDUP = 8, AM_FLAGS = 9, AM_CHANNELS = 10;
    private static final Tr MGNM3 = PanelText.tr("conv.mgnm3", "mg/m³", "мг/м³"),
            SWEET = PanelText.tr("conv.sweet_h2s", "H₂S in gas", "H₂S в газе"),
            RICH = PanelText.tr("conv.rich_loading", "Rich loading", "Загрузка амина"), MOL = PanelText.tr("conv.mol_mol", "mol/mol", "моль/моль"),
            CIRC = PanelText.tr("conv.circulation", "Amine", "Амин"), M3H = PanelText.tr("conv.m3h", "m³/h", "м³/ч"),
            HOLDUP = PanelText.tr("conv.h2s_holdup", "Acid gas", "Кислый газ"),
            OFF_SPEC = PanelText.tr("conv.off_spec", "Sales gas above 20 mg/m³ H₂S", "Товарный газ выше 20 мг/м³ H₂S"),
            SATURATED = PanelText.tr("conv.saturated", "Amine saturated: raise circulation", "Амин насыщен: прибавьте циркуляцию"),
            NO_PLANT = PanelText.tr("conv.no_plant", "No gas plant within 6 blocks", "Нет установки подготовки газа в 6 блоках"),
            HOLDUP_FULL = PanelText.tr("conv.holdup_full", "Acid gas has nowhere to go: no Claus unit", "Кислый газ некуда сдать: нет Клауса"),
            Q_DOWN = PanelText.tr("conv.q_down", "Amine −0.25 m³/h", "Амин −0,25 м³/ч"), Q_UP = PanelText.tr("conv.q_up", "Amine +0.25 m³/h", "Амин +0,25 м³/ч"),
            STEAM_DOWN = PanelText.tr("conv.steam_down", "Steam −", "Пар −"), STEAM_UP = PanelText.tr("conv.steam_up", "Steam +", "Пар +"),
            STEAM_TIP = PanelText.tr("conv.steam_tip", "Reboiler: 10 kg of steam per m³ of amine", "Ребойлер: 10 кг пара на м³ амина"),
            AMINE_STATUS = PanelText.tr("conv.amine_status", "Gas %s m³/h · H₂S %s kg/h, %s %% pure · lean %s · steam %s kg/m³",
                    "Газ %s м³/ч · H₂S %s кг/ч, чистота %s %% · регенерированный %s · пар %s кг/м³");

    public static final PanelLayout AMINE = PanelLayout.builder("amine_unit", 232, 140, AM_CHANNELS)
            .dial(8, 24, false, AM_SWEET_X10, 10, 50, 20, 1, MGNM3.get(), SWEET.get(), null)
            .dial(64, 24, false, AM_RICH_PERMIL, 1000, 0.6, 0.5, 2, MOL.get(), RICH.get(), null)
            .dial(120, 24, false, AM_Q_X100, 100, 6, Double.NaN, 2, M3H.get(), CIRC.get(), null)
            .glass(176, 24, 56, AM_HOLDUP, 0xFFD8D070, Double.NaN, 900, HOLDUP.get(), null)
            .lamp(196, 22, AM_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(196, 36, AM_FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(196, 50, AM_FLAGS, F_ALARM, LampColor.RED, OFF_SPEC.get()).lamp(196, 64, AM_FLAGS, F_WARN, LampColor.AMBER, SATURATED.get())
            .lamp(196, 78, AM_FLAGS, F_AUX, LampColor.RED, NO_PLANT.get()).lamp(196, 92, AM_FLAGS, F_LOW, LampColor.AMBER, HOLDUP_FULL.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_DOWN, Component.literal("−"), Q_DOWN.get()).button(78, 92, 20, B_UP, Component.literal("+"), Q_UP.get())
            .button(102, 92, 40, B_DOWN2, STEAM_DOWN.get(), STEAM_TIP.get()).button(146, 92, 40, B_UP2, STEAM_UP.get(), STEAM_TIP.get())
            .readout(8, 118, 216, v -> AMINE_STATUS.get(v.raw(AM_GAS_NM3H), v.num(AM_H2S_KGH10, 10, 1), v.num(AM_PURITY_PERMIL, 10, 1),
                    v.num(AM_LEAN_X1000, 10000, 4), v.raw(AM_STEAM)), null)
            .noInventory().build();

    // ------------------------------------------------------------------ смешение бензина, лаборатория (09 §9.9.12–9.9.14)
    public static final int BL_RON_X10 = 0, BL_MON_X10 = 1, BL_RVP_X10 = 2, BL_TANK = 3, BL_V0 = 4, BL_SEL = 8, BL_GRADE = 9,
            BL_RATE_X100 = 10, BL_PASS_RON_X10 = 11, BL_FLAGS = 12, BL_CHANNELS = 13;
    public static final Tr NOTHING_TO_SAMPLE = PanelText.tr("conv.nothing_to_sample", "No gasoline or blending component here",
            "Здесь нет бензина или компонента смешения"),
            SAMPLED = PanelText.tr("conv.sampled", "Sample taken: %s", "Проба отобрана: %s"),
            EMPTY_BOTTLE = PanelText.tr("conv.empty_bottle", "Use on a blending station or a tank of gasoline components",
                    "ПКМ по станции смешения или баку с компонентом бензина"),
            NOT_ANALYSED = PanelText.tr("conv.not_analysed", "Not analysed: take it to the quality lab", "Не проанализирована: отнесите в лабораторию"),
            ON_GRADE = PanelText.tr("conv.on_grade", "Meets %s", "Соответствует: %s"),
            OFF_GRADE = PanelText.tr("conv.off_grade", "Fails %s", "Не соответствует: %s"),
            QUALITY_1 = PanelText.tr("conv.quality_1", "RON %s · MON %s · RVP %s kPa · S %s ppm", "ОЧИ %s · ОЧМ %s · ДНП %s кПа · S %s ppm"),
            QUALITY_2 = PanelText.tr("conv.quality_2", "Benzene %s %% · aromatics %s %% · olefins %s %% · %s kg/m³",
                    "Бензол %s %% · ароматика %s %% · олефины %s %% · %s кг/м³");
    private static final Tr[] GRADE_NAMES = {PanelText.tr("conv.grade.normal80", "Normal-80", "Нормаль-80"),
            PanelText.tr("conv.grade.ai92", "AI-92", "АИ-92")};
    private static final Tr[] COMPONENT_NAMES = {PanelText.tr("conv.comp.lsr", "straight-run", "прямогонный"),
            PanelText.tr("conv.comp.reformate", "reformate", "риформат"), PanelText.tr("conv.comp.fcc", "cracked", "каткрекинга"),
            PanelText.tr("conv.comp.butane", "butane", "бутан")};
    private static final Tr RON_DIAL = PanelText.tr("conv.ron", "RON", "ОЧИ"), MON_DIAL = PanelText.tr("conv.mon", "MON", "ОЧМ"),
            RVP_DIAL = PanelText.tr("conv.rvp", "RVP", "ДНП"), BLEND = PanelText.tr("conv.blend", "Motor gasoline", "Товарный бензин"),
            ANALYZER = PanelText.tr("conv.analyzer", "Analyzer attached", "Анализатор подключён"),
            OFF_SPEC_BLEND = PanelText.tr("conv.off_spec_blend", "Analyzer: blend off grade", "Анализатор: смесь не в марке"),
            CERT_OK = PanelText.tr("conv.cert_ok", "Batch certified on grade", "Партия с паспортом, в марке"),
            CERT_BAD = PanelText.tr("conv.cert_bad", "Batch certified off grade", "Паспорт: партия не в марке"),
            V_DOWN = PanelText.tr("conv.v_down", "Selected valve −0.1 m³/h", "Выбранный клапан −0,1 м³/ч"),
            V_UP = PanelText.tr("conv.v_up", "Selected valve +0.1 m³/h", "Выбранный клапан +0,1 м³/ч"),
            NEXT = PanelText.tr("conv.next", "Valve", "Клапан"), NEXT_TIP = PanelText.tr("conv.next_tip", "Select the next component valve",
                    "Выбрать клапан следующего компонента"),
            GRADE = PanelText.tr("conv.grade", "Grade", "Марка"), GRADE_TIP = PanelText.tr("conv.grade_tip", "Switch the grade the batch is made to",
                    "Переключить марку, на которую делают партию"),
            CAL = PanelText.tr("conv.cal", "Calibrate", "Калибровка"), CAL_TIP = PanelText.tr("conv.cal_tip",
                    "Zero the analyzer drift against a lab certificate newer than the last calibration",
                    "Обнулить дрейф анализатора по паспорту лаборатории, свежее последней калибровки"),
            VALVES = PanelText.tr("conv.valves", "Valves m³/h: %s %s · %s %s · %s %s · %s %s",
                    "Клапаны, м³/ч: %s %s · %s %s · %s %s · %s %s"),
            BLEND_STATUS = PanelText.tr("conv.blend_status", "%s · blending %s m³/h · selected: %s · certified RON %s",
                    "%s · смешение %s м³/ч · выбран: %s · ОЧИ по паспорту %s");

    public static Component gradeName(dev.denfry.promptcritical.sim.refining.GasolineBlending.Grade g) {
        return GRADE_NAMES[g.id().equals("ai92") ? 1 : 0].get();
    }

    /** Две строки качества {@code fuel_quality} для подсказки пробы и паспорта. */
    public static void qualityLines(RefineryComponents.FuelQuality q, List<Component> out) {
        out.add(QUALITY_1.get(PanelText.num(q.ron(), 1), PanelText.num(q.mon(), 1), PanelText.num(q.rvpKpa(), 1), PanelText.num(q.sulfurPpm(), 1))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        out.add(QUALITY_2.get(PanelText.num(q.benzenePct(), 1), PanelText.num(q.aromaticsPct(), 1), PanelText.num(q.olefinsPct(), 1),
                PanelText.num(q.density(), 0)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private static Component comp(int i) {
        return COMPONENT_NAMES[Math.clamp(i, 0, COMPONENT_NAMES.length - 1)].get();
    }

    public static final PanelLayout BLENDER = PanelLayout.builder("gasoline_blender", 300, 150, BL_CHANNELS)
            .dial(8, 24, false, BL_RON_X10, 10, 110, Double.NaN, 1, RON_DIAL.get(), RON_DIAL.get(), null)
            .dial(64, 24, false, BL_MON_X10, 10, 110, Double.NaN, 1, MON_DIAL.get(), MON_DIAL.get(), null)
            .dial(120, 24, false, BL_RVP_X10, 10, 100, 60, 0, KPA.get(), RVP_DIAL.get(), null)
            .glass(176, 24, 56, BL_TANK, 0xFFE8E070, Double.NaN, Double.NaN, BLEND.get(), null)
            .lamp(204, 22, BL_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(204, 36, BL_FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(204, 50, BL_FLAGS, F_AUX, LampColor.GREEN, ANALYZER.get()).lamp(204, 64, BL_FLAGS, F_WARN, LampColor.AMBER, OFF_SPEC_BLEND.get())
            .lamp(204, 78, BL_FLAGS, F_LOW, LampColor.GREEN, CERT_OK.get()).lamp(204, 92, BL_FLAGS, F_ALARM, LampColor.RED, CERT_BAD.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_DOWN, Component.literal("−"), V_DOWN.get()).button(78, 92, 20, B_UP, Component.literal("+"), V_UP.get())
            .button(102, 92, 40, B_DOWN2, NEXT.get(), NEXT_TIP.get()).button(146, 92, 40, B_UP2, GRADE.get(), GRADE_TIP.get())
            .button(8, 108, 56, B_ACTION, CAL.get(), CAL_TIP.get())
            .readout(8, 126, 284, v -> VALVES.get(comp(0), v.num(BL_V0, 100, 2), comp(1), v.num(BL_V0 + 1, 100, 2), comp(2),
                    v.num(BL_V0 + 2, 100, 2), comp(3), v.num(BL_V0 + 3, 100, 2)), null)
            .readout(8, 138, 284, v -> BLEND_STATUS.get(GRADE_NAMES[Math.clamp(v.raw(BL_GRADE), 0, 1)].get(), v.num(BL_RATE_X100, 100, 2),
                    comp(v.raw(BL_SEL)), v.num(BL_PASS_RON_X10, 10, 1)), null)
            .noInventory().build();

    public static final int LB_PROGRESS = 0, LB_RON_X10 = 1, LB_MON_X10 = 2, LB_RVP_X10 = 3, LB_S_X10 = 4, LB_BZ_X10 = 5, LB_ARO_X10 = 6,
            LB_OLE_X10 = 7, LB_RHO = 8, LB_FLAGS = 9, LB_CHANNELS = 10;
    private static final Tr PROGRESS = PanelText.tr("conv.progress", "Analysis", "Анализ"),
            RESULT = PanelText.tr("conv.result", "Last result ready", "Есть результат"),
            SAMPLE = PanelText.tr("conv.sample", "Sample bottle", "Проба"), DONE = PanelText.tr("conv.done", "Analysed sample, certificate", "Проба после анализа, паспорт"),
            LAB_1 = PanelText.tr("conv.lab_1", "RON %s · MON %s · RVP %s kPa · S %s ppm", "ОЧИ %s · ОЧМ %s · ДНП %s кПа · S %s ppm"),
            LAB_2 = PanelText.tr("conv.lab_2", "Benzene %s %% · aromatics %s %% · olefins %s %% · %s kg/m³",
                    "Бензол %s %% · ароматика %s %% · олефины %s %% · %s кг/м³");

    public static final PanelLayout LAB = PanelLayout.builder("quality_lab", 232, 120, LB_CHANNELS)
            .bar(8, 24, 140, LB_PROGRESS, 10, 100, 0xFF60A060, PROGRESS.get(), null)
            .lamp(176, 22, LB_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(176, 36, LB_FLAGS, F_RUN, LampColor.AMBER, WORKING.get())
            .lamp(176, 50, LB_FLAGS, F_AUX, LampColor.GREEN, RESULT.get())
            .button(8, 50, 44, B_RUN, RUN.get(), null)
            .slot(70, 50, 0, s -> s.is(dev.denfry.promptcritical.mod.registry.PcItems.SAMPLE_BOTTLE.get()), SAMPLE.get())
            .output(100, 50, 1, DONE.get()).output(118, 50, 2, DONE.get())
            .readout(8, 80, 216, v -> LAB_1.get(v.num(LB_RON_X10, 10, 1), v.num(LB_MON_X10, 10, 1), v.num(LB_RVP_X10, 10, 1),
                    v.num(LB_S_X10, 10, 1)), null)
            .readout(8, 94, 216, v -> LAB_2.get(v.num(LB_BZ_X10, 10, 1), v.num(LB_ARO_X10, 10, 1), v.num(LB_OLE_X10, 10, 1),
                    v.raw(LB_RHO)), null).build();

    private ConversionPanels() {
    }

    public static List<PanelLayout> all() {
        List<PanelLayout> out = new ArrayList<>();
        out.add(DESALTER);
        out.add(VACUUM);
        out.add(FCC);
        out.add(AMINE);
        out.add(BLENDER);
        out.add(LAB);
        return List.copyOf(out);
    }
}
