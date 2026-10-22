package dev.denfry.promptcritical.mod.powerhouse.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.powerhouse.PowerhouseBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент котла I1: крыльчатки дутьевого вентилятора и дымососа на валу Create. Только на клиенте. */
public final class PowerhouseClient {
    private static final PartialModel FD_IMPELLER = partial("forced_draft_fan/partial/impeller");
    private static final PartialModel ID_IMPELLER = partial("induced_draft_fan/partial/impeller");

    private PowerhouseClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(PowerhouseClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // ось крыльчатки — центр блока, вдоль Z (фасад на север)
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.FORCED_DRAFT_FAN.get(), c -> new FoundryMachineRenderer<>(FD_IMPELLER, Motion.SPIN_Z, 8, 8, 1));
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.INDUCED_DRAFT_FAN.get(), c -> new FoundryMachineRenderer<>(ID_IMPELLER, Motion.SPIN_Z, 8, 8, 1));
    }
}
