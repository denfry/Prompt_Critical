package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Щит светосигнального табло (03c §3c.2.7): 12 окон 4×3 по «последовательности A» ISA-18.1 — UNACK мигает 2 Гц цветом
 * приоритета (первопричина — 4 Гц), ACKED горит ровно, RTNUN медленно мигает 0,5 Гц, шельфованное окно погашено с
 * пометкой «Ш», выведенное из работы — «ВР», подавленное погашено. Цвета ISA-101 (03c §3c.3.2): P1 красный, P2
 * оранжевый, P3 жёлтый; форма маркера ▲ / ◆ / ● дублирует цвет для игроков с дальтонизмом. Мигание считает клиент по
 * своим часам — сервер шлёт только состояние.
 *
 * <p>Каналы окна {@code i}: {@code W0 + 2i} — ссылка на прибор ({@link AlarmPoints#ref} + 1) или {@code −id}
 * программной сигнализации, 0 — пусто; {@code W0 + 2i + 1} — состояние (биты 0–3), приоритет (4–7) и флаги.
 */
public final class AnnunciatorPanels {
    public static final int W0 = 0, UNACKED = 24, ACTIVE = 25, SHELVED = 26, SUPPRESSED = 27, RATE = 28, FLAGS = 29, TOTAL = 30,
            MACHINES = 31, MORE = 32, CHANNELS = 33;
    public static final int F_FIRST_OUT = 1 << 8, F_TEST = 1 << 9, F_PRESENT = 1 << 10;
    public static final int B_ACK = 0, B_SILENCE = 1, B_TEST = 2;

    private static final Tr STATUS = PanelText.tr("annunciator.status",
                    "Unack %s · active %s · shelved %s · suppressed %s · 10 min: %s · %s machines",
                    "Не квит. %s · стоят %s · шельф %s · подавл. %s · за 10 мин: %s · машин %s"),
            FLOOD = PanelText.tr("annunciator.flood", "ALARM FLOOD: %s alarms in 10 min", "ЛАВИНА: %s сигнализаций за 10 мин"),
            MORE_TEXT = PanelText.tr("annunciator.more", "+%s more alarms off the windows: see the alarm list",
                    "ещё %s сигнализаций вне окон: смотрите список"),
            HORN = PanelText.tr("annunciator.horn", " · horn P%s", " · сирена P%s"),
            TIP = PanelText.tr("annunciator.tip",
                    "Watches the local panels of machines within 16 blocks: red lamps are priority 2, amber lamps and red dial zones 2–3. Sneak + use: acknowledge all",
                    "Следит за местными щитами машин в 16 блоках: красные лампы — приоритет 2, жёлтые — 3, красная зона прибора — 2. Shift + ПКМ: квитировать всё"),
            ACK = PanelText.tr("annunciator.ack", "ACK", "КВИТ"),
            SILENCE = PanelText.tr("annunciator.silence", "SILENCE", "ЗВУК"),
            TEST = PanelText.tr("annunciator.test", "TEST", "ПРОВ"),
            ACK_TIP = PanelText.tr("annunciator.ack_tip", "Acknowledge every window: flashing turns steady, returned alarms go out",
                    "Квитировать все окна: мигание станет ровным, вернувшиеся в норму погаснут"),
            SILENCE_TIP = PanelText.tr("annunciator.silence_tip", "Silence the horn without acknowledging; a new alarm sounds again",
                    "Заглушить сирену без квитирования; новая сигнализация зазвучит снова"),
            TEST_TIP = PanelText.tr("annunciator.test_tip", "Lamp test: every window flashes for 3 s", "Проверка ламп: все окна мигают 3 с"),
            SOFT = PanelText.tr("annunciator.soft", "Program alarm #%s", "Программная сигнализация №%s"),
            SHELVED_MARK = PanelText.tr("annunciator.shelved_mark", "SH ", "Ш "),
            OOS_MARK = PanelText.tr("annunciator.oos_mark", "OOS ", "ВР "),
            LAMP_TEST = PanelText.tr("annunciator.lamp_test", "LAMP TEST", "ПРОВЕРКА");
    static final Tr ACKED_MSG = PanelText.tr("annunciator.acked", "Acknowledged %s · %s alarms from %s machines",
            "Квитировано %s · сигнализаций %s от машин: %s");

    private static final int CW = 100, CH = 16, X0 = 8, Y0 = 22;

    public static final PanelLayout ANNUNCIATOR;

    static {
        PanelLayout.Builder b = PanelLayout.builder("annunciator_panel", 2 * X0 + 3 * CW + 2 * 4, 150, CHANNELS);
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                b.readout(X0 + c * (CW + 4), Y0 + r * CH, CW, v -> window(v, i), v -> windowTip(v, i));
            }
        int y = Y0 + 4 * CH + 4;
        b.readout(X0, y, 3 * CW + 8, AnnunciatorPanels::status, v -> TIP.get());
        b.readout(X0, y + 16, 3 * CW + 8, AnnunciatorPanels::flood, null);
        b.button(X0, y + 36, 70, B_ACK, ACK.get(), ACK_TIP.get());
        b.button(X0 + 76, y + 36, 70, B_SILENCE, SILENCE.get(), SILENCE_TIP.get());
        b.button(X0 + 152, y + 36, 70, B_TEST, TEST.get(), TEST_TIP.get());
        ANNUNCIATOR = b.noInventory().build();
    }

    private AnnunciatorPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(ANNUNCIATOR);
    }

    /** Фаза мигания с частотой {@code hz} по часам клиента. */
    static boolean blink(double hz) {
        long period = Math.round(1000 / hz);
        return net.minecraft.Util.getMillis() % period < period / 2;
    }

    static ChatFormatting color(int priority) {
        return switch (priority) {
            case 1 -> ChatFormatting.RED;
            case 2 -> ChatFormatting.GOLD;
            case 3 -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GRAY;
        };
    }

    static String marker(int priority) {
        return switch (priority) {
            case 1 -> "▲1 ";
            case 2 -> "◆2 ";
            case 3 -> "●3 ";
            default -> "";
        };
    }

    /** Надпись окна: машина и прибор по ссылке или номер программной сигнализации. */
    public static MutableComponent engraving(int src) {
        if (src < 0) return SOFT.get(-src);
        AlarmPoints.Ref ref = AlarmPoints.ref(src - 1);
        if (ref == null) return Component.empty();
        String key = ref.machineKey();
        MutableComponent machine = net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(ref.layout().id);
        return machine.append(" · ").append(ref.label());
    }

    static Component window(PanelValues v, int i) {
        return windowAt(v, W0 + 2 * i, W0 + 2 * i + 1);
    }

    /** Окно по каналам ссылки {@code srcCh} и состояния {@code flagCh} (табло и список сигнализаций пульта). */
    public static Component windowAt(PanelValues v, int srcCh, int flagCh) {
        int src = v.raw(srcCh), f = v.raw(flagCh);
        if ((f & F_TEST) != 0) return LAMP_TEST.get().withStyle(blink(2) ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
        if ((f & F_PRESENT) == 0) return Component.empty();
        AlarmState state = AlarmState.values()[Math.min(AlarmState.values().length - 1, f & 15)];
        int prio = f >> 4 & 15;
        MutableComponent text = Component.literal(marker(prio)).append(engraving(src));
        ChatFormatting on = color(prio), off = ChatFormatting.DARK_GRAY;
        return switch (state) {
            case UNACK -> text.withStyle(blink((f & F_FIRST_OUT) != 0 ? 4 : 2) ? on : off);
            case ACKED -> text.withStyle(on);
            case RTNUN -> text.withStyle(blink(0.5) ? ChatFormatting.WHITE : off);
            case SHELVED -> SHELVED_MARK.get().append(text).withStyle(off);
            case OOS -> OOS_MARK.get().append(text).withStyle(off);
            default -> text.withStyle(ChatFormatting.GRAY);
        };
    }

    static Component windowTip(PanelValues v, int i) {
        int src = v.raw(W0 + 2 * i), f = v.raw(W0 + 2 * i + 1);
        if ((f & F_PRESENT) == 0) return Component.empty();
        AlarmState state = AlarmState.values()[Math.min(AlarmState.values().length - 1, f & 15)];
        return engraving(src).append(" — " + state.name() + ((f & F_FIRST_OUT) != 0 ? " · FIRST OUT" : ""));
    }

    static Component status(PanelValues v) {
        MutableComponent c = STATUS.get(v.raw(UNACKED), v.raw(ACTIVE), v.raw(SHELVED), v.raw(SUPPRESSED), v.raw(RATE), v.raw(MACHINES));
        int horn = v.raw(FLAGS) >> 1;
        if (horn > 0) c.append(HORN.get(horn));
        return c;
    }

    static Component flood(PanelValues v) {
        if ((v.raw(FLAGS) & 1) != 0) return FLOOD.get(v.raw(RATE)).withStyle(blink(1) ? ChatFormatting.RED : ChatFormatting.DARK_RED);
        if (v.raw(MORE) > 0) return MORE_TEXT.get(v.raw(MORE));
        return Component.empty();
    }
}
