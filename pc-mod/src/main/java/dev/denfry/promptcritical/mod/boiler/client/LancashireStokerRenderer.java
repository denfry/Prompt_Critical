package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.LancashireStokerBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Механические забрасыватели ланкаширского котла: цельная модель, линия вала с эксцентриками крутится вокруг x через
 * (y 8, z −8) px модели, смотровые окна коробок светятся, пока толкатели подают уголь.
 */
final class LancashireStokerRenderer extends WholeModelRenderer<LancashireStokerBlockEntity> {
    static final PartialModel SHAFT = partial("shaft"), GLOW = partial("glow");
    /** Видимые обороты линии вала при работе, град/тик (≈ 20 об/мин у эксцентриков). */
    private static final float DEG_PER_TICK = 6f;

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/lancashire_stoker/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(LancashireStokerBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        BlockState s = be.getBlockState();
        boolean running = s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING);
        float deg = running && be.getLevel() != null ? (be.getLevel().getGameTime() + pt) * DEG_PER_TICK % 360 : 0;
        part(SHAFT, be, f, 0, 0, 0).translate(0, 0.5, -0.5).rotateXDegrees(deg).translate(0, -0.5, 0.5).light(light).renderInto(ms, vc);
        if (running) part(GLOW, be, f, 0, 0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
    }
}
