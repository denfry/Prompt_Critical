package dev.denfry.promptcritical.mod.panel;

/** Показания щита на клиенте: каналы-целые из меню. */
public interface PanelValues {
    int raw(int ch);

    default double get(int ch, double scale) {
        return raw(ch) / scale;
    }

    default boolean bit(int ch, int bit) {
        return (raw(ch) & 1 << bit) != 0;
    }

    /** Значение канала строкой с {@code decimals} знаками. */
    default String num(int ch, double scale, int decimals) {
        return PanelText.num(get(ch, scale), decimals);
    }
}
