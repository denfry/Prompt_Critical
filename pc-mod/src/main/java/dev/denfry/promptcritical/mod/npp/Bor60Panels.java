package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.npp.SodiumFastReactor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Пульт БОР-60 (art/blockbench/core/reactor_panels.md, текстура bor60.png): температура натрия на выходе из зоны,
 * давление аргоновой подушки, лампа течи натрия (пожар в помещении или вода в натрии ПГ), расходы натрия первого и
 * второго контуров, перепад на ПТО и ключ АЗ под крышкой — срабатывает при удержании {@value Vver70Panels#AZ_HOLD_MS}
 * мс. Каналы {@link SfrPanels} плюс аргон, расход второго контура и перепад ПТО.
 */
public final class Bor60Panels {
    public static final int ARGON_KPA = SfrPanels.CHANNELS, INT_FLOW = ARGON_KPA + 1, IHX_DT = INT_FLOW + 1, IHX_PUMPS = IHX_DT + 1,
            INT_COLD_C = IHX_PUMPS + 1, CHANNELS = INT_COLD_C + 1;
    /** Лампа течи натрия: бит флагов {@link SfrPanels#FLAGS}, собранный из пожара и реакции натрий–вода. */
    public static final int F_NA_LEAK = 7;

    private static final Tr DEG = PanelText.tr("sfr.deg_c", "°C", "°C"),
            KPA = PanelText.tr("unit.kpa", "kPa", "кПа"),
            T_OUT = PanelText.tr("bor60.t_out", "Core outlet", "Выход из зоны"),
            T_OUT_TIP = PanelText.tr("bor60.t_out_tip", "Sodium %s → %s °C · power %s %% · scram at %s °C",
                    "Натрий %s → %s °C · мощность %s %% · АЗ при %s °C"),
            ARGON = PanelText.tr("bor60.argon", "Argon", "Аргон"),
            ARGON_TIP = PanelText.tr("bor60.argon_tip", "Argon cover gas over the primary sodium %s kPa · gas system holds %s–%s kPa",
                    "Аргоновая подушка над натрием первого контура %s кПа · газовая система держит %s–%s кПа"),
            LEAK = PanelText.tr("bor60.na_leak", "Na leak", "Течь Na"),
            FLOW1 = PanelText.tr("bor60.flow1", "Primary Na flow", "Расход Na 1К"),
            FLOW2 = PanelText.tr("bor60.flow2", "Secondary Na flow", "Расход Na 2К"),
            FLOW2_TIP = PanelText.tr("bor60.flow2_tip", "Secondary sodium pumps powered: %s of 2", "Насосы второго контура под питанием: %s из 2"),
            IHX = PanelText.tr("bor60.ihx", "IHX ΔT %s K · secondary %s → %s °C · steam %s kg/s", "ПТО ΔT %s K · 2К %s → %s °C · пар %s кг/с"),
            AZ = PanelText.tr("v70.az", "AZ", "АЗ"),
            AZ_TIP = PanelText.tr("v70.az_tip", "Lift the guard and hold for 1.5 s to scram the reactor",
                    "Откинуть крышку и держать 1,5 с — аварийная защита реактора"),
            // пуск с пульта: взвод АЗ и привод регулирующих стержней (те же ключи, что у пульта БН)
            RESET = PanelText.tr("npp.reset", "Cock", "Взвод"),
            RESET_TIP = PanelText.tr("bwr.reset_tip", "Reset the scram (only with the cause gone and rods in)", "Сбросить АЗ (причина ушла, стержни внизу)"),
            UP = PanelText.tr("npp.rr_up", "▲", "▲"), STOP = PanelText.tr("npp.rr_stop", "■", "■"), DOWN = PanelText.tr("npp.rr_down", "▼", "▼"),
            UP_TIP = PanelText.tr("bwr.up_tip", "Withdraw the control rods", "Извлекать регулирующие стержни"),
            STOP_TIP = PanelText.tr("bwr.stop_tip", "Stop the control rods", "Остановить регулирующие стержни"),
            DOWN_TIP = PanelText.tr("bwr.down_tip", "Insert the control rods", "Вводить регулирующие стержни");

    private static final SodiumFastReactor.Design D = SodiumFastReactor.BOR60;

    public static final PanelLayout BOARD = PanelLayout.builder("bor60", 220, 200, CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/bor60.png"))
            .dial(12, 24, true, SfrPanels.OUTLET_C, 1, 700, D.tripOutletK() - 273.15, 0, DEG.get(), T_OUT.get(),
                    v -> T_OUT_TIP.get(v.raw(SfrPanels.INLET_C), v.raw(SfrPanels.OUTLET_C), v.num(SfrPanels.POWER, 10, 1),
                            Math.round(D.tripOutletK() - 273.15)))
            .dial(92, 32, false, ARGON_KPA, 1, 300, D.argonNomPa() * (1 + SodiumFastReactor.ARGON_BAND) / 1e3, 0, KPA.get(), ARGON.get(),
                    v -> ARGON_TIP.get(v.raw(ARGON_KPA), Math.round(D.argonNomPa() * (1 - SodiumFastReactor.ARGON_BAND) / 1e3),
                            Math.round(D.argonNomPa() * (1 + SodiumFastReactor.ARGON_BAND) / 1e3)))
            .lamp(154, 40, SfrPanels.FLAGS, F_NA_LEAK, LampColor.RED, LEAK.get())
            .bar(12, 114, 196, SfrPanels.FLOW, 1, 120, 0xFFD0A040, FLOW1.get(), null)
            .bar(12, 134, 196, INT_FLOW, 1, 120, 0xFFC08030, FLOW2.get(), v -> FLOW2_TIP.get(v.raw(IHX_PUMPS)))
            .readout(12, 148, 196, Bor60Panels::ihx, null)
            .button(12, 174, 36, SfrPanels.B_RESET, RESET.get(), RESET_TIP.get())
            .holdButton(72, 174, 76, SfrPanels.B_SCRAM, Vver70Panels.AZ_HOLD_MS, AZ.get(), AZ_TIP.get())
            .button(154, 174, 18, SfrPanels.B_UP, UP.get(), UP_TIP.get())
            .button(172, 174, 18, SfrPanels.B_STOP, STOP.get(), STOP_TIP.get())
            .button(190, 174, 18, SfrPanels.B_DOWN, DOWN.get(), DOWN_TIP.get())
            .noInventory()
            .build();

    private Bor60Panels() {
    }

    private static Component ihx(PanelValues v) {
        Component c = IHX.get(v.raw(IHX_DT), v.raw(INT_COLD_C), v.raw(SfrPanels.INT_HOT_C), v.num(SfrPanels.STEAM, 10, 1));
        return v.bit(SfrPanels.FLAGS, SfrPanels.F_NA_WATER) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}
