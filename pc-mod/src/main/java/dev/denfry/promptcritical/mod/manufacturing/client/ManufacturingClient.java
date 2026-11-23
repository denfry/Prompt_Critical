package dev.denfry.promptcritical.mod.manufacturing.client;

import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingText;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Клиент производства деталей: подсказка паспорта детали. Только на клиенте. */
public final class ManufacturingClient {
    private ManufacturingClient() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ManufacturingClient::tooltip);
    }

    private static void tooltip(ItemTooltipEvent event) {
        PartSpec p = event.getItemStack().get(ManufacturingComponents.PART_SPEC.get());
        if (p != null) ManufacturingText.tooltip(p, event.getToolTip());
    }
}
