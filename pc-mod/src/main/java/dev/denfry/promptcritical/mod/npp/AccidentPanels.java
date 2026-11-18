package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Accident channels shared by the light-water reactor panels: a block of {@link #COUNT} channels from a panel's base
 * index, one readout line (the post-accident monitoring summary) with a tooltip, and the initiating-event drill button.
 */
public final class AccidentPanels {
    public static final int LEVEL = 0, COVER = 1, PCT_C = 2, CLAD_C = 3, H2_KG10 = 4, CONT_KPA = 5, CONT_H2 = 6, ECCS_KGS10 = 7,
            BREAK_KGS10 = 8, BREAK_CM2 = 9, TRAINS = 10, FLAGS = 11, COUNT = 12;
    public static final int F_ACTIVE = 0, F_SBO = 1, F_UNCOVERED = 2, F_ADS = 3, F_CONTAINMENT = 4, F_BREACHED = 5, F_PCT = 6;

    public static final Tr DRILL = PanelText.tr("accident.drill", "LOCA", "LOCA"),
            DRILL_TIP = PanelText.tr("accident.drill_tip",
                    "Initiating-event drill: open a small break, widen it to a guillotine break of the main pipe, isolate it",
                    "Учебное исходное событие: малая течь, затем разрыв главного трубопровода на полное сечение, затем отсечение"),
            NORMAL = PanelText.tr("accident.normal", "ECCS standby · trains %s/%s · accumulators %s · containment %s",
                    "САОЗ в резерве · каналов %s/%s · гидроёмкостей %s · гермооболочка %s"),
            ACTIVE = PanelText.tr("accident.active", "%s · level %s %% · PCT %s °C · H₂ %s kg · ECCS %s kg/s · containment %s",
                    "%s · уровень %s %% · макс. оболочка %s °C · H₂ %s кг · САОЗ %s кг/с · ГО %s"),
            LOCA = PanelText.tr("accident.loca", "LOCA %s cm²", "Течь %s см²"),
            SBO = PanelText.tr("accident.sbo", "BLACKOUT", "ОБЕСТОЧИВАНИЕ"),
            BOTH = PanelText.tr("accident.loca_sbo", "LOCA %s cm² + BLACKOUT", "Течь %s см² + обесточивание"),
            CONT_NONE = PanelText.tr("accident.cont_none", "none", "нет"),
            CONT_KPA_TEXT = PanelText.tr("accident.cont_kpa", "%s kPa", "%s кПа"),
            CONT_TORN = PanelText.tr("accident.cont_torn", "BREACHED", "РАЗРУШЕНА"),
            TIP = PanelText.tr("accident.tip",
                    "Vessel level %s %% · core covered %s %% · cladding %s °C, peak %s °C (limit 1204) · break %s kg/s · ADS %s · containment H₂ %s %% (burns at 8)",
                    "Уровень в корпусе %s %% · зона залита на %s %% · оболочка %s °C, максимум %s °C (предел 1204) · течь %s кг/с · АДС %s · H₂ в ГО %s %% (горит с 8)"),
            YES = PanelText.tr("accident.yes", "open", "открыт"),
            NO = PanelText.tr("accident.no", "shut", "закрыт");

    private AccidentPanels() {
    }

    public static Component line(PanelValues v, int base) {
        int flags = v.raw(base + FLAGS);
        Component cont = (flags & 1 << F_BREACHED) != 0 ? CONT_TORN.get()
                : (flags & 1 << F_CONTAINMENT) != 0 ? CONT_KPA_TEXT.get(v.raw(base + CONT_KPA)) : CONT_NONE.get();
        if ((flags & 1 << F_ACTIVE) == 0) {
            int t = v.raw(base + TRAINS);
            return NORMAL.get(t & 0xFF, t >> 8 & 0xFF, t >> 16 & 0xFF, cont);
        }
        String cm2 = Integer.toString(v.raw(base + BREAK_CM2));
        boolean loca = v.raw(base + BREAK_CM2) > 0, sbo = (flags & 1 << F_SBO) != 0;
        Component what = loca && sbo ? BOTH.get(cm2) : loca ? LOCA.get(cm2) : SBO.get();
        Component c = ACTIVE.get(what, v.num(base + LEVEL, 10, 0), v.raw(base + PCT_C), v.num(base + H2_KG10, 10, 1),
                v.num(base + ECCS_KGS10, 10, 1), cont);
        ChatFormatting colour = (flags & (1 << F_UNCOVERED | 1 << F_PCT | 1 << F_BREACHED)) != 0 ? ChatFormatting.DARK_RED : ChatFormatting.RED;
        return c.copy().withStyle(colour);
    }

    public static Component tip(PanelValues v, int base) {
        int flags = v.raw(base + FLAGS);
        return TIP.get(v.num(base + LEVEL, 10, 0), v.num(base + COVER, 10, 0), v.raw(base + CLAD_C), v.raw(base + PCT_C),
                v.num(base + BREAK_KGS10, 10, 1), (flags & 1 << F_ADS) != 0 ? YES.get() : NO.get(), v.num(base + CONT_H2, 10, 1));
    }
}
