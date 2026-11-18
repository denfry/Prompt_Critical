package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.List;

/** Щиты A0 нефтегаза: ёмкость СУГ (09 §9.8.4, §9.11.6), камеры скребка и СКЗ (§9.8.2–9.8.3), уровнемер резервуара (§9.8.4). */
public final class HazardPanels {
    // ёмкость СУГ
    public static final int WALL_C = 0, PRESSURE = 1, FILL = 2, FIRE = 3, FLAGS = 4, CHANNELS = 5;
    public static final int F_RELIEF = 0, F_DELUGE = 1, F_FIRE = 2, F_HOT = 3;

    private static final Tr WALL = PanelText.tr("hazard.wall", "Wall", "Стенка"),
            BAR = PanelText.tr("hazard.bar", "bar", "бар"), PRESSURE_L = PanelText.tr("hazard.pressure", "Pressure", "Давление"),
            KW = PanelText.tr("hazard.kw", "kW/m²", "кВт/м²"), FIRE_L = PanelText.tr("hazard.fire", "Fire flux", "Поток пожара"),
            PROPANE = PanelText.tr("hazard.propane", "Propane", "Пропан"),
            RELIEF = PanelText.tr("hazard.relief", "Relief valve lifting", "Сброс через ПК"),
            DELUGE = PanelText.tr("hazard.deluge", "Deluge cooling", "Орошение"),
            ON_FIRE = PanelText.tr("hazard.on_fire", "Exposed to fire", "Под огнём"),
            HOT = PanelText.tr("hazard.hot", "Wall above 300 °C: BLEVE risk", "Стенка выше 300 °C: опасность BLEVE"),
            BULLET_STATUS = PanelText.tr("hazard.bullet_status", "Propane %s t · liquid %s °C · fails at %s bar",
                    "Пропан %s т · жидкость %s °C · разрушение при %s бар");
    /** Ёмкость СУГ: дополнительные каналы для строки состояния. */
    public static final int MASS_T10 = 5, LIQUID_C = 6, FAIL_BAR = 7, BULLET_CHANNELS = 8;

    public static final PanelLayout BULLET = PanelLayout.builder("gas_storage_bullet", 232, 140, BULLET_CHANNELS)
            .dial(8, 24, false, WALL_C, 1, 800, 300, 0, PanelText.CELSIUS.get(), WALL.get(), null)
            .dial(64, 24, false, PRESSURE, 10, 30, 18, 1, BAR.get(), PRESSURE_L.get(), null)
            .dial(120, 24, false, FIRE, 1, 300, 37.5, 0, KW.get(), FIRE_L.get(), null)
            .glass(176, 24, 56, FILL, 0xFFE8E0C0, Double.NaN, 0.85, PROPANE.get(), null)
            .lamp(200, 22, FLAGS, F_RELIEF, LampColor.AMBER, RELIEF.get()).lamp(200, 36, FLAGS, F_DELUGE, LampColor.GREEN, DELUGE.get())
            .lamp(200, 50, FLAGS, F_FIRE, LampColor.RED, ON_FIRE.get()).lamp(200, 64, FLAGS, F_HOT, LampColor.RED, HOT.get())
            .readout(8, 118, 216, v -> BULLET_STATUS.get(v.num(MASS_T10, 10, 1), v.raw(LIQUID_C), v.num(FAIL_BAR, 10, 1)), null).build();

    private HazardPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(BULLET);
    }
}
