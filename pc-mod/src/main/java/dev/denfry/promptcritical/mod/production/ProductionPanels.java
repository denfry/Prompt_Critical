package dev.denfry.promptcritical.mod.production;

import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.PERCENT;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.sim.production.BeamPump;
import java.util.List;

/** Местные щиты добычи и НПЗ: устье скважины с динамограммой, сепаратор, атмосферная колонна с печью. */
public final class ProductionPanels {
    // устье
    public static final int W_THP_X100 = 0, W_BHP_X100 = 1, W_PR_X100 = 2, W_OIL_M3D10 = 3, W_GOR = 4, W_WC_PERMIL = 5, W_CHOKE = 6,
            W_MODE = 7, W_FLAGS = 8, W_FILL_PERMIL = 9, W_PEAK_KN = 10, W_SPM10 = 11, W_CARD = 12,
            W_CHANNELS = W_CARD + 2 * BeamPump.CARD_POINTS;
    public static final int WF_OPEN = 0, WF_PUMPING = 1, WF_TREE = 2, WF_SEPARATOR = 3;
    public static final int WB_CHOKE_DOWN = 0, WB_CHOKE_UP = 1, WB_VALVE = 2;
    // сепаратор
    public static final int S_OIL_PERMIL = 0, S_WATER_PERMIL = 1, S_GAS_PERMIL = 2, S_BSW_X100 = 3, S_RET_S = 4, S_FLARE_M3H = 5,
            S_FLAGS = 6, S_CHANNELS = 7;
    public static final int SF_FLARING = 0, SF_CARRYOVER = 1, SF_FULL = 2;
    // колонна
    public static final int C_T_OUT = 0, C_FEED_M3H10 = 1, C_FUEL_M3H = 2, C_REFLUX_X10 = 3, C_TRAYS = 4, C_Y_GASOLINE = 5,
            C_Y_KEROSENE = 6, C_Y_DIESEL = 7, C_Y_RESIDUE = 8, C_OVERLAP_K = 9, C_FLAGS = 10, C_TANK0 = 11, C_SALT_X10 = 15,
            C_CORROSION_PERMIL = 16, C_CHANNELS = 17;
    public static final int CF_LIT = 0, CF_READY = 1, CF_LEAK = 2;
    public static final int CB_T_DOWN = 0, CB_T_UP = 1, CB_R_DOWN = 2, CB_R_UP = 3;

    private static final Tr M3D = PanelText.tr("unit.m3d", "m³/d", "м³/сут"), M3H = PanelText.tr("unit.m3h_prod", "m³/h", "м³/ч"),
            CELSIUS = PanelText.tr("unit.celsius_prod", "°C", "°C"), MIN = PanelText.tr("unit.min", "min", "мин"),
            THP = PanelText.tr("prod.thp", "Buffer", "Буфер"),
            THP_TIP = PanelText.tr("prod.thp_tip", "Tubing head pressure before the choke %s MPa", "Давление на буфере перед штуцером %s МПа"),
            BHP = PanelText.tr("prod.bhp", "Bottomhole", "Забой"),
            PR = PanelText.tr("prod.pr", "Reservoir", "Пласт"),
            PR_TIP = PanelText.tr("prod.pr_tip", "Average reservoir pressure %s MPa: falls as the field is produced",
                    "Среднее пластовое давление %s МПа: падает с отбором"),
            OIL = PanelText.tr("prod.oil", "Oil", "Нефть"),
            CARD = PanelText.tr("prod.card", "Dynamometer card", "Динамограмма"),
            CARD_TIP = PanelText.tr("prod.card_tip", "Polished rod load against position: peak %s kN, pump fillage %s %%, %s strokes/min",
                    "Нагрузка на полированный шток по ходу: максимум %s кН, заполнение насоса %s %%, %s кач/мин"),
            OPEN = PanelText.tr("prod.open", "Open", "Открыта"),
            PUMPING = PanelText.tr("prod.pumping", "Pump", "Насос"),
            TREE = PanelText.tr("prod.tree", "Tree", "Арматура"),
            SEP = PanelText.tr("prod.sep", "Separator", "Сепаратор"),
            CHOKE_DOWN = PanelText.tr("prod.choke_down", "Choke −", "Штуцер −"),
            CHOKE_UP = PanelText.tr("prod.choke_up", "Choke +", "Штуцер +"),
            VALVE = PanelText.tr("prod.valve", "Master", "Задвижка"),
            VALVE_TIP = PanelText.tr("prod.valve_tip", "Open or close the master valve", "Открыть или закрыть центральную задвижку"),
            WELL = PanelText.tr("prod.well", "Choke %s/64″ · water cut %s %% · GOR %s m³/m³",
                    "Штуцер %s/64″ · обводнённость %s %% · газовый фактор %s м³/м³"),
            OIL_LEVEL = PanelText.tr("prod.oil_level", "Oil", "Нефть"),
            WATER_LEVEL = PanelText.tr("prod.water_level", "Water", "Вода"),
            GAS_LEVEL = PanelText.tr("prod.gas_level", "Gas", "Газ"),
            BSW = PanelText.tr("prod.bsw", "BS&W", "Вода в нефти"),
            BSW_TIP = PanelText.tr("prod.bsw_tip", "Water left in the oil %s %%: drops smaller than the Stokes cut size stay in it",
                    "Вода в нефти %s %%: капли мельче стоксовского граничного размера не успевают осесть"),
            RETENTION = PanelText.tr("prod.retention", "Retention", "Отстой"),
            FLARING = PanelText.tr("prod.flaring", "Flare", "Факел"),
            CARRYOVER = PanelText.tr("prod.carryover", "Carryover", "Унос"),
            FULL = PanelText.tr("prod.full", "Full", "Полон"),
            SEPARATOR = PanelText.tr("prod.separator", "Oil to the sides, gas from the top, water from the bottom · flare %s nm³/h",
                    "Нефть — с боков, газ — сверху, вода — снизу · на факел %s нм³/ч"),
            OUTLET = PanelText.tr("prod.outlet", "Furnace out", "Выход печи"),
            FEED = PanelText.tr("prod.feed", "Feed", "Сырьё"),
            FUEL = PanelText.tr("prod.fuel", "Fuel gas", "Топливо"),
            LIT = PanelText.tr("prod.lit", "Burners", "Горелки"),
            READY = PanelText.tr("prod.ready", "Column", "Колонна"),
            T_DOWN = PanelText.tr("prod.t_down", "T −", "T −"),
            T_UP = PanelText.tr("prod.t_up", "T +", "T +"),
            R_DOWN = PanelText.tr("prod.r_down", "Reflux −", "Флегма −"),
            R_UP = PanelText.tr("prod.r_up", "Reflux +", "Флегма +"),
            YIELDS = PanelText.tr("prod.yields", "Gasoline %s %% · kerosene %s %% · diesel %s %% · mazut %s %%",
                    "Бензин %s %% · керосин %s %% · дизель %s %% · мазут %s %%"),
            SHARPNESS = PanelText.tr("prod.sharpness", "%s theoretical trays · reflux %s · kerosene/diesel overlap %s K",
                    "Теоретических тарелок %s · флегмовое число %s · наложение керосин/дизель %s К"),
            LEAK = PanelText.tr("prod.overhead_leak", "Overhead cooler leaks: salt", "Течь конденсатора: соли"),
            SALT = PanelText.tr("prod.salt", "Salt in crude %s mg/l · overhead tube wear %s %%",
                    "Соли в нефти %s мг/л · износ пучка конденсатора %s %%"),
            GASOLINE = PanelText.tr("prod.gasoline", "Gasoline", "Бензин"),
            KEROSENE = PanelText.tr("prod.kerosene", "Kerosene", "Керосин"),
            DIESEL = PanelText.tr("prod.diesel", "Diesel", "Дизель"),
            MAZUT = PanelText.tr("prod.mazut", "Mazut", "Мазут");

    public static final PanelLayout WELLHEAD = PanelLayout.builder("wellhead", 300, 200, W_CHANNELS)
            .dial(8, 20, false, W_THP_X100, 100, 20, Double.NaN, 1, MPA.get(), THP.get(), v -> THP_TIP.get(v.num(W_THP_X100, 100, 2)))
            .dial(64, 20, false, W_BHP_X100, 100, 30, Double.NaN, 1, MPA.get(), BHP.get(), null)
            .dial(120, 20, false, W_PR_X100, 100, 30, Double.NaN, 1, MPA.get(), PR.get(), v -> PR_TIP.get(v.num(W_PR_X100, 100, 2)))
            .dial(176, 20, false, W_OIL_M3D10, 10, 100, Double.NaN, 0, M3D.get(), OIL.get(), null)
            .plot(8, 84, 180, 80, W_CARD, BeamPump.CARD_POINTS, 2000, 1500, W_FLAGS, WF_PUMPING, CARD.get(),
                    v -> CARD_TIP.get(v.raw(W_PEAK_KN), v.num(W_FILL_PERMIL, 10, 0), v.num(W_SPM10, 10, 1)))
            .lamp(236, 24, W_FLAGS, WF_OPEN, LampColor.GREEN, OPEN.get())
            .lamp(236, 40, W_FLAGS, WF_PUMPING, LampColor.GREEN, PUMPING.get())
            .lamp(236, 56, W_FLAGS, WF_TREE, LampColor.AMBER, TREE.get())
            .lamp(236, 72, W_FLAGS, WF_SEPARATOR, LampColor.AMBER, SEP.get())
            .button(200, 100, 46, WB_CHOKE_DOWN, CHOKE_DOWN.get(), null)
            .button(250, 100, 46, WB_CHOKE_UP, CHOKE_UP.get(), null)
            .button(200, 122, 96, WB_VALVE, VALVE.get(), VALVE_TIP.get())
            .readout(8, 180, 284, v -> WELL.get(v.raw(W_CHOKE), v.num(W_WC_PERMIL, 10, 0), v.raw(W_GOR)), null)
            .noInventory().build();

    public static final PanelLayout SEPARATOR_PANEL = PanelLayout.builder("three_phase_separator", 240, 120, S_CHANNELS)
            .glass(8, 22, 64, S_OIL_PERMIL, 0xFF3A2A18, Double.NaN, Double.NaN, OIL_LEVEL.get(), null)
            .glass(36, 22, 64, S_WATER_PERMIL, 0xFF4A6A8A, Double.NaN, Double.NaN, WATER_LEVEL.get(), null)
            .glass(64, 22, 64, S_GAS_PERMIL, 0xFFB0B0A0, Double.NaN, Double.NaN, GAS_LEVEL.get(), null)
            .dial(96, 22, false, S_BSW_X100, 100, 10, 2, 2, PERCENT.get(), BSW.get(), v -> BSW_TIP.get(v.num(S_BSW_X100, 100, 2)))
            .dial(150, 22, false, S_RET_S, 60, 10, Double.NaN, 1, MIN.get(), RETENTION.get(), null)
            .lamp(206, 24, S_FLAGS, SF_FLARING, LampColor.AMBER, FLARING.get())
            .lamp(206, 40, S_FLAGS, SF_CARRYOVER, LampColor.RED, CARRYOVER.get())
            .lamp(206, 56, S_FLAGS, SF_FULL, LampColor.RED, FULL.get())
            .readout(8, 104, 224, v -> SEPARATOR.get(v.raw(S_FLARE_M3H)), null)
            .noInventory().build();

    public static final PanelLayout COLUMN = PanelLayout.builder("crude_column", 300, 170, C_CHANNELS)
            .dial(8, 22, false, C_T_OUT, 1, 400, 380, 0, CELSIUS.get(), OUTLET.get(), null)
            .dial(64, 22, false, C_FEED_M3H10, 10, 12, Double.NaN, 1, M3H.get(), FEED.get(), null)
            .dial(120, 22, false, C_FUEL_M3H, 1, 400, Double.NaN, 0, M3H.get(), FUEL.get(), null)
            .glass(180, 22, 64, C_TANK0, 0xFFE0C060, Double.NaN, Double.NaN, GASOLINE.get(), null)
            .glass(208, 22, 64, C_TANK0 + 1, 0xFFC8B070, Double.NaN, Double.NaN, KEROSENE.get(), null)
            .glass(236, 22, 64, C_TANK0 + 2, 0xFFA08040, Double.NaN, Double.NaN, DIESEL.get(), null)
            .glass(264, 22, 64, C_TANK0 + 3, 0xFF302010, Double.NaN, Double.NaN, MAZUT.get(), null)
            .lamp(8, 100, C_FLAGS, CF_LIT, LampColor.AMBER, LIT.get())
            .lamp(70, 100, C_FLAGS, CF_READY, LampColor.GREEN, READY.get())
            .lamp(140, 100, C_FLAGS, CF_LEAK, LampColor.RED, LEAK.get())
            .readout(8, 86, 284, v -> SALT.get(v.num(C_SALT_X10, 10, 1), v.num(C_CORROSION_PERMIL, 10, 0)), null)
            .button(8, 116, 40, CB_T_DOWN, T_DOWN.get(), null)
            .button(52, 116, 40, CB_T_UP, T_UP.get(), null)
            .button(100, 116, 56, CB_R_DOWN, R_DOWN.get(), null)
            .button(160, 116, 56, CB_R_UP, R_UP.get(), null)
            .readout(8, 138, 284, v -> YIELDS.get(v.num(C_Y_GASOLINE, 10, 1), v.num(C_Y_KEROSENE, 10, 1), v.num(C_Y_DIESEL, 10, 1),
                    v.num(C_Y_RESIDUE, 10, 1)), null)
            .readout(8, 152, 284, v -> SHARPNESS.get(v.raw(C_TRAYS), v.num(C_REFLUX_X10, 10, 1), v.raw(C_OVERLAP_K)), null)
            .noInventory().build();

    private ProductionPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(WELLHEAD, SEPARATOR_PANEL, COLUMN);
    }
}
