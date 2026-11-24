package dev.denfry.promptcritical.mod.hydrogen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.hydrogen.ElectrolyserBlockEntity;
import dev.denfry.promptcritical.mod.hydrogen.FuelCellBlockEntity;
import dev.denfry.promptcritical.mod.hydrogen.H2StorageBlockEntity;
import dev.denfry.promptcritical.mod.hydrogen.HydrogenBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Клиент водородной станции: цельные модели и подвижные части из шапки art/blockbench/hydrogen/hydrogen.author.js —
 * поплавки в смотровых стёклах сепараторов, маховик и шкив компрессора (1 : 4), стрелки манометров рампы (0…250 бар на
 * 270°) и газгольдера (0…40 бар), вентиляторы радиатора и горящий вариант модуля топливных элементов. Только клиент.
 */
public final class HydrogenClient {
    private static final PartialModel H2_LEVEL = partial("h2_electrolyser_real/partial/h2_level");
    private static final PartialModel O2_LEVEL = partial("h2_electrolyser_real/partial/o2_level");
    private static final PartialModel FLYWHEEL = partial("h2_compressor_real/partial/flywheel");
    private static final PartialModel MOTOR_SHEAVE = partial("h2_compressor_real/partial/motor_sheave");
    private static final PartialModel TUBE_NEEDLE = partial("h2_tube_bank_real/partial/pressure_needle");
    private static final PartialModel HOLDER_NEEDLE = partial("h2_gasholder_real/partial/pressure_needle");
    private static final PartialModel FAN_0 = partial("h2_fuel_cell_real/partial/fan_0");
    private static final PartialModel FAN_1 = partial("h2_fuel_cell_real/partial/fan_1");

    static {
        for (String id : new String[] {"h2_electrolyser_real", "h2_compressor_real", "h2_tube_bank_real", "h2_gasholder_real",
                "h2_fuel_cell_real", "h2_fuel_cell_real_on"})
            WholeModels.register(id);
    }

    private HydrogenClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    static boolean running(BlockEntity be) {
        BlockState s = be.getBlockState();
        return s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(HydrogenClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(HydrogenBlockEntities.ELECTROLYSER.get(), c -> new ElectrolyserRenderer());
        // компрессор: маховик вокруг z через (9, 14) на месте; шкив двигателя вокруг z через (−18, 10), хранится на 8 px
        // правее; передача 1 : 4, обороты показаны условно
        event.registerBlockEntityRenderer(HydrogenBlockEntities.H2_COMPRESSOR.get(), c -> new SpinWholeRenderer<>(null)
                .spin(FLYWHEEL, Direction.Axis.Z, 9, 14, 0, 0, 0, be -> running(be) ? 60 : 0)
                .spin(MOTOR_SHEAVE, Direction.Axis.Z, -10, 10, -8, 0, 0, be -> running(be) ? 240 : 0));
        event.registerBlockEntityRenderer(HydrogenBlockEntities.H2_STORAGE.get(), c -> new StorageRenderer());
        event.registerBlockEntityRenderer(HydrogenBlockEntities.FUEL_CELL.get(), c -> new FuelCellRenderer());
    }

    /** Поплавки в стёклах сепараторов: хранятся на 48 px левее, ходят по y 0…20 px; в работе уровень выше от газа. */
    static final class ElectrolyserRenderer extends WholeModelRenderer<ElectrolyserBlockEntity> {
        @Override
        protected void parts(ElectrolyserBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            boolean run = running(be);
            part(H2_LEVEL, be, f, 3, (run ? 13 : 11) / 16.0, 0).light(light).renderInto(ms, vc);
            part(O2_LEVEL, be, f, 3, (run ? 12 : 11) / 16.0, 0).light(light).renderInto(ms, vc);
        }
    }

    /**
     * Стрелка манометра по давлению с сервера: рампа — вокруг x через (y 30, z 8), хранится на 48 px левее, шкала
     * 250 бар; газгольдер — вокруг z через (16, 40), хранится на (0, −24, +4) px, шкала 40 бар. 270° на всю шкалу.
     */
    static final class StorageRenderer extends WholeModelRenderer<H2StorageBlockEntity> {
        @Override
        protected void parts(H2StorageBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            if (be.tubeBank()) {
                float deg = 270f * Math.min(1f, be.shownBar() / 250f);
                part(TUBE_NEEDLE, be, f, 3, 0, 0).translate(0, 30 / 16f, 8 / 16f).rotateXDegrees(-deg).translate(0, -30 / 16f, -8 / 16f)
                        .light(light).renderInto(ms, vc);
            } else {
                float deg = 270f * Math.min(1f, be.shownBar() / 40f);
                part(HOLDER_NEEDLE, be, f, 0, 24 / 16f, -4 / 16f).translate(16 / 16f, 16 / 16f, 0).rotateZDegrees(-deg)
                        .translate(-16 / 16f, -16 / 16f, 0).light(light).renderInto(ms, vc);
            }
        }
    }

    /** Модуль ТЭ: в работе — горящий вариант модели и вентиляторы радиатора (вокруг y, хранятся на 24 px ниже). */
    static final class FuelCellRenderer extends WholeModelRenderer<FuelCellBlockEntity> {
        @Override
        protected String modelId(FuelCellBlockEntity be) {
            String id = be.wholeModelId();
            return id != null && running(be) ? id + "_on" : id;
        }

        @Override
        protected void parts(FuelCellBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            float time = net.createmod.catnip.animation.AnimationTickHolder.getRenderTime(be.getLevel());
            float deg = running(be) ? (time * 18) % 360 : 0;
            part(FAN_0, be, f, 0, 24 / 16f, 0).translate(-10.5f / 16, 0, 8 / 16f).rotateYDegrees(deg).translate(10.5f / 16, 0, -8 / 16f)
                    .light(light).renderInto(ms, vc);
            part(FAN_1, be, f, 0, 24 / 16f, 0).translate(-3.5f / 16, 0, 8 / 16f).rotateYDegrees(-deg).translate(3.5f / 16, 0, -8 / 16f)
                    .light(light).renderInto(ms, vc);
        }
    }
}
