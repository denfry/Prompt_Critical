package dev.denfry.promptcritical.mod.grid.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.client.WireRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.client.whole.WholeOrOldRenderer;
import dev.denfry.promptcritical.mod.grid.GridBlockEntities;
import dev.denfry.promptcritical.mod.grid.RealShaftMachineBlock;
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
    /** Ротор генератора и вал двигателя реального масштаба. */
    static final PartialModel ROTOR_REAL = partial("shaft_generator_real/partial/rotor");
    static final PartialModel MOTOR_SHAFT_REAL = partial("electric_motor_mv_real/partial/shaft");

    static {
        // цельные модели реального масштаба — до запекания моделей
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("shaft_generator_real");
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("electric_motor_mv_real");
    }

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
        // у блоков реального масштаба — цельная модель и ротор или вал на оси порта
        event.registerBlockEntityRenderer(GridBlockEntities.SHAFT_GENERATOR.get(),
                c -> new WholeOrOldRenderer<>(new RealShaftRenderer<>(ROTOR_REAL, RealShaftMachineBlock.GENERATOR_PORT),
                        new FoundryMachineRenderer<>(ROTOR, Motion.SPIN_Z, 8, 8, 1, false)));
        event.registerBlockEntityRenderer(GridBlockEntities.ELECTRIC_MOTOR.get(),
                c -> new WireRenderer<>(new WholeOrOldRenderer<>(new RealShaftRenderer<>(MOTOR_SHAFT_REAL, RealShaftMachineBlock.MOTOR_PORT),
                        new FoundryMachineRenderer<>(MOTOR_SHAFT, Motion.SPIN_Z, 8, 8, 1, false))));
        // клеммы автоматики механизмов (этап 9): катушки и блок-контакты
        // плакат наряда-допуска на дверце ячейки и на приводе разъединителя
        // у ячейки КРУ дверь ниже контроллера: он в средней клетке, над дверью шинный отсек
        event.registerBlockEntityRenderer(GridBlockEntities.SWITCHGEAR_CELL.get(), c -> new WireRenderer<>(new PosterRenderer<>(null, 5, 4)));
        event.registerBlockEntityRenderer(GridBlockEntities.LV_SWITCHBOARD.get(), c -> new WireRenderer<>(new PosterRenderer<>(null, 5, 20)));
        event.registerBlockEntityRenderer(GridBlockEntities.GRID_CONSUMER.get(), c -> new WireRenderer<>(null));
        event.registerBlockEntityRenderer(GridBlockEntities.SYNCHROSCOPE.get(), c -> new SynchroscopeRenderer());
        event.registerBlockEntityRenderer(GridBlockEntities.DISCONNECTOR.get(), c -> new PosterRenderer<>(new DisconnectorRenderer(), 5, 3));
        event.registerBlockEntityRenderer(GridBlockEntities.WIRE_CONNECTOR_LV.get(), c -> new SpanRenderer());
        event.registerBlockEntityRenderer(GridBlockEntities.WIRE_CONNECTOR_MV.get(), c -> new SpanRenderer());
    }
}
