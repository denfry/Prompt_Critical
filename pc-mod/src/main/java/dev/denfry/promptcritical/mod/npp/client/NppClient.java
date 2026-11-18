package dev.denfry.promptcritical.mod.npp.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.npp.NppBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент АЭС: перегрузочная машина CANDU на торце реактора. Только на клиенте. */
public final class NppClient {
    static final PartialModel CANDU_BRIDGE = partial("candu_reactor/partial/bridge");
    static final PartialModel CANDU_HEAD = partial("candu_reactor/partial/head");

    private NppClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(NppClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NppBlockEntities.CANDU_REACTOR.get(), c -> new CanduRenderer());
    }
}
