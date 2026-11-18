package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.battery.LampCharger;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Местный щит A0 зарядного стеллажа ламповой: вольтметр ввода 0,4 кВ, ваттметр выпрямителей, лампы «сеть» и «автомат»,
 * ключ вводного автомата, двенадцать гнёзд под светильники (полоска под иконкой — заряд).
 */
public final class LampRackPanels {
    public static final int SUPPLY_V = 0, POWER_W10 = 1, LAMPS = 2, CHARGING = 3, FULL = 4, TEMP_C10 = 5, REPAIRED = 6,
            FLAGS = 7, CHANNELS = 8;
    public static final int F_SUPPLY = 0, F_BREAKER = 1;
    public static final int BUTTON_BREAKER = 0;

    private static final Tr VOLTS = PanelText.tr("unit.v", "V", "В"),
            WATTS = PanelText.tr("unit.w", "W", "Вт"),
            SUPPLY = PanelText.tr("lamp_rack.supply", "Mains", "Сеть"),
            POWER = PanelText.tr("lamp_rack.power", "Chargers", "Зарядка"),
            BREAKER = PanelText.tr("lamp_rack.breaker", "Breaker", "Автомат"),
            BREAKER_TIP = PanelText.tr("lamp_rack.breaker_tip", "Switch the rack's incoming breaker on or off",
                    "Включить или отключить вводной автомат стеллажа"),
            SLOT = PanelText.tr("lamp_rack.slot", "Cap lamp on charge: %s A to %s V per cell, then constant voltage",
                    "Светильник на заряд: %s А до %s В на элемент, затем постоянным напряжением"),
            SUPPLY_TIP = PanelText.tr("lamp_rack.supply_tip", "Mains %s V · below 340 V the chargers slow down, below 200 V they stop",
                    "Сеть %s В · ниже 340 В заряд медленнее, ниже 200 В не идёт"),
            POWER_TIP = PanelText.tr("lamp_rack.power_tip", "Rectifiers draw %s W from the mains (efficiency %s)",
                    "Выпрямители берут из сети %s Вт (КПД %s)"),
            STATUS = PanelText.tr("lamp_rack.status", "Lamps %s · charging %s · charged %s · batteries up to %s °C · glasses replaced %s",
                    "Ламп %s · на заряде %s · заряжено %s · батареи до %s °C · стёкол заменено %s");

    private static final double CC_AMPS = LampCharger.CC_C_RATE * 10;

    public static final PanelLayout RACK;

    static {
        PanelLayout.Builder b = PanelLayout.builder("lamp_rack", 232, 140, CHANNELS)
                .dial(8, 24, false, SUPPLY_V, 1, 450, Double.NaN, 0, VOLTS.get(), SUPPLY.get(),
                        v -> SUPPLY_TIP.get(v.raw(SUPPLY_V)))
                .dial(64, 24, false, POWER_W10, 10, 250, Double.NaN, 0, WATTS.get(), POWER.get(),
                        v -> POWER_TIP.get(v.num(POWER_W10, 10, 1), PanelText.num(LampCharger.EFFICIENCY, 2)))
                .lamp(122, 26, FLAGS, F_SUPPLY, LampColor.GREEN, SUPPLY.get())
                .lamp(122, 42, FLAGS, F_BREAKER, LampColor.WHITE, BREAKER.get())
                .button(122, 60, 52, BUTTON_BREAKER, BREAKER.get(), BREAKER_TIP.get());
        Component slotTip = SLOT.get(PanelText.num(CC_AMPS, 1), PanelText.num(LampCharger.CV_VOLTS_PER_CELL, 2));
        for (int i = 0; i < LampRackBlockEntity.SLOTS; i++)
            b.slot(8 + 18 * (i % 6), 84 + 18 * (i / 6), i, s -> s.getItem() instanceof CapLampItem, slotTip);
        RACK = b.readout(8, 124, 216, LampRackPanels::status, null).build();
    }

    private LampRackPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(RACK);
    }

    private static Component status(PanelValues v) {
        return STATUS.get(v.raw(LAMPS), v.raw(CHARGING), v.raw(FULL), v.num(TEMP_C10, 10, 0), v.raw(REPAIRED));
    }
}
