package dev.denfry.promptcritical.mod.production.client;

import dev.denfry.promptcritical.mod.production.ProductionBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Клиент добычи: цельные модели и подвижные части качалки и сепаратора реального масштаба. Старые блоки рисуются
 * своими состояниями и мультипартом, как раньше. Только на клиенте.
 */
public final class ProductionClient {
    private ProductionClient() {
    }

    public static void register(IEventBus modBus) {
        ProductionRealRenderer.init();
        TransportRealRenderer.init();
        modBus.addListener(ProductionClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ProductionBlockEntities.PUMPJACK.get(), c -> ProductionRealRenderer.pumpjack());
        event.registerBlockEntityRenderer(ProductionBlockEntities.SEPARATOR.get(), c -> ProductionRealRenderer.separator());
        event.registerBlockEntityRenderer(ProductionBlockEntities.MAINLINE_PUMP.get(), c -> TransportRealRenderer.mainlinePump());
        event.registerBlockEntityRenderer(ProductionBlockEntities.LINE_HEATER.get(), c -> TransportRealRenderer.lineHeater());
        event.registerBlockEntityRenderer(ProductionBlockEntities.OIL_TANK.get(), c -> TransportRealRenderer.oilTank());
    }
}
