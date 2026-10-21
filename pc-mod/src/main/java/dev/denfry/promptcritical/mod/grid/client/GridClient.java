package dev.denfry.promptcritical.mod.grid.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.client.WireRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.grid.GridBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Клиент электросети: ротор генератора и вал двигателя, стрелка синхроноскопа, нож разъединителя, провода ВЛ; провода
 * автоматики к клеммам ячеек, щитов, двигателя и потребителя.
 */
public final class GridClient {
    static final PartialModel ROTOR = partial("shaft_generator/partial/rotor");
    static final PartialModel MOTOR_SHAFT = partial("electric_motor/partial/shaft");
    static final PartialModel BLADE = partial("disconnector/partial/blade");

    private GridClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(GridClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // вал в частях свой — полувал Create не рисуем
        event.registerBlockEntityRenderer(GridBlockEntities.SHAFT_GENERATOR.get(),
                c -> new FoundryMachineRenderer<>(ROTOR, Motion.SPIN_Z, 8, 8, 1, false));
        event.registerBlockEntityRenderer(GridBlockEntities.ELECTRIC_MOTOR.get(),
                c -> new WireRenderer<>(new FoundryMachineRenderer<>(MOTOR_SHAFT, Motion.SPIN_Z, 8, 8, 1, false)));
        // клеммы автоматики механизмов (этап 9): катушки и блок-контакты
        event.registerBlockEntityRenderer(GridBlockEntities.SWITCHGEAR_CELL.get(), c -> new WireRenderer<>(null));
        event.registerBlockEntityRenderer(GridBlockEntities.LV_SWITCHBOARD.get(), c -> new WireRenderer<>(null));
        event.registerBlockEntityRenderer(GridBlockEntities.GRID_CONSUMER.get(), c -> new WireRenderer<>(null));
        event.registerBlockEntityRenderer(GridBlockEntities.SYNCHROSCOPE.get(), c -> new SynchroscopeRenderer());
        event.registerBlockEntityRenderer(GridBlockEntities.DISCONNECTOR.get(), c -> new DisconnectorRenderer());
        event.registerBlockEntityRenderer(GridBlockEntities.WIRE_CONNECTOR_LV.get(), c -> new SpanRenderer());
        event.registerBlockEntityRenderer(GridBlockEntities.WIRE_CONNECTOR_MV.get(), c -> new SpanRenderer());
    }
}
