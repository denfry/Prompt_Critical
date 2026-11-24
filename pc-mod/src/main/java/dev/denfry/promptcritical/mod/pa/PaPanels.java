package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щиты ГГС (13 §13.3.4): пульт {@code intercom_station} и громкоговоритель {@code plant_loudspeaker}. */
public final class PaPanels {
    public static final int I_CHANNEL = 0, I_ZONE = 1, I_FLAGS = 2, I_SPEAKERS = 3, INTERCOM_CHANNELS = 4;
    public static final int S_ZONE = 0, S_FLAGS = 1, S_RANGE10 = 2, SPEAKER_CHANNELS = 3;
    public static final int F_POWERED = 1, F_ACTIVE = 2, F_VOICE = 4;
    public static final int B_CHANNEL0 = 0, B_PREV = 10, B_NEXT = 11, B_MESSAGE0 = 20;

    static final Tr ZONE = PanelText.tr("pa.zone", "Zone %s", "Зона %s"),
            ALL_ZONES = PanelText.tr("pa.all_zones", "all zones", "все зоны"),
            INTERCOM_STATUS = PanelText.tr("pa.intercom_status", "Channel %s · %s · loudspeakers: %s",
                    "Канал %s · %s · громкоговорителей: %s"),
            TALKING = PanelText.tr("pa.talking", "PTT pressed: live speech on air", "Тангента нажата: живая речь в эфире"),
            HINT = PanelText.tr("pa.hint", "Shift+RMB at the station: press-to-talk (Simple Voice Chat)",
                    "Shift+ПКМ у пульта: тангента (Simple Voice Chat)"),
            HINT_NO_VOICE = PanelText.tr("pa.hint_no_voice", "No Simple Voice Chat: prerecorded messages only",
                    "Нет Simple Voice Chat: только предзаписанные сообщения"),
            FAULT = PanelText.tr("pa.fault", "PA fault: no power", "Неисправность ГГС: нет питания"),
            NOT_YOURS = PanelText.tr("pa.not_yours", "This PA belongs to another station", "Это ГГС другой станции"),
            TALK_ON = PanelText.tr("pa.talk_on", "On air: %s, %s", "В эфире: %s, %s"),
            TALK_NO_VOICE = PanelText.tr("pa.talk_no_voice", "On air: %s, %s (no Simple Voice Chat: nobody hears you)",
                    "В эфире: %s, %s (нет Simple Voice Chat: вас не слышно)"),
            TALK_OFF = PanelText.tr("pa.talk_off", "PTT released", "Тангента отпущена"),
            BUSY = PanelText.tr("pa.busy", "Someone else is on this station", "Пульт занят другим"),
            SENT = PanelText.tr("pa.sent", "Message out of %s loudspeakers", "Сообщение передано в %s громкоговорителей"),
            NOT_SENT = PanelText.tr("pa.not_sent", "Not sent: no loudspeakers in the zone, or a higher-priority message is on",
                    "Не передано: в зоне нет громкоговорителей или идёт сообщение важнее"),
            ZONE_FULL = PanelText.tr("pa.zone_full", "Zone %s already has the most loudspeakers allowed",
                    "В зоне %s уже предельное число громкоговорителей"),
            SPEAKER_STATUS = PanelText.tr("pa.speaker_status", "%s · intelligible within %s m", "%s · разборчиво в %s м"),
            ON_AIR = PanelText.tr("pa.on_air", "on air", "в эфире"),
            IDLE = PanelText.tr("pa.idle", "standby", "дежурный режим"),
            OFF = PanelText.tr("pa.off", "no power", "нет питания"),
            ZONE_PREV = PanelText.tr("pa.zone_prev", "<", "<"),
            ZONE_NEXT = PanelText.tr("pa.zone_next", ">", ">"),
            ZONE_TIP = PanelText.tr("pa.zone_tip", "Alert zone, as the zones of the fire alarm panel",
                    "Зона оповещения, как зоны ППК");

    public static final PanelLayout INTERCOM = intercom(), SPEAKER = speaker();

    private PaPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(INTERCOM, SPEAKER);
    }

    private static PanelLayout intercom() {
        PanelLayout.Builder b = PanelLayout.builder("intercom_station", 232, 122, INTERCOM_CHANNELS);
        PaChannel[] ch = PaChannel.values();
        for (int i = 0; i < ch.length; i++) b.button(8 + 36 * i, 24, 34, B_CHANNEL0 + i, ch[i].label.get(), null);
        b.readout(8, 46, 140, v -> ZONE.get(v.raw(I_ZONE)), v -> ZONE_TIP.get());
        b.button(152, 42, 36, B_PREV, ZONE_PREV.get(), null).button(192, 42, 32, B_NEXT, ZONE_NEXT.get(), null);
        b.readout(8, 64, 216, PaPanels::intercomStatus, null);
        PaMessage[] m = PaMessage.values();
        for (int i = 0; i < m.length; i++) b.button(8 + 54 * i, 82, 50, B_MESSAGE0 + i, m[i].button.get(), m[i].text.get());
        b.readout(8, 104, 216, v -> (v.raw(I_FLAGS) & F_VOICE) != 0 ? HINT.get() : HINT_NO_VOICE.get(), null);
        return b.art(PromptCritical.id("textures/gui/panel/intercom_station.png")).noInventory().build();
    }

    private static PanelLayout speaker() {
        return PanelLayout.builder("plant_loudspeaker", 232, 60, SPEAKER_CHANNELS)
                .readout(8, 24, 140, v -> ZONE.get(v.raw(S_ZONE)), v -> ZONE_TIP.get())
                .button(152, 20, 36, B_PREV, ZONE_PREV.get(), null).button(192, 20, 32, B_NEXT, ZONE_NEXT.get(), null)
                .readout(8, 42, 216, v -> SPEAKER_STATUS.get((v.raw(S_FLAGS) & F_POWERED) == 0 ? OFF.get()
                        : (v.raw(S_FLAGS) & F_ACTIVE) != 0 ? ON_AIR.get() : IDLE.get(), PanelText.num(v.raw(S_RANGE10) / 10.0, 1)), null)
                .art(PromptCritical.id("textures/gui/panel/plant_loudspeaker.png")).noInventory().build();
    }

    private static Component intercomStatus(PanelValues v) {
        int f = v.raw(I_FLAGS);
        if ((f & F_POWERED) == 0) return FAULT.get();
        if ((f & F_ACTIVE) != 0) return TALKING.get();
        PaChannel c = PaChannel.of(v.raw(I_CHANNEL));
        return INTERCOM_STATUS.get(c.label.get(), c.allZones ? ALL_ZONES.get() : ZONE.get(v.raw(I_ZONE)), v.raw(I_SPEAKERS));
    }
}
