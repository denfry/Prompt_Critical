package dev.denfry.promptcritical.mod.chem.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.chem.ChemBlockEntities;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент химии (ПЭВД, азотная кислота): подвижные части машин на валу Create. Только на клиенте. */
public final class ChemClient {
    /** Кривошипный диск гиперкомпрессора ПЭВД (art/blockbench/polymers/ldpe_reactor.author.js), ось z через x 8, y 6. */
    private static final PartialModel LDPE_CRANK = PartialModel.of(PromptCritical.id("block/ldpe_reactor/partial/crank"));

    /** Колесо воздушного компрессора азотной кислоты (art/blockbench/reagents/nitric_acid_unit.author.js), ось z через x 8, y 8. */
    private static final PartialModel NITRIC_IMPELLER = PartialModel.of(PromptCritical.id("block/nitric_acid_unit/partial/impeller"));

    private ChemClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ChemClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ChemBlockEntities.LDPE_REACTOR.get(), c -> new FoundryMachineRenderer<>(LDPE_CRANK, Motion.SPIN_Z, 8, 6, 1));
        // центробежный компрессор быстроходнее вала: визуально ×3 через мультипликатор
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.nitrogen.NitrogenBlockEntities.NITRIC_ACID_UNIT.get(),
                c -> new FoundryMachineRenderer<>(NITRIC_IMPELLER, Motion.SPIN_Z, 8, 8, 3));
    }
}
