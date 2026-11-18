package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.DosimeterItem;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щит стеллажа бассейна выдержки: тепловыделение, температура, активность, мощность дозы над ним. */
public final class RackPanels {
    public static final int HEAT_W = 0, TEMP_C = 1, ACT_GBQ = 2, DOSE_LOG = 3, FLAGS = 4, CHANNELS = 5;
    public static final int F_WATER = 0, F_DRY = 1, F_HOT = 2, F_CLAD = 3;

    private static final Tr TEMP = PanelText.tr("rack.temp", "Fuel", "Топливо"),
            WATER = PanelText.tr("rack.water", "Under water", "Под водой"),
            DRY = PanelText.tr("rack.dry", "Dry", "Без воды"),
            HOT = PanelText.tr("rack.hot", "Hot", "Перегрев"),
            CLAD = PanelText.tr("rack.clad", "Clad fail", "Оболочки"),
            SLOT = PanelText.tr("rack.slot", "Irradiated fuel or activated part", "Облучённое топливо или активированная деталь"),
            STATUS = PanelText.tr("rack.status", "Decay heat %s W · activity %s GBq · dose above %s",
                    "Остаточное тепло %s Вт · активность %s ГБк · над стеллажом %s");

    public static final PanelLayout RACK;

    static {
        PanelLayout.Builder b = PanelLayout.builder("fuel_rack", 200, 110, CHANNELS)
                .dial(8, 24, false, TEMP_C, 1, 1000, FuelRackBlockEntity.CLAD_LIMIT_C, 0, PanelText.CELSIUS.get(), TEMP.get(), null)
                .lamp(64, 22, FLAGS, F_WATER, LampColor.GREEN, WATER.get())
                .lamp(64, 36, FLAGS, F_DRY, LampColor.AMBER, DRY.get())
                .lamp(64, 50, FLAGS, F_HOT, LampColor.AMBER, HOT.get())
                .lamp(64, 64, FLAGS, F_CLAD, LampColor.RED, CLAD.get());
        for (int i = 0; i < FuelRackBlockEntity.SLOTS; i++)
            b.slot(130 + 18 * (i % 3), 22 + 18 * (i / 3), i, s -> s.has(ActivityData.ACTIVITY.get()), SLOT.get());
        RACK = b.readout(8, 96, 184, RackPanels::status, null).build();
    }

    private RackPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(RACK);
    }

    private static Component status(PanelValues v) {
        double sv = v.raw(DOSE_LOG) > 0 ? Math.pow(10, v.raw(DOSE_LOG) / 100.0 - 10) : 0;
        return STATUS.get(v.raw(HEAT_W), v.raw(ACT_GBQ), DosimeterItem.rate(sv));
    }
}
