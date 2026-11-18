package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Щиты помещения управления (03c §3c.9.1): ядро помещения (обитаемость) и секция пульта БЩУ (обзор L1 и список сигнализаций). */
public final class ControlRoomPanels {
    // ядро помещения
    public static final int C_VOLUME = 0, C_DOSE_NSV_H = 1, C_FIRES = 2, C_STATE = 3, C_CAUSE = 4, C_FLAGS = 5, C_CHANNELS = 6;
    public static final int CF_DEGRADED = 0, CF_UNINHABITABLE = 1;
    // пульт: 8 строк списка по 2 канала, затем сводка
    public static final int R0 = 0, D_P1 = 16, D_P2 = 17, D_P3 = 18, D_UNACKED = 19, D_SHELVED = 20, D_SUPPRESSED = 21, D_RATE = 22,
            D_FLAGS = 23, D_ANNS = 24, D_MCR = 25, D_MCR_CAUSE = 26, D_IN_ROOM = 27, D_PAGE = 28, D_PAGES = 29, D_TOTAL = 30, D_CHANNELS = 31;
    public static final int B_ACK_ALL = 0, B_SILENCE_ALL = 1, B_PREV = 2, B_NEXT = 3;

    private static final Tr VOLUME = PanelText.tr("mcr.volume", "Room: %s blocks of air", "Помещение: %s блоков воздуха"),
            OPEN = PanelText.tr("mcr.open", "Not a closed room (over 4096 blocks of air or no air): close the walls and use the core",
                    "Не замкнутое помещение (больше 4096 блоков воздуха или нет воздуха): закройте стены и нажмите на ядро"),
            DOSE = PanelText.tr("mcr.dose", "Dose rate %s µSv/h · fires %s", "Мощность дозы %s мкЗв/ч · очагов огня %s"),
            DEGRADED = PanelText.tr("mcr.degraded", "Degraded", "Ухудшено"),
            UNINHABITABLE = PanelText.tr("mcr.uninhabitable", "Uninhabitable", "Необитаемо"),
            TIP = PanelText.tr("mcr.tip",
                    "Degraded above 69 µSv/h (50 mSv over a 30-day accident) or with fire; uninhabitable above 10 mSv/h or with 4 fires. Annunciators within 16 blocks alarm on both lamps",
                    "Ухудшено выше 69 мкЗв/ч (50 мЗв за 30 суток аварии) или при огне; необитаемо выше 10 мЗв/ч или при 4 очагах. Табло в 16 блоках сигналят по обеим лампам");
    private static final Tr[] STATE = {
            PanelText.tr("mcr.state.normal", "Habitable", "Обитаемо"), DEGRADED, UNINHABITABLE};
    private static final Tr[] CAUSE = {
            PanelText.tr("mcr.cause.none", "", ""), PanelText.tr("mcr.cause.dose", "dose rate", "мощность дозы"),
            PanelText.tr("mcr.cause.temperature", "air temperature", "температура воздуха"), PanelText.tr("mcr.cause.smoke", "smoke", "дым"),
            PanelText.tr("mcr.cause.power", "instrument power", "питание приборов")};
    static final Tr RESCANNED = PanelText.tr("mcr.rescanned", "Control room volume: %s blocks of air (0 — not closed)",
            "Объём щитовой: %s блоков воздуха (0 — не замкнута)");

    private static final Tr SUMMARY = PanelText.tr("desk.summary", "P1 %s · P2 %s · P3 %s · unack %s · shelved %s · suppressed %s · 10 min %s",
                    "P1 %s · P2 %s · P3 %s · не квит. %s · шельф %s · подавл. %s · за 10 мин %s"),
            FLOOD = PanelText.tr("desk.flood", "ALARM FLOOD", "ЛАВИНА СИГНАЛИЗАЦИЙ"),
            PAGE = PanelText.tr("desk.page", "Alarms %s · page %s/%s · %s annunciators within 32 blocks",
                    "Сигнализаций %s · стр. %s/%s · табло в 32 блоках: %s"),
            MCR = PanelText.tr("desk.mcr", "Control room: %s %s%s", "Щитовая: %s %s%s"),
            NO_MCR = PanelText.tr("desk.no_mcr", "No control room core nearby", "Рядом нет ядра щитовой"),
            OUTSIDE = PanelText.tr("desk.outside", " · desk outside the room", " · пульт вне помещения"),
            EMPTY = PanelText.tr("desk.empty", "No alarms: every window is dark", "Сигнализаций нет: все окна погашены"),
            ACK_ALL = PanelText.tr("desk.ack_all", "ACK ALL", "КВИТ ВСЁ"),
            SILENCE_ALL = PanelText.tr("desk.silence_all", "SILENCE", "ЗВУК"),
            PREV = PanelText.tr("desk.prev", "<", "<"),
            NEXT = PanelText.tr("desk.next", ">", ">"),
            TIP_DESK = PanelText.tr("desk.tip",
                    "The operator's overview: every annunciator within 32 blocks, unacknowledged first. Sneak + use: acknowledge all",
                    "Обзор оператора: все табло в 32 блоках, неквитированные сверху. Shift + ПКМ: квитировать всё");
    static final Tr DESK_ACKED = PanelText.tr("desk.acked", "Acknowledged %s alarms on %s annunciators",
            "Квитировано сигнализаций: %s на табло: %s");

    public static final PanelLayout CORE = PanelLayout.builder("control_room_core", 232, 96, C_CHANNELS)
            .readout(8, 22, 216, ControlRoomPanels::volume, v -> TIP.get())
            .readout(8, 38, 216, v -> DOSE.get(PanelText.num(v.raw(C_DOSE_NSV_H) / 1000.0, 2), v.raw(C_FIRES)), null)
            .readout(8, 54, 216, ControlRoomPanels::state, null)
            .lamp(8, 72, C_FLAGS, CF_DEGRADED, LampColor.AMBER, DEGRADED.get())
            .lamp(110, 72, C_FLAGS, CF_UNINHABITABLE, LampColor.RED, UNINHABITABLE.get())
            .noInventory().build();

    public static final PanelLayout DESK;

    static {
        PanelLayout.Builder b = PanelLayout.builder("control_desk", 324, 230, D_CHANNELS);
        b.readout(8, 22, 308, ControlRoomPanels::page, v -> TIP_DESK.get());
        for (int i = 0; i < 8; i++) {
            int row = i;
            b.readout(8, 40 + 16 * i, 308, v -> row(v, row), null);
        }
        b.readout(8, 172, 308, ControlRoomPanels::summary, null);
        b.readout(8, 188, 308, ControlRoomPanels::mcr, null);
        b.button(8, 206, 80, B_ACK_ALL, ACK_ALL.get(), null);
        b.button(94, 206, 70, B_SILENCE_ALL, SILENCE_ALL.get(), null);
        b.button(256, 206, 28, B_PREV, PREV.get(), null);
        b.button(288, 206, 28, B_NEXT, NEXT.get(), null);
        DESK = b.noInventory().build();
    }

    private ControlRoomPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CORE, DESK);
    }

    private static Component volume(PanelValues v) {
        return v.raw(C_VOLUME) < 0 ? OPEN.get().withStyle(ChatFormatting.GOLD) : VOLUME.get(v.raw(C_VOLUME));
    }

    private static Component state(PanelValues v) {
        int s = Math.clamp(v.raw(C_STATE), 0, STATE.length - 1), c = Math.clamp(v.raw(C_CAUSE), 0, CAUSE.length - 1);
        MutableComponent t = STATE[s].get();
        if (c > 0) t.append(" — ").append(CAUSE[c].get());
        return t.withStyle(s == 2 ? ChatFormatting.RED : s == 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY);
    }

    private static Component page(PanelValues v) {
        return PAGE.get(v.raw(D_TOTAL), v.raw(D_PAGE) + 1, v.raw(D_PAGES), v.raw(D_ANNS));
    }

    private static Component row(PanelValues v, int i) {
        if ((v.raw(R0 + 2 * i + 1) & AnnunciatorPanels.F_PRESENT) == 0)
            return i == 0 && v.raw(D_TOTAL) == 0 ? EMPTY.get().withStyle(ChatFormatting.GRAY) : Component.empty();
        return AnnunciatorPanels.windowAt(v, R0 + 2 * i, R0 + 2 * i + 1);
    }

    private static Component summary(PanelValues v) {
        if ((v.raw(D_FLAGS) & 1) != 0 && AnnunciatorPanels.blink(1))
            return FLOOD.get().withStyle(ChatFormatting.RED);
        return SUMMARY.get(v.raw(D_P1), v.raw(D_P2), v.raw(D_P3), v.raw(D_UNACKED), v.raw(D_SHELVED), v.raw(D_SUPPRESSED), v.raw(D_RATE));
    }

    private static Component mcr(PanelValues v) {
        int s = v.raw(D_MCR);
        if (s < 0) return NO_MCR.get().withStyle(ChatFormatting.GRAY);
        int c = Math.clamp(v.raw(D_MCR_CAUSE), 0, CAUSE.length - 1);
        Component outside = v.raw(D_IN_ROOM) == 0 ? OUTSIDE.get() : Component.empty();
        return MCR.get(STATE[Math.clamp(s, 0, 2)].get(), CAUSE[c].get(), outside)
                .withStyle(s == 2 ? ChatFormatting.RED : s == 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY);
    }
}
