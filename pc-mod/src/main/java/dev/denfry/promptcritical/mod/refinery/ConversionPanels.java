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

    private ConversionPanels() {
    }

    public static List<PanelLayout> all() {
        List<PanelLayout> out = new ArrayList<>();
        out.add(DESALTER);
        out.add(VACUUM);
        out.add(FCC);
        out.add(AMINE);
        return List.copyOf(out);
    }
}
