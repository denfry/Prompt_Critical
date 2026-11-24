package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;

/**
 * Каналы ГГС (13 §13.3.4). Приоритет: меньше — важнее, сообщение важнее прерывает идущее. {@code EVAC} и {@code ALL}
 * слышны во всех зонах, остальные — в выбранной. Роли 04d в коде пока нет: говорить может любой из команды станции.
 */
public enum PaChannel {
    EVAC(0, true, "EVAC", "ЭВАК"),
    FIRE_GAS(1, false, "FIRE/GAS", "ПОЖ/ГАЗ"),
    ALL(2, true, "ALL", "ВСЕ"),
    CONTROL(3, false, "CONTROL", "БЩУ"),
    MAINTENANCE(3, false, "MAINT", "РЕМ"),
    CHEM_RAD(3, false, "CHEM/RAD", "ХИМ/РАД");

    public final int priority;
    public final boolean allZones;
    public final Tr label;

    PaChannel(int priority, boolean allZones, String en, String ru) {
        this.priority = priority;
        this.allZones = allZones;
        this.label = PanelText.tr("pa.channel." + name().toLowerCase(java.util.Locale.ROOT), en, ru);
    }

    public static PaChannel of(int i) {
        PaChannel[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
