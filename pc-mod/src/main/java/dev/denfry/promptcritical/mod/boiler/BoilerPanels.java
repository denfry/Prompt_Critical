package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import static dev.denfry.promptcritical.mod.panel.PanelText.CELSIUS;
import static dev.denfry.promptcritical.mod.panel.PanelText.KGH;
import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.PA;
import static dev.denfry.promptcritical.mod.panel.PanelText.RPM;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Местные щиты котельной I0: котёл, паровая машина, цепная решётка. Каналы — целые, масштаб в имени. */
public final class BoilerPanels {
    // --- котёл ---
    public static final int B_P_KPA = 0, B_GLASS_PERMIL = 1, B_BED_KG10 = 2, B_FEED_L = 3, B_FLUE_C10 = 4, B_DRAFT_PA10 = 5,
            B_WATER_C10 = 6, B_FLAGS = 7, B_EFF_PERMIL = 8, B_CROWN_C10 = 9, B_RELIEF_KPA = 10, B_CHANNELS = 11;
    public static final int F_LIT = 0, F_RELIEF = 1, F_PLUG = 2, F_SMOKY = 3, F_LOW_WATER = 4, F_RUPTURED = 5;
    public static final int BUTTON_CHARGE = 0;
    /** Склад щита котла: совок с топливом, шлак. */
    public static final int SLOT_FUEL = 0, SLOT_SLAG = 1;

    // --- машина ---
    public static final int E_RPM10 = 0, E_CHEST_KPA = 1, E_OUT_RPM10 = 2, E_CAPACITY_SU = 3, E_LOAD_KW10 = 4, E_FLAGS = 5,
            E_GEAR = 6, E_STEAM_KGH = 7, E_THROTTLE_PERMIL = 8, E_CHANNELS = 9;
    public static final int EF_VALVE = 0, EF_DASHPOT = 1, EF_BOILER = 2, EF_RUNNING = 3;
    public static final int BUTTON_VALVE = 0, BUTTON_BAR = 1, BUTTON_GEAR = 2;

    // --- решётка ---
    public static final int S_FEED_KGH = 0, S_RPM10 = 1, S_FLAGS = 2, S_BED_KG10 = 3, S_CHANNELS = 4;
    public static final int SF_RUNNING = 0, SF_BOILER = 1;

    private static final Tr PRESSURE = PanelText.tr("boiler.pressure", "Pressure", "Давление"),
            GLASS = PanelText.tr("boiler.glass", "Water", "Вода"),
            DRAFT = PanelText.tr("boiler.draft", "Draft", "Тяга"),
            FLUE = PanelText.tr("boiler.flue", "Flue gas", "Дым. газы"),
            LIT = PanelText.tr("boiler.lit", "Fire", "Топка"),
            RELIEF = PanelText.tr("boiler.relief", "Relief", "Клапан"),
            PLUG = PanelText.tr("boiler.plug", "Plug", "Пробка"),
            LOW = PanelText.tr("boiler.low_water", "Low wtr", "Упуск"),
            BED = PanelText.tr("boiler.bed", "Fire bed", "Слой на решётке"),
            FEED = PanelText.tr("boiler.feed", "Feed tank", "Питательный бак"),
            CHARGE = PanelText.tr("boiler.charge", "Fire", "Забросить"),
            CHARGE_TIP = PanelText.tr("boiler.charge_tip", "Throw one shovel of fuel from the slot onto the grate",
                    "Забросить на решётку одну порцию топлива из слота"),
            FUEL_SLOT = PanelText.tr("boiler.fuel_slot", "Fuel: coal, washed coal, charcoal, coke",
                    "Топливо: уголь, концентрат, древесный уголь, кокс"),
            SLAG_SLOT = PanelText.tr("boiler.slag_slot", "Slag from the ash pit", "Шлак из зольника"),
            SLAG = PanelText.tr("boiler.slag", "Slag", "Шлак"),
            PRESSURE_TIP = PanelText.tr("boiler.pressure_tip", "Steam pressure %s MPa (working %s, relief valve lifts at %s)",
                    "Давление пара %s МПа (рабочее %s, клапан открывается при %s)"),
            GLASS_TIP = PanelText.tr("boiler.glass_tip", "Gauge glass %s%% · red line — furnace crown and fusible plug",
                    "Водоуказательное стекло %s%% · красная черта — потолок жаровой трубы и пробка"),
            DRAFT_TIP = PanelText.tr("boiler.draft_tip", "Chimney draft %s Pa — more chimney, more air",
                    "Тяга трубы %s Па — выше труба, больше воздуха"),
            FLUE_TIP = PanelText.tr("boiler.flue_tip", "Flue gas leaving the boiler %s °C", "Уходящие газы %s °C"),
            BED_TIP = PanelText.tr("boiler.bed_tip", "Coal on the grate %s of %s kg", "Угля на решётке %s из %s кг"),
            FEED_TIP = PanelText.tr("boiler.feed_tip", "Feed water %s of %s L — fill by pipe or bucket",
                    "Питательной воды %s из %s л — трубой или ведром"),
            STATUS = PanelText.tr("boiler.status", "Water %s °C · crown %s °C · eff. %s", "Вода %s °C · потолок %s °C · КПД %s"),
            STATUS_COLD = PanelText.tr("boiler.status_cold", "Cold: fill the tank, charge coal, light with flint",
                    "Холодный: наполните бак, заложите уголь, разожгите огнивом"),
            STATUS_RUPTURED = PanelText.tr("boiler.status_ruptured", "WRECKED — boiler shell ruptured", "РАЗРУШЕН — разрыв обечайки"),
            STATUS_PLUG = PanelText.tr("boiler.status_plug", "Plug melted — draw the fire, blow down, fit a new plug",
                    "Пробка выплавлена — погасите, стравите, поставьте новую");

    private static final Tr TACHO = PanelText.tr("engine.tacho", "Speed", "Обороты"),
            CHEST = PanelText.tr("engine.chest", "Steam chest", "Золотник"),
            VALVE = PanelText.tr("engine.valve", "Stop valve", "Стопорный вентиль"),
            VALVE_TIP = PanelText.tr("engine.valve_tip", "Open or close the stop valve on the steam line",
                    "Открыть или закрыть стопорный вентиль на паропроводе"),
            BAR = PanelText.tr("engine.bar", "Bar over", "Провернуть"),
            BAR_TIP = PanelText.tr("engine.bar_tip", "Turn the flywheel off dead centre with a bar",
                    "Провернуть маховик ломом с мёртвой точки"),
            GEAR_BUTTON = PanelText.tr("engine.gear_button", "Gear", "Передача"),
            GEAR_TIP = PanelText.tr("engine.gear_tip", "Output gear: 16 / 32 / 64 RPM on the Create shaft at full speed",
                    "Передача на вал Create: 16 / 32 / 64 RPM на номинальных оборотах"),
            VALVE_LAMP = PanelText.tr("engine.valve_lamp", "Valve", "Вентиль"),
            DASHPOT = PanelText.tr("engine.dashpot", "Dashpot", "Катаракт"),
            BOILER_LAMP = PanelText.tr("engine.boiler", "Boiler", "Котёл"),
            DRIVE = PanelText.tr("engine.drive", "Drive", "Привод"),
            THROTTLE = PanelText.tr("engine.throttle", "Governor throttle", "Дроссель регулятора"),
            THROTTLE_TIP = PanelText.tr("engine.throttle_tip", "Governor opens the throttle %s%%", "Регулятор открыл дроссель на %s%%"),
            OUTPUT = PanelText.tr("engine.output", "Shaft %s of %s RPM · %s SU · load %s kW",
                    "Вал %s из %s RPM · %s SU · нагрузка %s кВт"),
            STEAM = PanelText.tr("engine.steam", "Steam %s kg/h", "Пар %s кг/ч"),
            NO_BOILER = PanelText.tr("engine.no_boiler", "No boiler touching the engine", "Машина не примыкает к котлу"),
            TACHO_TIP = PanelText.tr("engine.tacho_tip", "Engine %s rpm (rated 120)", "Машина %s об/мин (номинал 120)"),
            CHEST_TIP = PanelText.tr("engine.chest_tip", "Steam at the valve chest %s MPa", "Пар перед золотником %s МПа");

    private static final Tr FEED_RATE = PanelText.tr("stoker.feed", "Coal feed", "Подача угля"),
            HOPPER = PanelText.tr("stoker.hopper", "Hopper: coal, washed coal, charcoal, coke",
                    "Бункер: уголь, концентрат, древесный уголь, кокс"),
            CHAIN = PanelText.tr("stoker.chain", "Chain", "Цепь"),
            STOKER_STATUS = PanelText.tr("stoker.status", "Shaft %s rpm · bed %s kg", "Вал %s об/мин · слой %s кг"),
            STOKER_NO_BOILER = PanelText.tr("stoker.no_boiler", "Back the stoker onto a boiler", "Решётку нужно примкнуть задом к котлу");

    public static final PanelLayout BOILER = PanelLayout.builder("fire_tube_boiler", 220, 178, B_CHANNELS)
            .dial(8, 24, true, B_P_KPA, 1000, 1.6, 1.32, 2, MPA.get(), PRESSURE.get(),
                    v -> PRESSURE_TIP.get(v.num(B_P_KPA, 1000, 2), "1.20", v.num(B_RELIEF_KPA, 1000, 2)))
            .glass(80, 24, 74, B_GLASS_PERMIL, 0xFF3F78B8, (0.45 - FireTubeBoilerBlockEntity.GLASS_BOTTOM_M) / FireTubeBoilerBlockEntity.GLASS_SPAN_M,
                    (0.67 - FireTubeBoilerBlockEntity.GLASS_BOTTOM_M) / FireTubeBoilerBlockEntity.GLASS_SPAN_M, GLASS.get(),
                    v -> GLASS_TIP.get(v.num(B_GLASS_PERMIL, 10, 0)))
            .dial(104, 24, false, B_DRAFT_PA10, 10, 200, Double.NaN, 0, PA.get(), DRAFT.get(),
                    v -> DRAFT_TIP.get(v.num(B_DRAFT_PA10, 10, 0)))
            .dial(162, 24, false, B_FLUE_C10, 10, 600, 450, 0, CELSIUS.get(), FLUE.get(),
                    v -> FLUE_TIP.get(v.num(B_FLUE_C10, 10, 0)))
            .lamp(104, 88, B_FLAGS, F_LIT, LampColor.AMBER, LIT.get())
            .lamp(104, 104, B_FLAGS, F_RELIEF, LampColor.WHITE, RELIEF.get())
            .lamp(162, 88, B_FLAGS, F_PLUG, LampColor.RED, PLUG.get())
            .lamp(162, 104, B_FLAGS, F_LOW_WATER, LampColor.RED, LOW.get())
            .bar(8, 132, 98, B_BED_KG10, 10, FireTubeBoilerBlockEntity.MAX_BED_KG, 0xFFE07828, BED.get(),
                    v -> BED_TIP.get(v.num(B_BED_KG10, 10, 0), PanelText.num(FireTubeBoilerBlockEntity.MAX_BED_KG, 0)))
            .bar(114, 132, 98, B_FEED_L, 1, FireTubeBoilerBlockEntity.FEED_TANK_MB, 0xFF3F78B8, FEED.get(),
                    v -> FEED_TIP.get(v.num(B_FEED_L, 1, 0), FireTubeBoilerBlockEntity.FEED_TANK_MB))
            .readout(8, 143, 204, BoilerPanels::boilerStatus, null)
            .slot(10, 159, SLOT_FUEL, s -> BoilerFuels.key(s) != null, FUEL_SLOT.get())
            .button(30, 160, 64, BUTTON_CHARGE, CHARGE.get(), CHARGE_TIP.get())
            .caption(166, 164, SLAG.get())
            .output(194, 159, SLOT_SLAG, SLAG_SLOT.get())
            .build();

    public static final PanelLayout ENGINE = PanelLayout.builder("steam_engine", 220, 168, E_CHANNELS)
            .dial(8, 24, true, E_RPM10, 10, 160, 140, 0, RPM.get(), TACHO.get(), v -> TACHO_TIP.get(v.num(E_RPM10, 10, 0)))
            .dial(80, 24, false, E_CHEST_KPA, 1000, 1.6, 1.32, 2, MPA.get(), CHEST.get(),
                    v -> CHEST_TIP.get(v.num(E_CHEST_KPA, 1000, 2)))
            .lamp(140, 26, E_FLAGS, EF_BOILER, LampColor.GREEN, BOILER_LAMP.get())
            .lamp(140, 42, E_FLAGS, EF_VALVE, LampColor.GREEN, VALVE_LAMP.get())
            .lamp(140, 58, E_FLAGS, EF_DASHPOT, LampColor.WHITE, DASHPOT.get())
            .lamp(140, 74, E_FLAGS, EF_RUNNING, LampColor.AMBER, DRIVE.get())
            .bar(80, 100, 132, E_THROTTLE_PERMIL, 1000, 1, 0xFFC8A050, THROTTLE.get(),
                    v -> THROTTLE_TIP.get(v.num(E_THROTTLE_PERMIL, 10, 0)))
            .readout(8, 114, 204, BoilerPanels::engineStatus, null)
            .readout(8, 129, 204, v -> STEAM.get(v.num(E_STEAM_KGH, 1, 0)), null)
            .button(8, 146, 80, BUTTON_VALVE, VALVE.get(), VALVE_TIP.get())
            .button(92, 146, 56, BUTTON_BAR, BAR.get(), BAR_TIP.get())
            .button(152, 146, 60, BUTTON_GEAR, GEAR_BUTTON.get(), GEAR_TIP.get())
            .build();

    public static final PanelLayout STOKER = PanelLayout.builder("grate_stoker", 176, 100, S_CHANNELS)
            .dial(8, 24, false, S_FEED_KGH, 1, 1500, Double.NaN, 0, KGH.get(), FEED_RATE.get(), null)
            .lamp(64, 26, S_FLAGS, SF_BOILER, LampColor.GREEN, BOILER_LAMP.get())
            .lamp(64, 42, S_FLAGS, SF_RUNNING, LampColor.AMBER, CHAIN.get())
            .slot(140, 34, 0, s -> BoilerFuels.key(s) != null, HOPPER.get())
            .readout(64, 62, 104, v -> v.bit(S_FLAGS, SF_BOILER)
                    ? STOKER_STATUS.get(v.num(S_RPM10, 10, 0), v.num(S_BED_KG10, 10, 0))
                    : STOKER_NO_BOILER.get().withStyle(ChatFormatting.RED), null)
            .build();

    private BoilerPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(BOILER, ENGINE, STOKER);
    }

    private static Component boilerStatus(PanelValues v) {
        if (v.bit(B_FLAGS, F_RUPTURED)) return STATUS_RUPTURED.get().withStyle(ChatFormatting.RED);
        if (v.bit(B_FLAGS, F_PLUG)) return STATUS_PLUG.get().withStyle(ChatFormatting.RED);
        if (!v.bit(B_FLAGS, F_LIT) && v.raw(B_P_KPA) < 20) return STATUS_COLD.get();
        String eff = v.raw(B_EFF_PERMIL) < 0 ? "—" : v.num(B_EFF_PERMIL, 10, 0) + " %";
        return STATUS.get(v.num(B_WATER_C10, 10, 0), v.num(B_CROWN_C10, 10, 0), eff);
    }

    private static Component engineStatus(PanelValues v) {
        if (!v.bit(E_FLAGS, EF_BOILER)) return NO_BOILER.get().withStyle(ChatFormatting.RED);
        return OUTPUT.get(v.num(E_OUT_RPM10, 10, 0), v.raw(E_GEAR), v.raw(E_CAPACITY_SU), v.num(E_LOAD_KW10, 10, 1));
    }
}
