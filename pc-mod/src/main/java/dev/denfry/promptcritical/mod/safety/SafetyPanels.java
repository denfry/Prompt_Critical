package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щиты промышленной безопасности 13: люк замкнутого пространства (§13.2.6). */
public final class SafetyPanels {
    public static final int B_HATCH = 0, B_ACTION = 1;

    // ------------------------------------------------------------------ люк-лаз (13 §13.2.6)
    public static final int MW_O2_X10 = 0, MW_LEL_X10 = 1, MW_H2S = 2, MW_CO = 3, MW_VOLUME = 4, MW_VENT_LS = 5, MW_INSIDE = 6,
            MW_SAFE_S = 7, MW_TANK = 8, MW_FLAGS = 9, MW_CHANNELS = 10;
    public static final int F_OPEN = 0, F_CONFINED = 1, F_ATTENDANT = 2, F_PERMIT = 3, F_VENT = 4, F_UNSAFE = 5;
    private static final Tr PCT = PanelText.tr("safety.pct", "%", "%"), PPM = PanelText.tr("safety.ppm", "ppm", "ppm"),
            O2 = PanelText.tr("safety.o2", "Oxygen", "Кислород"), LEL = PanelText.tr("safety.lel", "Flammables, LEL", "Горючие, НКПР"),
            H2S = PanelText.tr("safety.h2s", "H₂S", "H₂S"), CO = PanelText.tr("safety.co", "CO", "CO"),
            OPEN = PanelText.tr("safety.hatch_open", "Hatch open", "Люк открыт"),
            CONFINED = PanelText.tr("safety.confined", "Confined space behind the hatch", "За люком замкнутое пространство"),
            ATTENDANT = PanelText.tr("safety.attendant", "Attendant at the entry (≤ 4 m)", "Наблюдающий у входа (≤ 4 м)"),
            PERMIT = PanelText.tr("safety.permit", "Confined space permit active", "Наряд на замкнутое пространство действует"),
            VENT = PanelText.tr("safety.vent", "Forced ventilation", "Принудительная вентиляция"),
            UNSAFE = PanelText.tr("safety.unsafe", "Atmosphere unfit to enter", "Атмосфера непригодна для входа"),
            HATCH = PanelText.tr("safety.hatch", "Hatch", "Люк"),
            HATCH_TIP = PanelText.tr("safety.hatch_tip", "Open or close the manhole cover. Enter and leave: sneak + right-click the manway",
                    "Открыть или закрыть крышку. Вход и выход: Shift + ПКМ по люку"),
            STATUS = PanelText.tr("safety.manway_status", "Volume %s m³ · ventilation %s L/s · inside %s · fit to enter in %s · gas %s mB",
                    "Объём %s м³ · вентиляция %s л/с · внутри %s · пригодна через %s · газ %s мБ"),
            NEVER = PanelText.tr("safety.never", "never (no ventilation)", "никогда (нет вентиляции)"),
            NOW = PanelText.tr("safety.now", "now", "сейчас"), MIN = PanelText.tr("safety.minutes", "%s min", "%s мин");

    public static final PanelLayout MANWAY = PanelLayout.builder("manway", 300, 132, MW_CHANNELS)
            .dial(8, 24, false, MW_O2_X10, 10, 25, Double.NaN, 1, PCT.get(), O2.get(), null)
            .dial(64, 24, false, MW_LEL_X10, 10, 100, 10, 0, PCT.get(), LEL.get(), null)
            .dial(120, 24, false, MW_H2S, 1, 100, 10, 0, PPM.get(), H2S.get(), null)
            .dial(176, 24, false, MW_CO, 1, 200, 25, 0, PPM.get(), CO.get(), null)
            .lamp(236, 22, MW_FLAGS, F_OPEN, LampColor.AMBER, OPEN.get())
            .lamp(236, 36, MW_FLAGS, F_CONFINED, LampColor.GREEN, CONFINED.get())
            .lamp(236, 50, MW_FLAGS, F_ATTENDANT, LampColor.GREEN, ATTENDANT.get())
            .lamp(236, 64, MW_FLAGS, F_PERMIT, LampColor.GREEN, PERMIT.get())
            .lamp(236, 78, MW_FLAGS, F_VENT, LampColor.GREEN, VENT.get())
            .lamp(236, 92, MW_FLAGS, F_UNSAFE, LampColor.RED, UNSAFE.get())
            .button(8, 92, 60, B_HATCH, HATCH.get(), HATCH_TIP.get())
            .readout(8, 112, 284, v -> STATUS.get(v.raw(MW_VOLUME), v.raw(MW_VENT_LS), v.raw(MW_INSIDE),
                    v.raw(MW_SAFE_S) < 0 ? NEVER.get() : v.raw(MW_SAFE_S) == 0 ? NOW.get() : MIN.get((v.raw(MW_SAFE_S) + 59) / 60),
                    v.raw(MW_TANK)), null)
            .noInventory().build();

    // ------------------------------------------------------------------ щит розжига факела (13 §13.4.3)
    public static final int B_DOWN = 2, B_UP = 3;
    public static final int FL_FLOW = 0, FL_HEAT_X10 = 1, FL_SMOKE = 2, FL_ETA_X10 = 3, FL_RATIO_X100 = 4, FL_PILOTS = 5, FL_FUEL = 6,
            FL_SAFE_M = 7, FL_FLAGS = 8, FL_CHANNELS = 9;
    public static final int F_BURNING = 0, F_UNBURNT = 1, F_RAIN = 2, F_NO_SEAL = 3, F_NO_KO = 4, F_NO_TIP = 5, F_NO_STEAM = 6, F_FLASHBACK = 7;
    private static final Tr NM3H = PanelText.tr("safety.nm3h", "m³/h", "м³/ч"), MW = PanelText.tr("safety.mw", "MW", "МВт"),
            FLOW = PanelText.tr("safety.flare_flow", "Relief", "Сброс"), HEAT = PanelText.tr("safety.flare_heat", "Heat", "Тепло"),
            SMOKE = PanelText.tr("safety.smoke", "Smoke", "Дымность"), ETA = PanelText.tr("safety.eta", "Combustion", "Полнота сгорания"),
            BURNING = PanelText.tr("safety.burning", "Flare burning", "Факел горит"),
            UNBURNT = PanelText.tr("safety.unburnt", "UNBURNT RELEASE: pilots out", "НЕСГОРЕВШИЙ ВЫБРОС: горелки погасли"),
            RAIN = PanelText.tr("safety.rain", "Liquid carry-over: burning rain", "Унос жидкости: горящий дождь"),
            NO_SEAL = PanelText.tr("safety.no_seal", "No seal drum: air in the stack", "Нет гидрозатвора: воздух в стволе"),
            NO_KO = PanelText.tr("safety.no_ko", "No knock-out drum", "Нет сепаратора капель"),
            NO_TIP = PanelText.tr("safety.no_tip", "No flare tip within 8 blocks", "Нет оголовка в 8 блоках"),
            NO_STEAM = PanelText.tr("safety.no_steam", "No steam: water-tube boiler within 16 blocks", "Нет пара: водотрубный котёл в 16 блоках"),
            FLASHBACK = PanelText.tr("safety.flashback", "FLASHBACK: seal drum low", "ПРОСКОК ПЛАМЕНИ: мало воды в затворе"),
            IGNITE = PanelText.tr("safety.ignite", "Ignite", "Розжиг"),
            IGNITE_TIP = PanelText.tr("safety.ignite_tip", "Light the three pilots: fuel gas piped to the panel",
                    "Зажечь три дежурные горелки: топливный газ трубой в щит"),
            STEAM_TIP = PanelText.tr("safety.steam_tip", "Steam to gas ratio, 0.05 kg/kg per step: 0.3 is smokeless for associated gas, above 0.9 the flame lifts off",
                    "Пар к газу, 0,05 кг/кг за шаг: 0,3 — бездымно для попутного газа, выше 0,9 — срыв пламени"),
            FLARE_STATUS = PanelText.tr("safety.flare_status", "Steam %s kg/kg · pilots %s of 3 · fuel %s mB · 1.58 kW/m² at %s m",
                    "Пар %s кг/кг · горелок %s из 3 · топливо %s мБ · 1,58 кВт/м² на %s м");

    public static final PanelLayout FLARE = PanelLayout.builder("flare_ignition_panel", 300, 150, FL_CHANNELS)
            .dial(8, 24, false, FL_FLOW, 1, 2000, Double.NaN, 0, NM3H.get(), FLOW.get(), null)
            .dial(64, 24, false, FL_HEAT_X10, 10, 100, Double.NaN, 1, MW.get(), HEAT.get(), null)
            .dial(120, 24, false, FL_SMOKE, 1, 100, 20, 0, PCT.get(), SMOKE.get(), null)
            .dial(176, 24, false, FL_ETA_X10, 10, 100, Double.NaN, 1, PCT.get(), ETA.get(), null)
            .lamp(236, 18, FL_FLAGS, F_BURNING, LampColor.GREEN, BURNING.get())
            .lamp(236, 30, FL_FLAGS, F_UNBURNT, LampColor.RED, UNBURNT.get())
            .lamp(236, 42, FL_FLAGS, F_FLASHBACK, LampColor.RED, FLASHBACK.get())
            .lamp(236, 54, FL_FLAGS, F_RAIN, LampColor.RED, RAIN.get())
            .lamp(236, 66, FL_FLAGS, F_NO_SEAL, LampColor.AMBER, NO_SEAL.get())
            .lamp(236, 78, FL_FLAGS, F_NO_KO, LampColor.AMBER, NO_KO.get())
            .lamp(236, 90, FL_FLAGS, F_NO_STEAM, LampColor.AMBER, NO_STEAM.get())
            .lamp(236, 102, FL_FLAGS, F_NO_TIP, LampColor.AMBER, NO_TIP.get())
            .button(8, 96, 60, B_ACTION, IGNITE.get(), IGNITE_TIP.get())
            .button(72, 96, 20, B_DOWN, Component.literal("−"), STEAM_TIP.get())
            .button(96, 96, 20, B_UP, Component.literal("+"), STEAM_TIP.get())
            .readout(8, 128, 284, v -> FLARE_STATUS.get(v.num(FL_RATIO_X100, 100, 2), v.raw(FL_PILOTS), v.raw(FL_FUEL), v.raw(FL_SAFE_M)), null)
            .noInventory().build();

    private SafetyPanels() {
    }

    public static List<PanelLayout> all() {
        List<PanelLayout> out = new ArrayList<>();
        out.add(MANWAY);
        out.add(FLARE);
        return out;
    }
}
