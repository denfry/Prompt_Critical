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
            BREAK_KGS10 = 8, BREAK_CM2 = 9, TRAINS = 10, FLAGS = 11, MELT_STAGE = 12, MELT_GAMMA = 13, HEAD_C = 14, CORIUM_KG = 15,
            ABLATED_M3 = 16, MELT_FLAGS = 17, H2_REGIME = 18, CONT_STEAM = 19, COUNT = 20;
    public static final int F_ACTIVE = 0, F_SBO = 1, F_UNCOVERED = 2, F_ADS = 3, F_CONTAINMENT = 4, F_BREACHED = 5, F_PCT = 6;
    /** {@link #MELT_FLAGS}: the plant has a lower head model, the cavity is flooded, in-vessel retention holds, the head failed. */
    public static final int M_VESSEL = 0, M_FLOODED = 1, M_IVR = 2, M_FAILED = 3;

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
                    "Vessel level %s %% · core covered %s %% · cladding %s °C, peak %s °C (limit 1204) · break %s kg/s · ADS %s · containment H₂ %s %%, steam %s %%: %s (a global burn from 8 %%, at once on a hot surface)",
                    "Уровень в корпусе %s %% · зона залита на %s %% · оболочка %s °C, максимум %s °C (предел 1204) · течь %s кг/с · АДС %s · в ГО H₂ %s %%, пар %s %%: %s (общее горение с 8 %%, сразу — от горячей поверхности)"),
            MELT_LINE = PanelText.tr("accident.melt_line", "Core: %s · lost geometry %s %% · lower head %s °C · cavity %s",
                    "Зона: %s · потеря геометрии %s %% · днище %s °C · шахта %s"),
            MELT_OUT = PanelText.tr("accident.melt_out", "Core: %s · corium on the concrete %s t · ablated %s m³ · cavity %s",
                    "Зона: %s · кориум на бетоне %s т · проплавлено %s м³ · шахта %s"),
            MELT_TIP = PanelText.tr("accident.melt_tip",
                    "Water back over a core with under 10 % lost geometry still cools it. Flood the reactor cavity to hold the melt in the vessel (up to 1.2 MW/m² through the head); open the PORV before the head fails, or the melt is blown into the containment",
                    "Залив зоны с потерей геометрии меньше 10 % ещё охлаждает её. Затопите шахту реактора, чтобы удержать расплав в корпусе (до 1,2 МВт/м² через днище); откройте ИПУ до проплавления днища, иначе расплав выбросит в гермооболочку"),
            CAVITY_WET = PanelText.tr("accident.cavity_wet", "flooded", "затоплена"),
            CAVITY_DRY = PanelText.tr("accident.cavity_dry", "dry", "сухая");
    /** {@link #H2_REGIME}: the mixture's regime of 04c §4c.7.3. */
    private static final Tr[] REGIMES = {
            PanelText.tr("accident.h2.none", "not flammable", "не горит"),
            PanelText.tr("accident.h2.slow_deflagration", "slow deflagration", "медленное горение"),
            PanelText.tr("accident.h2.complete_deflagration", "deflagration", "дефлаграция"),
            PanelText.tr("accident.h2.fast_deflagration", "FAST DEFLAGRATION", "БЫСТРАЯ ДЕФЛАГРАЦИЯ"),
            PanelText.tr("accident.h2.detonation", "DETONABLE", "ДЕТОНАЦИОННАЯ СМЕСЬ")};
    private static final Tr[] STAGES = {
            PanelText.tr("accident.stage.intact", "intact", "цела"),
            PanelText.tr("accident.stage.clad_ballooning_burst", "cladding burst", "оболочки вздулись и лопнули"),
            PanelText.tr("accident.stage.rapid_oxidation", "rapid oxidation", "бурное окисление"),
            PanelText.tr("accident.stage.control_material_relocation", "absorber relocated", "поглотитель стёк"),
            PanelText.tr("accident.stage.clad_melt_candling", "cladding melting down", "оболочки плавятся и стекают"),
            PanelText.tr("accident.stage.ceramic_melt_pool", "MOLTEN POOL", "БАССЕЙН РАСПЛАВА"),
            PanelText.tr("accident.stage.lower_plenum_relocation", "MELT IN THE LOWER PLENUM", "РАСПЛАВ В НИЖНЕЙ КАМЕРЕ"),
            PanelText.tr("accident.stage.lower_head_failure", "VESSEL FAILED", "КОРПУС ПРОПЛАВЛЕН"),
            PanelText.tr("accident.stage.ex_vessel_mcci", "CORIUM ON THE CONCRETE", "КОРИУМ НА БЕТОНЕ")};
    public static final Tr
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

    /** Melt progression line (vessel plants): stage, lost geometry, lower head, cavity, corium on the concrete. */
    public static Component meltLine(PanelValues v, int base) {
        int flags = v.raw(base + MELT_FLAGS);
        int s = Math.max(0, Math.min(STAGES.length - 1, v.raw(base + MELT_STAGE)));
        Component cavity = (flags & 1 << M_FLOODED) != 0 ? CAVITY_WET.get() : CAVITY_DRY.get();
        Component c = (flags & 1 << M_FAILED) != 0
                ? MELT_OUT.get(STAGES[s].get(), v.num(base + CORIUM_KG, 1000, 1), v.raw(base + ABLATED_M3), cavity)
                : MELT_LINE.get(STAGES[s].get(), v.num(base + MELT_GAMMA, 10, 0), v.raw(base + HEAD_C), cavity);
        return s >= 5 ? c.copy().withStyle(ChatFormatting.DARK_RED) : s > 0 ? c.copy().withStyle(ChatFormatting.RED) : c;
    }

    public static Component meltTip(PanelValues v, int base) {
        return MELT_TIP.get();
    }

    public static Component tip(PanelValues v, int base) {
        int flags = v.raw(base + FLAGS);
        return TIP.get(v.num(base + LEVEL, 10, 0), v.num(base + COVER, 10, 0), v.raw(base + CLAD_C), v.raw(base + PCT_C),
                v.num(base + BREAK_KGS10, 10, 1), (flags & 1 << F_ADS) != 0 ? YES.get() : NO.get(), v.num(base + CONT_H2, 10, 1),
                v.num(base + CONT_STEAM, 10, 0), REGIMES[Math.max(0, Math.min(REGIMES.length - 1, v.raw(base + H2_REGIME)))].get());
    }
}
