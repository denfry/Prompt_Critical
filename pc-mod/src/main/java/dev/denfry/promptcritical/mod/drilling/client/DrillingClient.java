package dev.denfry.promptcritical.mod.drilling.client;

import dev.denfry.promptcritical.mod.drilling.DrillingBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Клиент буровой: цельные модели и подвижные части блоков реального масштаба (ротор, лебёдка, буровой насос, секции
 * вышки). Старые блоки рисуются своими состояниями и мультипартом, как раньше. Только на клиенте.
 */
public final class DrillingClient {
    private DrillingClient() {
    }

    public static void register(IEventBus modBus) {
        DrillingRealRenderer.init();
        modBus.addListener(DrillingClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DrillingBlockEntities.ROTARY_TABLE.get(), c -> DrillingRealRenderer.rotary());
        event.registerBlockEntityRenderer(DrillingBlockEntities.MUD_PUMP.get(), c -> DrillingRealRenderer.mudPump());
        event.registerBlockEntityRenderer(DrillingBlockEntities.DRAWWORKS_REAL.get(), c -> DrillingRealRenderer.drawworks());
        event.registerBlockEntityRenderer(DrillingBlockEntities.DERRICK_REAL.get(), c -> DrillingRealRenderer.derrick());
    }
}
