package dev.denfry.promptcritical.mod.paperwork;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Вахтенный (оперативный) журнал: записи обхода и подписи сдачи смены. Ведётся от руки — в хронологическом порядке,
 * без пропусков и исправлений: запись нельзя удалить, новая идёт ниже последней. Порядок ведения оперативного журнала
 * — «Правила технической эксплуатации электроустановок потребителей» (ПТЭЭП, приказ Минэнерго России от 13.01.2003
 * № 6, разд. 1.8; с 2023 г. — «Правила технической эксплуатации электроустановок потребителей электрической энергии»,
 * приказ Минэнерго России от 12.08.2022 № 811) и «Правила работы с персоналом в организациях электроэнергетики»
 * (приказ Минэнерго России от 22.09.2020 № 796: приёмка-сдача смены с записью и подписями) [сверить пункты].
 *
 * @param entries записи, старые первыми
 */
public record ShiftLog(List<Entry> entries) {
    /** Журнал на 200 записей — как сшитая книга; заполненный сдают в архив и заводят новый. */
    public static final int CAPACITY = 200;
    /** Больше показаний с одного щита в строку журнала не пишут. */
    public static final int MAX_VALUES = 8;
    public static final ShiftLog EMPTY = new ShiftLog(List.of());

    /**
     * Запись: показания машины или подпись сдачи смены.
     *
     * @param handover {@code true} — «смену сдал»; {@code machine} и {@code values} пусты
     * @param day      сутки мира
     * @param minute   минута суток
     * @param author   кто записал
     * @param machine  машина
     * @param values   показания «название: значение единица»
     */
    public record Entry(boolean handover, long day, int minute, String author, Component machine, List<Component> values) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("handover", false).forGetter(Entry::handover),
                Codec.LONG.fieldOf("day").forGetter(Entry::day),
                Codec.INT.fieldOf("minute").forGetter(Entry::minute),
                Codec.STRING.fieldOf("author").forGetter(Entry::author),
                ComponentSerialization.CODEC.optionalFieldOf("machine", Component.empty()).forGetter(Entry::machine),
                ComponentSerialization.CODEC.listOf().optionalFieldOf("values", List.of()).forGetter(Entry::values))
                .apply(i, Entry::new));

        public Entry {
            values = List.copyOf(values);
        }
    }

    public static final Codec<ShiftLog> CODEC = Entry.CODEC.listOf().xmap(ShiftLog::new, ShiftLog::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, ShiftLog> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public ShiftLog {
        entries = List.copyOf(entries);
    }

    public boolean full() {
        return entries.size() >= CAPACITY;
    }

    /** Журнал с ещё одной записью в конце; заполненный не меняется. */
    public ShiftLog with(Entry e) {
        if (full()) return this;
        List<Entry> out = new ArrayList<>(entries);
        out.add(e);
        return new ShiftLog(out);
    }

    /** Записи после последней сдачи смены — текущая вахта. */
    public int currentShiftEntries() {
        int n = 0;
        for (int i = entries.size() - 1; i >= 0 && !entries.get(i).handover(); i--) n++;
        return n;
    }

    /**
     * Показания щита «как на приборах»: стрелочные приборы, шкалы, уровни, горящие лампы и табло — то, что видит
     * обходчик на местном щите A0 (кнопки, слоты и подписи не пишут).
     */
    public static List<Component> readings(PanelHost host) {
        PanelLayout layout = host.panelLayout();
        int[] raw = new int[layout.channels];
        host.readPanel(raw);
        PanelValues v = ch -> ch >= 0 && ch < raw.length ? raw[ch] : 0;
        List<Component> out = new ArrayList<>();
        for (PanelLayout.Element e : layout.elements) {
            if (out.size() >= MAX_VALUES) break;
            Component line = switch (e) {
                case PanelLayout.Dial d -> Component.empty().append(d.label()).append(": "
                        + PanelText.num(v.get(d.ch(), d.scale()), d.decimals()) + " ").append(d.unit());
                case PanelLayout.Bar b -> b.tip() != null
                        ? Component.empty().append(b.label()).append(": ").append(b.tip().apply(v))
                        : Component.empty().append(b.label()).append(": " + PanelText.num(v.get(b.ch(), b.scale()), 1));
                case PanelLayout.Glass g -> Component.empty().append(g.label()).append(": " + PanelText.num(v.get(g.ch(), 10), 0) + " %");
                case PanelLayout.UTube u -> Component.empty().append(u.label()).append(": " + PanelText.num(v.get(u.ch(), u.scale()), 1));
                case PanelLayout.Lamp l -> v.bit(l.ch(), l.bit()) ? Component.empty().append(l.label()).append(" ●") : null;
                case PanelLayout.Readout r -> r.text().apply(v).copy().withStyle(ChatFormatting.RESET);
                default -> null;
            };
            if (line != null && !line.getString().isBlank()) out.add(line);
        }
        return out;
    }
}
