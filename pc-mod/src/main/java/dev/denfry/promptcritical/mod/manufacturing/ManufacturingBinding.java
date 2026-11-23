package dev.denfry.promptcritical.mod.manufacturing;

import net.neoforged.bus.api.IEventBus;

/** Подключение производства деталей к шине мода: компоненты предметов. */
public final class ManufacturingBinding {
    private ManufacturingBinding() {
    }

    public static void register(IEventBus modBus) {
        ManufacturingComponents.COMPONENTS.register(modBus);
        ManufacturingItems.ITEMS.register(modBus);
        SmithyBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SmithyBlockEntities::capabilities);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HotHandling::onPlayerTick);
    }
}
