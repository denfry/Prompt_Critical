package dev.denfry.promptcritical.mod.foundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.foundry.CupolaRealBlock;
import dev.denfry.promptcritical.mod.foundry.CupolaRealBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Вагранка реального масштаба (docs/research/cupola-dimensions.md, «Model as built»): цельная модель, поверх — днище
 * на двух откидных створках, вал воздуходувки, который крутит вал Create в клетке порта, и свечение глазков фурм,
 * летки и колошника, пока вагранка в дутье.
 */
final class CupolaRealRenderer extends WholeModelRenderer<CupolaRealBlockEntity> {
    static final PartialModel GLOW = partial("glow"), DOOR_GLOW = partial("door_glow"), DROP_DOORS = partial("drop_doors"),
            BLOWER_SHAFT = partial("blower_shaft");
    /** Ось вала воздуходувки в модели, px: вдоль x на высоте 24 и глубине 24. */
    private static final float SHAFT_Y = 24 / 16f, SHAFT_Z = 24 / 16f;
    /** {@code door_glow} нарисован сдвинутым на 48 px вниз (как кусок клетки y 3). */
    private static final double DOOR_GLOW_DY = 3;

    static {
        WholeModels.register("cupola_furnace_real");
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/cupola_furnace_real/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельная модель и partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(CupolaRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        // днища не сбрасывают (в симуляции нет выбивки), створки всегда подняты под подовой плитой
        part(DROP_DOORS, be, f, 0, 0, 0).light(light).renderInto(ms, vc);
        part(BLOWER_SHAFT, be, f, 0, 0, 0).translate(0, SHAFT_Y, SHAFT_Z).rotateXDegrees(shaftAngle(be, pt))
                .translate(0, -SHAFT_Y, -SHAFT_Z).light(light).renderInto(ms, vc);
        if (be.getBlockState().getValue(BlockStateProperties.LIT)) {
            part(GLOW, be, f, 0, 0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
            part(DOOR_GLOW, be, f, 0, DOOR_GLOW_DY, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        }
    }

    /** Угол вала, градусы: по скорости клетки порта (её синхронизирует Create), той же формулой, что у валов Create. */
    private static float shaftAngle(CupolaRealBlockEntity be, float pt) {
        if (be.getLevel() == null) return 0;
        var port = be.getBlockPos().offset(CupolaRealBlock.SHAFT_PORT.rotate(Footprint.rotation(be.wholeModelFacing())));
        if (!(be.getLevel().getBlockEntity(port) instanceof KineticBlockEntity k)) return 0;
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        return (time * k.getSpeed() * 3f / 10) % 360;
    }
}
