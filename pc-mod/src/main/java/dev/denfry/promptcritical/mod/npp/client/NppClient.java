package dev.denfry.promptcritical.mod.npp.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.npp.NppBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент АЭС: подвижные части машин на валу Create и перегрузочная машина CANDU. Только на клиенте. */
public final class NppClient {
    /** Муфта ГЦН (art/blockbench/mechanics/primary_pump.author.js), ось z через x 8, y 7. */
    private static final PartialModel PUMP_COUPLING = PartialModel.of(PromptCritical.id("block/primary_pump/partial/coupling"));
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
        event.registerBlockEntityRenderer(NppBlockEntities.PRIMARY_PUMP.get(), c -> new FoundryMachineRenderer<>(PUMP_COUPLING, Motion.SPIN_Z, 8, 7, 1));
        event.registerBlockEntityRenderer(NppBlockEntities.CANDU_REACTOR.get(), c -> new CanduRenderer());
    }
}
