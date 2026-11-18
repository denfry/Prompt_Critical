package dev.denfry.promptcritical.mod.fx.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.fx.client.RunningPartRenderer.Motion;
import dev.denfry.promptcritical.mod.methanol.MethanolBlockEntities;
import dev.denfry.promptcritical.mod.nitrogen.NitrogenBlockEntities;
import dev.denfry.promptcritical.mod.refinery.RefineryBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Рендер подвижных частей технологических установок без вала Create (арт — art/blockbench/&lt;категория&gt;/*.author.js):
 * штанга гидрорезки коксовой камеры; муфты циркуляционных компрессоров на валу Create. Только на клиенте.
 */
public final class ProcessArtClient {
    static final PartialModel COKER_DRILL = partial("delayed_coker/partial/drill");
    static final PartialModel METHANOL_COUPLING = partial("methanol_converter/partial/coupling");
    static final PartialModel AMMONIA_COUPLING = partial("ammonia_converter/partial/coupling");

    private ProcessArtClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ProcessArtClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // штанга резака опускается на 4 px за 16 с и поднимается за 4 с
        event.registerBlockEntityRenderer(RefineryBlockEntities.DELAYED_COKER.get(),
                c -> new RunningPartRenderer<>(COKER_DRILL, Motion.STROKE_Y, 4, 0, 400));
        // муфта циркулятора на оси вала у задней грани контроллера (x 8, y 8)
        event.registerBlockEntityRenderer(MethanolBlockEntities.METHANOL_CONVERTER.get(),
                c -> new FoundryMachineRenderer<>(METHANOL_COUPLING, FoundryMachineRenderer.Motion.SPIN_Z, 8, 8, 1));
        event.registerBlockEntityRenderer(NitrogenBlockEntities.AMMONIA_CONVERTER.get(),
                c -> new FoundryMachineRenderer<>(AMMONIA_COUPLING, FoundryMachineRenderer.Motion.SPIN_Z, 8, 8, 1));
    }
}
