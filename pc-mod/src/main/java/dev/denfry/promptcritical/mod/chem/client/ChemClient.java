package dev.denfry.promptcritical.mod.chem.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.chem.ChemBlockEntities;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент химии: подвижные части машин на валу Create. Только на клиенте. */
public final class ChemClient {
    /** Кривошипный диск гиперкомпрессора ПЭВД (art/blockbench/polymers/ldpe_reactor.author.js), ось z через x 8, y 6. */
    private static final PartialModel LDPE_CRANK = PartialModel.of(PromptCritical.id("block/ldpe_reactor/partial/crank"));

    private ChemClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ChemClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ChemBlockEntities.LDPE_REACTOR.get(), c -> new FoundryMachineRenderer<>(LDPE_CRANK, Motion.SPIN_Z, 8, 6, 1));
    }
}
