package dev.denfry.promptcritical.mod.steelworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.steelworks.BofVesselRealBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;

/**
 * Кислородный конвертер реального масштаба (docs/research/bof_vessel-dimensions.md): неподвижная цельная модель опор,
 * редуктора и камина, поверх — корпус с цапфовым кольцом (вторая цельная модель {@code bof_vessel_real_tilt}),
 * повёрнутый вокруг оси цапф, и свечение горловины, пока идёт продувка. Положительный наклон — горловиной к фасаду
 * (завалка), как у старого конвертера.
 */
final class BofRealRenderer extends WholeModelRenderer<BofVesselRealBlockEntity> {
    static final PartialModel MOUTH_GLOW = PartialModel.of(PromptCritical.id("block/bof_vessel_real/partial/mouth_glow"));
    private static final double AXIS_Y = BofVesselRealBlockEntity.TRUNNION_Y_PX / 16, AXIS_Z = BofVesselRealBlockEntity.TRUNNION_Z_PX / 16;
    /** {@code mouth_glow} нарисован сдвинутым на 64 px вниз (как кусок клетки y 4). */
    private static final double MOUTH_GLOW_DY = 4;

    static {
        WholeModels.register("bof_vessel_real");
        WholeModels.register("bof_vessel_real_tilt");
    }

    /** Загрузить класс до запекания моделей: цельные модели и partial регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(BofVesselRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        float a = (float) -Math.toRadians(be.renderTiltDeg());
        wholeAboutX("bof_vessel_real_tilt", be, f, 0.5, AXIS_Y, AXIS_Z, a, ms, vc, light);
        if (be.getBlockState().getValue(PcProperties.RUNNING))
            part(MOUTH_GLOW, be, f, 0, 0, 0).translate(0, AXIS_Y, AXIS_Z).rotateX(a).translate(0, -AXIS_Y, -AXIS_Z)
                    .translate(0, MOUTH_GLOW_DY, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
    }
}
