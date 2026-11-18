package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.ArrayList;
import java.util.List;

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

    private SafetyPanels() {
    }

    public static List<PanelLayout> all() {
        List<PanelLayout> out = new ArrayList<>();
        out.add(MANWAY);
        return out;
    }
}
