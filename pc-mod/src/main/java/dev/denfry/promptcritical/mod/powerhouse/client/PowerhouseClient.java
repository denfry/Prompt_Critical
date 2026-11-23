package dev.denfry.promptcritical.mod.powerhouse.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.powerhouse.PowerhouseBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент котла I1 и ВЭУ: крыльчатки дутьевого вентилятора и дымососа на валу Create, гондола и колесо ветряка. Только на клиенте. */
public final class PowerhouseClient {
    private static final PartialModel FD_IMPELLER = partial("forced_draft_fan/partial/impeller");
    private static final PartialModel ID_IMPELLER = partial("induced_draft_fan/partial/impeller");
    private static final PartialModel WIND_NACELLE = partial("wind_turbine/partial/nacelle");
    private static final PartialModel WIND_BLADE = partial("wind_turbine/partial/blade");
    private static final PartialModel DG_FLYWHEEL = partial("diesel_generator/partial/flywheel");
    /** Машины реального масштаба (art/blockbench/boilers, ore_processing): цельные модели и части — до запекания. */
    private static final PartialModel FD_IMPELLER_REAL = partial("forced_draft_fan_real/partial/impeller");
    private static final PartialModel ID_IMPELLER_REAL = partial("induced_draft_fan_real/partial/impeller");
    private static final PartialModel FLOTATION_IMPELLER_REAL = partial("flotation_cell_real/partial/impeller");
    private static final PartialModel FLOTATION_PADDLES_REAL = partial("flotation_cell_real/partial/paddles");
    private static final PartialModel FLOTATION_FROTH_REAL = partial("flotation_cell_real/partial/froth");

    static {
        for (String id : new String[] {"forced_draft_fan_real", "induced_draft_fan_real", "electrode_boiler_real", "flotation_cell_real"})
            dev.denfry.promptcritical.mod.client.whole.WholeModels.register(id);
    }

    private PowerhouseClient() {
    }

    /** Скорость вала Create в контроллере, об/мин. */
    private static double controllerSpeed(net.minecraft.world.level.block.entity.BlockEntity be) {
        return be instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : 0;
    }

    /** {@code rpm}, пока машина работает ({@code running}), иначе 0. */
    private static double whileRunning(net.minecraft.world.level.block.entity.BlockEntity be, double rpm) {
        var s = be.getBlockState();
        return s.hasProperty(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING)
                && s.getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING) ? rpm : 0;
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(PowerhouseClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // ось крыльчатки — центр блока, вдоль Z (фасад на север)
        // реальные: колесо вращается вокруг z через (8, 8) px модели на месте, скорость — вала в контроллере
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.FORCED_DRAFT_FAN.get(), c -> new dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer<>(
                new FoundryMachineRenderer<>(FD_IMPELLER, Motion.SPIN_Z, 8, 8, 1))
                .spin(FD_IMPELLER_REAL, net.minecraft.core.Direction.Axis.Z, 8, 8, 0, 0, 0, PowerhouseClient::controllerSpeed));
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.INDUCED_DRAFT_FAN.get(), c -> new dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer<>(
                new FoundryMachineRenderer<>(ID_IMPELLER, Motion.SPIN_Z, 8, 8, 1))
                .spin(ID_IMPELLER_REAL, net.minecraft.core.Direction.Axis.Z, 8, 8, 0, 0, 0, PowerhouseClient::controllerSpeed));
        // электродный котёл: у старого рендера нет, у реального частей нет
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.ELECTRODE_BOILER.get(), c -> new dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer<>(null));
        // флотокамера реального масштаба: импеллер вокруг y через (16, 18), хранится на 16 px ниже; пеногон вокруг x
        // через (27, 7); пена — пока камера работает. Вала Create у флотокамеры нет: обороты условные
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.nonferrous.NonferrousBlockEntities.FLOTATION_CELL.get(),
                c -> new dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer<>(null)
                        .spin(FLOTATION_IMPELLER_REAL, net.minecraft.core.Direction.Axis.Y, 16, 18, 0, 16, 0, be -> whileRunning(be, 60))
                        .spin(FLOTATION_PADDLES_REAL, net.minecraft.core.Direction.Axis.X, 27, 7, 0, 0, 0, be -> whileRunning(be, 8))
                        .whileRunning(FLOTATION_FROTH_REAL));
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.WIND_TURBINE.get(), c -> new WindTurbineRenderer(WIND_NACELLE, WIND_BLADE));
        // маховик ДГ на торце дизеля: ось — x 8, y 0 модели, вдоль Z; 750 об/мин показаны условно
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.DIESEL_GENERATOR.get(),
                c -> FoundryMachineRenderer.whileRunning(DG_FLYWHEEL, Motion.SPIN_Z, 8, 0, 120));
    }
}
