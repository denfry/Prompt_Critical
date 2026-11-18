package dev.denfry.promptcritical.mod.chlor.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.chlor.ChlorBlockEntities;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент хлорного цеха: рендер подвижных частей. Только на клиенте. */
public final class ChlorClient {
    /** Мост осветлителя с граблями (art/blockbench/chlor_alkali/brine_treater.author.js); ось — (8, 8). */
    private static final PartialModel BRINE_RAKE = PartialModel.of(PromptCritical.id("block/brine_treater/partial/rake"));
    /**
     * Обороты моста, об/мин. Настоящий периферийный мост идёт со скоростью по кромке 2–3 м/мин (оборот за
     * 20–40 мин у осветлителя Ø20 м); здесь оборот за полторы минуты, иначе движение не заметить.
     */
    private static final float RAKE_RPM = 0.67f;

    private ChlorClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ChlorClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ChlorBlockEntities.BRINE_TREATER.get(),
                c -> new FoundryMachineRenderer<>(BRINE_RAKE, Motion.SWEEP_Y, 8, 8, RAKE_RPM, false));
    }
}
