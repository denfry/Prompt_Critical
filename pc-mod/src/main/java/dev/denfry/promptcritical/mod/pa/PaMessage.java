package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;

/**
 * Предзаписанные сообщения пульта ГГС (13 §13.3.4): сигнал и фраза, текст дублируется субтитром в зоне слышимости.
 * Канал задан сообщением: эвакуация и отбой — во все зоны, пожар и газ — в выбранную.
 */
public enum PaMessage {
    EVACUATE(PaChannel.EVAC, "Evacuate", "Эвакуация", "Evacuate the building by the nearest exit", "Покинуть помещение по ближайшему выходу"),
    FIRE(PaChannel.FIRE_GAS, "Fire", "Пожар", "Fire in the zone: leave the area", "Пожар в зоне: покинуть помещение"),
    GAS(PaChannel.FIRE_GAS, "Gas", "Газ", "Gas in the zone: put on breathing protection and leave", "Загазованность: надеть СИЗОД и покинуть зону"),
    ALL_CLEAR(PaChannel.ALL, "All clear", "Отбой", "All clear: return to work", "Отбой тревоги: вернуться к работе");

    public final PaChannel channel;
    public final Tr button, text;

    PaMessage(PaChannel channel, String enButton, String ruButton, String en, String ru) {
        this.channel = channel;
        String key = "pa.message." + name().toLowerCase(java.util.Locale.ROOT);
        this.button = PanelText.tr(key, enButton, ruButton);
        this.text = PanelText.tr(key + ".text", en, ru);
    }
}
