package dev.denfry.promptcritical.mod.mes;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щит сервера завода (A5): показатели завода и задание на выработку с кнопками. */
public final class MesPanels {
    public static final int GEN_KW = 0, LOAD_KW = 1, TARGET_KW = 2, COST_CR_H = 3, REVENUE_CR_H = 4, CO2_KG_H = 5,
            EFF_PERMIL = 6, LAMBDA_CENTS = 7, UNITS = 8, FLAGS = 9, CHANNELS = 10;
    public static final int F_LINK = 0, F_FOLLOW = 1, F_SHORT = 2;
    public static final int BTN_DOWN = 0, BTN_UP = 1, BTN_FOLLOW = 2;

    private static final Tr LINK = PanelText.tr("mes.link", "RTU link", "Связь с КП"),
            FOLLOW = PanelText.tr("mes.follow", "Follows load", "По нагрузке"),
            SHORT = PanelText.tr("mes.short", "Short of target", "Недобор"),
            POWER = PanelText.tr("mes.power", "Generation %s MW · load %s MW · target %s MW",
                    "Выработка %s МВт · нагрузка %s МВт · задание %s МВт"),
            MONEY = PanelText.tr("mes.money", "Fuel %s CR/h · sales %s CR/h · λ %s CR/MWh",
                    "Топливо %s CR/ч · продажи %s CR/ч · λ %s CR/МВт·ч"),
            STACK = PanelText.tr("mes.stack", "CO2 %s t/h · dispatch efficiency %s%% · units %s",
                    "CO2 %s т/ч · экономичность распределения %s%% · агрегатов %s"),
            DOWN = PanelText.tr("mes.down", "Target −10%", "Задание −10%"),
            UP = PanelText.tr("mes.up", "Target +10%", "Задание +10%"),
            FOLLOW_BTN = PanelText.tr("mes.follow_btn", "Follow load", "По нагрузке");

    public static final PanelLayout SERVER = PanelLayout.builder("mes_server", 240, 132, CHANNELS)
            .lamp(8, 24, FLAGS, F_LINK, LampColor.GREEN, LINK.get())
            .lamp(80, 24, FLAGS, F_FOLLOW, LampColor.AMBER, FOLLOW.get())
            .lamp(152, 24, FLAGS, F_SHORT, LampColor.RED, SHORT.get())
            .readout(8, 50, 224, MesPanels::power, null)
            .readout(8, 66, 224, MesPanels::money, null)
            .readout(8, 82, 224, MesPanels::stack, null)
            .button(8, 104, 70, BTN_DOWN, DOWN.get(), null)
            .button(84, 104, 70, BTN_UP, UP.get(), null)
            .button(160, 104, 72, BTN_FOLLOW, FOLLOW_BTN.get(), null)
            .build();

    private MesPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(SERVER);
    }

    private static Component power(PanelValues v) {
        return POWER.get(v.num(GEN_KW, 1000, 2), v.num(LOAD_KW, 1000, 2), v.num(TARGET_KW, 1000, 2));
    }

    private static Component money(PanelValues v) {
        return MONEY.get(v.raw(COST_CR_H), v.raw(REVENUE_CR_H), v.num(LAMBDA_CENTS, 100, 2));
    }

    private static Component stack(PanelValues v) {
        return STACK.get(v.num(CO2_KG_H, 1000, 2), v.num(EFF_PERMIL, 10, 1), v.raw(UNITS));
    }
}
